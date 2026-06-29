package com.mmf.group6.sea_battle.client;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

public class BattleshipClientGUI extends JFrame {
    private static final int BOARD_SIZE = 10;
    private static final int[] SHIP_SIZES = {4, 3, 3, 2, 2, 2, 1, 1, 1, 1};
    private static final int CELL_SIZE = 48;

    private static final Color BG_DARK = new Color(15, 23, 42);
    private static final Color BG_PANEL = new Color(30, 41, 59);
    private static final Color BORDER_COLOR = new Color(71, 85, 105);
    private static final Color ACCENT_GOLD = new Color(244, 162, 97);
    private static final Color ACCENT_GOLD_HOVER = new Color(231, 111, 80);
    private static final Color ACCENT_TEAL = new Color(42, 157, 143);
    private static final Color WATER_DEEP = new Color(14, 60, 90);
    private static final Color WATER_MID = new Color(27, 73, 101);
    private static final Color SHIP_BASE = new Color(92, 103, 125);
    private static final Color SHIP_HIGHLIGHT = new Color(139, 155, 180);
    private static final Color HIT_RED = new Color(230, 57, 70);
    private static final Color HIT_ORANGE = new Color(255, 140, 0);
    private static final Color MISS_BLUE = new Color(168, 218, 220);
    private static final Color MISS_LIGHT = new Color(200, 235, 240);
    private static final Color TEXT_PRIMARY = new Color(248, 250, 252);
    private static final Color TEXT_SECONDARY = new Color(148, 163, 184);
    private static final Color SUCCESS = new Color(34, 197, 94);
    private static final Color DANGER = new Color(239, 68, 68);

    private CardLayout cardLayout;
    private JPanel mainPanel;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private Thread receiverThread;
    private volatile boolean connected = false;

    private final ConnectionPanel connectionPanel;
    private final SetupPanel setupPanel;
    private final LobbyPanel lobbyPanel;
    private final BattlePanel battlePanel;

    public BattleshipClientGUI() {
        super("Морской бой");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        getContentPane().setBackground(BG_DARK);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        mainPanel.setOpaque(false);
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        connectionPanel = new ConnectionPanel();
        setupPanel = new SetupPanel();
        lobbyPanel = new LobbyPanel();
        battlePanel = new BattlePanel();

        mainPanel.add(connectionPanel, "CONNECT");
        mainPanel.add(setupPanel, "SETUP");
        mainPanel.add(lobbyPanel, "LOBBY");
        mainPanel.add(battlePanel, "BATTLE");

        setLayout(new OverlayLayout(getContentPane()));
        getContentPane().add(mainPanel);

        pack();
        setLocationRelativeTo(null);
        showPanel("CONNECT");
    }

    private void showPanel(String name) {
        cardLayout.show(mainPanel, name);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    private void connect(String host, int port) {
        new Thread(() -> {
            try {
                socket = new Socket(host, port);
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
                connected = true;

                SwingUtilities.invokeLater(() -> {
                    connectionPanel.setStatus("Ожидание запроса поля от сервера...", true);
                });

                receiverThread = new Thread(this::receiveLoop, "NetworkReceiver");
                receiverThread.setDaemon(true);
                receiverThread.start();

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    connectionPanel.setStatus("Не удалось подключиться: " + e.getMessage(), false);
                    connectionPanel.setConnectEnabled(true);
                });
            }
        }, "Connector").start();
    }

    private void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        socket = null;
        in = null;
        out = null;
    }

    private void receiveLoop() {
        try {
            while (connected) {
                String msg = in.readUTF();
                SwingUtilities.invokeLater(() -> handleMessage(msg));
            }
        } catch (IOException e) {
            if (connected) {
                SwingUtilities.invokeLater(() -> {
                    disconnect();
                    showPanel("CONNECT");
                    connectionPanel.setConnectEnabled(true);
                    connectionPanel.setStatus("Не подключено", false);
                });
            }
        }
    }

    private void handleMessage(String msg) {
        if (msg.equals("CMD_REQUIRE_FIELD")) {
            setupPanel.reset();
            showPanel("SETUP");
        } else if (msg.startsWith("CMD_OK")) {
            showPanel("LOBBY");
            lobbyPanel.refreshPlayers();
        } else if (msg.startsWith("CMD_ERROR:")) {
            String err = msg.substring(10);
        } else if (msg.startsWith("PLAYERS:")) {
            String list = msg.substring(8);
            lobbyPanel.updatePlayers(list);
        } else if (msg.startsWith("CMD_GAME_START:")) {
            String info = msg.substring(15);
            battlePanel.initGame(info);
            showPanel("BATTLE");
        } else if (msg.equals("CMD_YOUR_TURN")) {
            battlePanel.setMyTurn(true);
        } else if (msg.equals("CMD_OPPONENT_TURN")) {
            battlePanel.setMyTurn(false);
        } else if (msg.startsWith("CMD_MOVE_RESULT:")) {
            String[] p = msg.split(":");
            if (p.length >= 4) {
                String result = p[1];
                int x = Integer.parseInt(p[2]);
                int y = Integer.parseInt(p[3]);
                battlePanel.applyMyMove(x, y, result);
            }
        } else if (msg.startsWith("CMD_OPPONENT_MOVE:")) {
            String[] p = msg.split(":");
            if (p.length >= 4) {
                String result = p[1];
                int x = Integer.parseInt(p[2]);
                int y = Integer.parseInt(p[3]);
                battlePanel.applyOpponentMove(x, y, result);
            }
        } else if (msg.equals("CMD_AGAIN")) {
            battlePanel.showAgain();
        } else if (msg.startsWith("CMD_GAME_OVER:")) {
            String result = msg.substring(14);
            boolean win = result.equals("WIN");
            battlePanel.endGame();
            showPanel("LOBBY");
            lobbyPanel.refreshPlayers();
        } else {
            battlePanel.log("Сервер: " + msg);
        }
    }

    private void send(String cmd) {
        if (out == null) return;
        try {
            out.writeUTF(cmd);
            out.flush();
        } catch (IOException e) {
        }
    }

    class ConnectionPanel extends JPanel {
        private final JTextField txtHost;
        private final JTextField txtPort;
        private final JLabel lblStatus;
        private final GradientButton btnConnect;
        private final JLabel statusIndicator;

        ConnectionPanel() {
            setOpaque(false);
            setLayout(new GridBagLayout());

            JLabel title = new JLabel("⚓ МОРСКОЙ БОЙ", SwingConstants.CENTER);
            title.setFont(new Font("Segoe UI", Font.BOLD, 32));
            title.setForeground(ACCENT_GOLD);

            JLabel subtitle = new JLabel("Подключитесь к серверу для начала игры", SwingConstants.CENTER);
            subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            subtitle.setForeground(TEXT_SECONDARY);

            txtHost = new RoundedTextField("127.0.0.1", 12);
            txtPort = new RoundedTextField("12345", 6);

            lblStatus = new JLabel("Не подключено", SwingConstants.CENTER);
            lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            lblStatus.setForeground(DANGER);

            statusIndicator = new JLabel("●");
            statusIndicator.setFont(new Font("Segoe UI", Font.BOLD, 14));
            statusIndicator.setForeground(DANGER);

            btnConnect = new GradientButton("Подключиться", ACCENT_GOLD, ACCENT_GOLD_HOVER);
            btnConnect.setFont(new Font("Segoe UI", Font.BOLD, 15));
            btnConnect.setPreferredSize(new Dimension(200, 44));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(8, 8, 8, 8);
            gbc.gridwidth = 2;
            gbc.gridx = 0; gbc.gridy = 0;
            add(title, gbc);
            gbc.gridy = 1;
            add(subtitle, gbc);

            gbc.gridwidth = 1;
            gbc.gridy = 2;
            JLabel lblHost = new JLabel("Сервер:");
            lblHost.setForeground(TEXT_PRIMARY);
            lblHost.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            add(lblHost, gbc);
            gbc.gridx = 1;
            add(txtHost, gbc);

            gbc.gridx = 0; gbc.gridy = 3;
            JLabel lblPort = new JLabel("Порт:");
            lblPort.setForeground(TEXT_PRIMARY);
            lblPort.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            add(lblPort, gbc);
            gbc.gridx = 1;
            add(txtPort, gbc);

            gbc.gridwidth = 2;
            gbc.gridx = 0; gbc.gridy = 4;
            gbc.insets = new Insets(20, 8, 8, 8);
            add(btnConnect, gbc);

            gbc.gridy = 5;
            gbc.insets = new Insets(12, 8, 8, 8);
            JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
            statusPanel.setOpaque(false);
            statusPanel.add(statusIndicator);
            statusPanel.add(lblStatus);
            add(statusPanel, gbc);

            btnConnect.addActionListener(e -> {
                btnConnect.setEnabled(false);
                lblStatus.setText("Подключение...");
                statusIndicator.setForeground(ACCENT_GOLD);
                connect(txtHost.getText().trim(), Integer.parseInt(txtPort.getText().trim()));
            });
        }

        void setStatus(String s, boolean connecting) {
            lblStatus.setText(s);
            statusIndicator.setForeground(connecting ? ACCENT_GOLD : DANGER);
            lblStatus.setForeground(connecting ? ACCENT_GOLD : DANGER);
        }
        void setConnectEnabled(boolean b) { btnConnect.setEnabled(b); }
    }

    class SetupPanel extends JPanel {
        private final BoardPanel board;
        private final JPanel shipPalette;
        private final JLabel lblInfo;
        private final GradientButton btnRotate;
        private final GradientButton btnAuto;
        private final GradientButton btnClear;
        private final GradientButton btnReady;

        private boolean[][] field = new boolean[BOARD_SIZE][BOARD_SIZE];
        private final List<Integer> remainingShips = new ArrayList<>();
        private int selectedShipSize = -1;
        private boolean horizontal = true;

        SetupPanel() {
            setOpaque(false);
            setLayout(new BorderLayout(15, 15));
            setBorder(new EmptyBorder(15, 15, 15, 15));

            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            JLabel title = new JLabel("Расстановка кораблей", SwingConstants.CENTER);
            title.setFont(new Font("Segoe UI", Font.BOLD, 22));
            title.setForeground(ACCENT_GOLD);
            header.add(title, BorderLayout.NORTH);

            lblInfo = new JLabel("Выберите корабль и разместите на поле", SwingConstants.CENTER);
            lblInfo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            lblInfo.setForeground(TEXT_SECONDARY);
            header.add(lblInfo, BorderLayout.SOUTH);
            add(header, BorderLayout.NORTH);

            JPanel center = new JPanel(new BorderLayout(20, 0));
            center.setOpaque(false);

            board = new BoardPanel(BOARD_SIZE, CELL_SIZE, BoardPanel.Mode.SETUP);
            JPanel boardWrap = createPanelWrapper("Ваше поле", board);
            center.add(boardWrap, BorderLayout.CENTER);

            shipPalette = new JPanel();
            shipPalette.setLayout(new GridLayout(0, 1, 8, 8));
            shipPalette.setOpaque(false);
            shipPalette.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel paletteWrap = createPanelWrapper("Доступные корабли", shipPalette);
            paletteWrap.setPreferredSize(new Dimension(200, 0));
            center.add(paletteWrap, BorderLayout.EAST);

            add(center, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
            bottom.setOpaque(false);
            bottom.setBorder(new EmptyBorder(10, 0, 0, 0));

            btnRotate = new GradientButton("↻ Повернуть (R)", new Color(59, 130, 246), new Color(37, 99, 235));
            btnAuto = new GradientButton("🎲 Авто", new Color(139, 92, 246), new Color(124, 58, 237));
            btnClear = new GradientButton("🗑 Очистить", new Color(100, 116, 139), new Color(71, 85, 105));
            btnReady = new GradientButton("✓ Готов", ACCENT_TEAL, new Color(20, 130, 120));
            btnReady.setEnabled(false);

            for (GradientButton btn : new GradientButton[]{btnRotate, btnAuto, btnClear, btnReady}) {
                btn.setPreferredSize(new Dimension(130, 40));
                btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
            }

            bottom.add(btnRotate);
            bottom.add(btnAuto);
            bottom.add(btnClear);
            bottom.add(btnReady);
            add(bottom, BorderLayout.SOUTH);

            btnRotate.addActionListener(e -> {
                horizontal = !horizontal;
                board.repaint();
                updateInfo();
            });
            btnAuto.addActionListener(e -> autoPlace());
            btnClear.addActionListener(e -> reset());
            btnReady.addActionListener(e -> submitField());

            InputMap im = board.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            ActionMap am = board.getActionMap();
            im.put(KeyStroke.getKeyStroke("R"), "rotate");
            am.put("rotate", new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    horizontal = !horizontal;
                    board.repaint();
                    updateInfo();
                }
            });

            board.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (selectedShipSize <= 0) return;
                    int row = e.getY() / CELL_SIZE;
                    int col = e.getX() / CELL_SIZE;
                    if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) return;
                    if (canPlace(field, row, col, selectedShipSize, horizontal)) {
                        placeShip(field, row, col, selectedShipSize, horizontal);
                        remainingShips.remove((Integer) selectedShipSize);
                        selectedShipSize = -1;
                        refreshBoard();
                        rebuildPalette();
                        checkReady();
                        board.clearHighlight();
                    } else {
                        lblInfo.setText("Нельзя разместить корабль здесь");
                        lblInfo.setForeground(DANGER);
                    }
                }
            });

            board.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int row = e.getY() / CELL_SIZE;
                    int col = e.getX() / CELL_SIZE;
                    if (selectedShipSize > 0) {
                        boolean ok = canPlace(field, row, col, selectedShipSize, horizontal);
                        board.setHighlight(row, col, selectedShipSize, horizontal, ok);
                    } else {
                        board.clearHighlight();
                    }
                }
            });
        }

        private JPanel createPanelWrapper(String title, JComponent content) {
            JLabel lbl = new JLabel(title, SwingConstants.CENTER);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
            lbl.setForeground(TEXT_SECONDARY);
            lbl.setBorder(new EmptyBorder(0, 0, 8, 0));

            JPanel wrapper = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(BG_PANEL);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    g2.setColor(BORDER_COLOR);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            wrapper.setOpaque(false);
            wrapper.setBorder(new EmptyBorder(10, 10, 10, 10));
            wrapper.add(lbl, BorderLayout.NORTH);
            wrapper.add(content, BorderLayout.CENTER);
            return wrapper;
        }

        boolean[][] getField() { return field; }

        void reset() {
            field = new boolean[BOARD_SIZE][BOARD_SIZE];
            remainingShips.clear();
            for (int s : SHIP_SIZES) remainingShips.add(s);
            selectedShipSize = -1;
            horizontal = true;
            refreshBoard();
            rebuildPalette();
            btnReady.setEnabled(false);
            lblInfo.setText("Выберите корабль и разместите на поле");
            lblInfo.setForeground(TEXT_SECONDARY);
        }

        private void updateInfo() {
            if (selectedShipSize > 0) {
                lblInfo.setText("Выбран " + selectedShipSize + "-палубный. Поворот: " + (horizontal ? "→" : "↓") + " (R)");
                lblInfo.setForeground(ACCENT_GOLD);
            } else {
                lblInfo.setText("Осталось разместить: " + remainingShips.size() + " кораблей");
                lblInfo.setForeground(TEXT_SECONDARY);
            }
        }

        private void refreshBoard() {
            int[][] cells = new int[BOARD_SIZE][BOARD_SIZE];
            for (int i = 0; i < BOARD_SIZE; i++)
                for (int j = 0; j < BOARD_SIZE; j++)
                    cells[i][j] = field[i][j] ? 1 : 0;
            board.setCells(cells);
        }

        private void rebuildPalette() {
            shipPalette.removeAll();
            Map<Integer, Integer> counts = new LinkedHashMap<>();
            for (int s : remainingShips) counts.merge(s, 1, Integer::sum);

            for (int size : new int[]{4, 3, 2, 1}) {
                int cnt = counts.getOrDefault(size, 0);
                if (cnt > 0) {
                    JPanel shipItem = createShipItem(size, cnt);
                    shipPalette.add(shipItem);
                }
            }
            updateInfo();
            shipPalette.revalidate();
            shipPalette.repaint();
        }

        private JPanel createShipItem(int size, int count) {
            JPanel item = new JPanel(new BorderLayout(8, 0));
            item.setOpaque(false);
            item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            item.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

            JPanel shipVisual = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int cellW = 18;
                    int cellH = 14;
                    int gap = 2;
                    for (int i = 0; i < size; i++) {
                        int x = i * (cellW + gap);
                        GradientPaint gp = new GradientPaint(x, 0, SHIP_HIGHLIGHT, x, cellH, SHIP_BASE);
                        g2.setPaint(gp);
                        g2.fillRoundRect(x, 0, cellW, cellH, 4, 4);
                        g2.setColor(new Color(60, 70, 90));
                        g2.drawRoundRect(x, 0, cellW, cellH, 4, 4);
                    }
                    g2.dispose();
                }
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(size * 20 + 4, 16);
                }
            };
            shipVisual.setOpaque(false);

            JLabel countLabel = new JLabel("×" + count);
            countLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            countLabel.setForeground(ACCENT_GOLD);

            item.add(shipVisual, BorderLayout.WEST);
            item.add(countLabel, BorderLayout.EAST);

            item.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    item.setBackground(new Color(51, 65, 85, 100));
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    item.setBackground(null);
                }
                @Override
                public void mouseClicked(MouseEvent e) {
                    selectedShipSize = size;
                    updateInfo();
                }
            });

            return item;
        }

        private void checkReady() {
            btnReady.setEnabled(remainingShips.isEmpty());
            if (remainingShips.isEmpty()) {
                lblInfo.setText("Все корабли на месте! Нажмите «Готов»");
                lblInfo.setForeground(SUCCESS);
            }
        }

        private void autoPlace() {
            String gen = generateValidField();
            for (int i = 0; i < BOARD_SIZE; i++)
                for (int j = 0; j < BOARD_SIZE; j++)
                    field[i][j] = (gen.charAt(i * BOARD_SIZE + j) == '1');
            remainingShips.clear();
            selectedShipSize = -1;
            refreshBoard();
            rebuildPalette();
            btnReady.setEnabled(true);
            lblInfo.setText("Поле сгенерировано автоматически");
            lblInfo.setForeground(ACCENT_TEAL);
        }

        private void submitField() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < BOARD_SIZE; i++)
                for (int j = 0; j < BOARD_SIZE; j++)
                    sb.append(field[i][j] ? '1' : '0');
            send("SET_FIELD:" + sb.toString());
        }

        private boolean canPlace(boolean[][] f, int x, int y, int size, boolean horiz) {
            if (horiz) {
                if (y + size > BOARD_SIZE) return false;
                for (int i = -1; i <= size; i++) {
                    for (int j = -1; j <= 1; j++) {
                        int nx = x + j, ny = y + i;
                        if (nx >= 0 && nx < BOARD_SIZE && ny >= 0 && ny < BOARD_SIZE && f[nx][ny]) return false;
                    }
                }
            } else {
                if (x + size > BOARD_SIZE) return false;
                for (int i = -1; i <= size; i++) {
                    for (int j = -1; j <= 1; j++) {
                        int nx = x + i, ny = y + j;
                        if (nx >= 0 && nx < BOARD_SIZE && ny >= 0 && ny < BOARD_SIZE && f[nx][ny]) return false;
                    }
                }
            }
            return true;
        }

        private void placeShip(boolean[][] f, int x, int y, int size, boolean horiz) {
            if (horiz) for (int i = 0; i < size; i++) f[x][y + i] = true;
            else for (int i = 0; i < size; i++) f[x + i][y] = true;
        }

        private String generateValidField() {
            boolean[][] f = new boolean[BOARD_SIZE][BOARD_SIZE];
            Random rand = new Random();
            List<Integer> sizes = new ArrayList<>();
            for (int s : SHIP_SIZES) sizes.add(s);
            Collections.shuffle(sizes, rand);
            for (int size : sizes) {
                boolean placed = false;
                int attempts = 0;
                while (!placed && attempts < 5000) {
                    attempts++;
                    int x = rand.nextInt(BOARD_SIZE);
                    int y = rand.nextInt(BOARD_SIZE);
                    boolean horiz = rand.nextBoolean();
                    if (canPlace(f, x, y, size, horiz)) {
                        placeShip(f, x, y, size, horiz);
                        placed = true;
                    }
                }
                if (!placed) return generateValidField();
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < BOARD_SIZE; i++)
                for (int j = 0; j < BOARD_SIZE; j++)
                    sb.append(f[i][j] ? '1' : '0');
            return sb.toString();
        }
    }

    class LobbyPanel extends JPanel {
        private final DefaultListModel<PlayerItem> listModel = new DefaultListModel<>();
        private final JList<PlayerItem> playerList;
        private final GradientButton btnRefresh;
        private final GradientButton btnChallenge;
        private final JLabel lblStatus;

        LobbyPanel() {
            setOpaque(false);
            setLayout(new BorderLayout(15, 15));
            setBorder(new EmptyBorder(15, 15, 15, 15));

            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            JLabel title = new JLabel("Лобби", SwingConstants.CENTER);
            title.setFont(new Font("Segoe UI", Font.BOLD, 24));
            title.setForeground(ACCENT_GOLD);
            header.add(title, BorderLayout.NORTH);

            JLabel subtitle = new JLabel("Выберите противника для боя", SwingConstants.CENTER);
            subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            subtitle.setForeground(TEXT_SECONDARY);
            header.add(subtitle, BorderLayout.SOUTH);
            add(header, BorderLayout.NORTH);

            playerList = new JList<>(listModel);
            playerList.setOpaque(false);
            playerList.setCellRenderer(new PlayerCellRenderer());
            playerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            playerList.setBorder(null);

            JScrollPane sp = new JScrollPane(playerList);
            sp.setOpaque(false);
            sp.getViewport().setOpaque(false);
            sp.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(12, BORDER_COLOR, 1.5f),
                new EmptyBorder(8, 8, 8, 8)
            ));
            sp.setPreferredSize(new Dimension(350, 280));

            JPanel listWrapper = new JPanel(new BorderLayout());
            listWrapper.setOpaque(false);
            listWrapper.add(sp, BorderLayout.CENTER);
            add(listWrapper, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new BorderLayout());
            bottom.setOpaque(false);

            lblStatus = new JLabel(" ", SwingConstants.CENTER);
            lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            lblStatus.setForeground(TEXT_SECONDARY);
            bottom.add(lblStatus, BorderLayout.NORTH);

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
            btnPanel.setOpaque(false);
            btnPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

            btnRefresh = new GradientButton("↻ Обновить", new Color(59, 130, 246), new Color(37, 99, 235));
            btnChallenge = new GradientButton("⚔ Вызвать на бой", ACCENT_GOLD, ACCENT_GOLD_HOVER);

            for (GradientButton btn : new GradientButton[]{btnRefresh, btnChallenge}) {
                btn.setPreferredSize(new Dimension(160, 42));
                btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
            }

            btnPanel.add(btnRefresh);
            btnPanel.add(btnChallenge);
            bottom.add(btnPanel, BorderLayout.SOUTH);
            add(bottom, BorderLayout.SOUTH);

            btnRefresh.addActionListener(e -> refreshPlayers());
            btnChallenge.addActionListener(e -> {
                PlayerItem sel = playerList.getSelectedValue();
                if (sel == null) {
                    lblStatus.setText("Выберите противника из списка");
                    lblStatus.setForeground(DANGER);
                    return;
                }
                send("SELECT_PLAYER:" + sel.id);
                lblStatus.setText("Вызов отправлен...");
                lblStatus.setForeground(ACCENT_GOLD);
            });
        }

        void refreshPlayers() {
            listModel.clear();
            send("GET_PLAYERS");
            lblStatus.setText("Список обновлён");
            lblStatus.setForeground(TEXT_SECONDARY);
        }

        void updatePlayers(String data) {
            listModel.clear();
            if (data.isEmpty()) {
                lblStatus.setText("Нет доступных игроков");
                lblStatus.setForeground(TEXT_SECONDARY);
                return;
            }
            for (String idStr : data.split(",")) {
                try {
                    int id = Integer.parseInt(idStr.trim());
                    listModel.addElement(new PlayerItem(id, "Игрок " + id));
                } catch (NumberFormatException ignored) {}
            }
            lblStatus.setText("Доступно игроков: " + listModel.size());
            lblStatus.setForeground(TEXT_SECONDARY);
        }
    }

    class PlayerItem {
        int id;
        String name;
        PlayerItem(int id, String name) { this.id = id; this.name = name; }
    }

    class PlayerCellRenderer extends JPanel implements ListCellRenderer<PlayerItem> {
        private final JLabel iconLabel = new JLabel("👤");
        private final JLabel nameLabel = new JLabel();
        private final JLabel idLabel = new JLabel();
        private boolean isSelected;

        PlayerCellRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
            setOpaque(false);

            iconLabel.setFont(new Font("Segoe UI", Font.PLAIN, 20));

            JPanel textPanel = new JPanel(new GridLayout(2, 1));
            textPanel.setOpaque(false);
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            nameLabel.setForeground(TEXT_PRIMARY);
            idLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            idLabel.setForeground(TEXT_SECONDARY);
            textPanel.add(nameLabel);
            textPanel.add(idLabel);

            add(iconLabel, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends PlayerItem> list, PlayerItem value, 
                int index, boolean isSelected, boolean cellHasFocus) {
            this.isSelected = isSelected;
            nameLabel.setText(value.name);
            idLabel.setText("ID: " + value.id);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isSelected) {
                g2.setColor(new Color(244, 162, 97, 40));
                g2.fillRoundRect(4, 2, getWidth() - 8, getHeight() - 4, 10, 10);
                g2.setColor(ACCENT_GOLD);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(4, 2, getWidth() - 8, getHeight() - 4, 10, 10);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    class BattlePanel extends JPanel {
        private final BoardPanel myBoard;
        private final BoardPanel enemyBoard;
        private final JLabel lblStatus;
        private final JLabel lblStatusIcon;
        private final EventLog log;
        private final GradientButton btnSurrender;
        private final JLabel lblShipsRemaining;
        private final JPanel statusBanner;

        private boolean inGame = false;
        private boolean myTurn = false;
        private final int[][] myCells = new int[BOARD_SIZE][BOARD_SIZE];
        private final int[][] enemyCells = new int[BOARD_SIZE][BOARD_SIZE];
        private int myShipsSunk = 0;
        private int enemyShipsSunk = 0;

        BattlePanel() {
            setOpaque(false);
            setLayout(new BorderLayout(15, 15));
            setBorder(new EmptyBorder(15, 15, 15, 15));

            statusBanner = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(BG_PANEL);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    g2.setColor(BORDER_COLOR);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            statusBanner.setOpaque(false);
            statusBanner.setBorder(new EmptyBorder(12, 20, 12, 20));

            lblStatusIcon = new JLabel("⚔", SwingConstants.CENTER);
            lblStatusIcon.setFont(new Font("Segoe UI", Font.PLAIN, 24));

            lblStatus = new JLabel("Ожидание начала боя...", SwingConstants.CENTER);
            lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 18));
            lblStatus.setForeground(TEXT_SECONDARY);

            lblShipsRemaining = new JLabel("Ваши корабли: 10  |  Корабли врага: 10", SwingConstants.CENTER);
            lblShipsRemaining.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            lblShipsRemaining.setForeground(TEXT_SECONDARY);

            JPanel statusText = new JPanel(new GridLayout(2, 1, 0, 4));
            statusText.setOpaque(false);
            statusText.add(lblStatus);
            statusText.add(lblShipsRemaining);

            statusBanner.add(lblStatusIcon, BorderLayout.WEST);
            statusBanner.add(statusText, BorderLayout.CENTER);
            add(statusBanner, BorderLayout.NORTH);

            JPanel boards = new JPanel(new GridLayout(1, 2, 20, 0));
            boards.setOpaque(false);

            myBoard = new BoardPanel(BOARD_SIZE, CELL_SIZE, BoardPanel.Mode.MY_FIELD);
            enemyBoard = new BoardPanel(BOARD_SIZE, CELL_SIZE, BoardPanel.Mode.ENEMY_FIELD);

            boards.add(createBoardWrapper("🛡 Ваше поле", myBoard));
            boards.add(createBoardWrapper("🎯 Поле противника", enemyBoard));
            add(boards, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new BorderLayout(10, 10));
            bottom.setOpaque(false);

            log = new EventLog();
            JScrollPane logScroll = new JScrollPane(log);
            logScroll.setOpaque(false);
            logScroll.getViewport().setOpaque(false);
            logScroll.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(12, BORDER_COLOR, 1.5f),
                new EmptyBorder(8, 8, 8, 8)
            ));
            logScroll.setPreferredSize(new Dimension(0, 140));
            bottom.add(logScroll, BorderLayout.CENTER);

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            btnPanel.setOpaque(false);
            btnSurrender = new GradientButton("🏳 Сдаться", DANGER, new Color(185, 28, 28));
            btnSurrender.setPreferredSize(new Dimension(140, 40));
            btnSurrender.setFont(new Font("Segoe UI", Font.BOLD, 13));
            btnSurrender.addActionListener(e -> {
                send("SURRENDER");
                inGame = false;
                myTurn = false;
            });
            btnPanel.add(btnSurrender);
            bottom.add(btnPanel, BorderLayout.SOUTH);
            add(bottom, BorderLayout.SOUTH);

            enemyBoard.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!inGame || !myTurn) return;
                    int row = e.getY() / CELL_SIZE;
                    int col = e.getX() / CELL_SIZE;
                    if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) return;
                    if (enemyCells[row][col] != 0) {
                        log.addEvent("Уже стреляли сюда!", EventType.WARNING);
                        return;
                    }
                    send("MOVE:" + row + ":" + col);
                }
            });

            enemyBoard.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    if (!inGame || !myTurn) return;
                    int row = e.getY() / CELL_SIZE;
                    int col = e.getX() / CELL_SIZE;
                    if (row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE && enemyCells[row][col] == 0) {
                        enemyBoard.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    } else {
                        enemyBoard.setCursor(Cursor.getDefaultCursor());
                    }
                }
            });
        }

        private JPanel createBoardWrapper(String title, BoardPanel board) {
            JLabel lbl = new JLabel(title, SwingConstants.CENTER);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
            lbl.setForeground(TEXT_SECONDARY);
            lbl.setBorder(new EmptyBorder(0, 0, 8, 0));

            JPanel wrapper = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(BG_PANEL);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    g2.setColor(BORDER_COLOR);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            wrapper.setOpaque(false);
            wrapper.setBorder(new EmptyBorder(10, 10, 10, 10));
            wrapper.add(lbl, BorderLayout.NORTH);
            wrapper.add(board, BorderLayout.CENTER);
            return wrapper;
        }

        void initGame(String info) {
            inGame = true;
            myTurn = false;
            myShipsSunk = 0;
            enemyShipsSunk = 0;
            lblStatus.setText("Бой начался! " + info);
            lblStatus.setForeground(TEXT_PRIMARY);
            lblStatusIcon.setText("⚔");
            lblShipsRemaining.setText("Ваши корабли: 10  |  Корабли врага: 10");
            log.clear();
            log.addEvent("=== Бой начался ===", EventType.SYSTEM);
            log.addEvent(info, EventType.SYSTEM);

            for (int i = 0; i < BOARD_SIZE; i++) {
                Arrays.fill(myCells[i], 0);
                Arrays.fill(enemyCells[i], 0);
            }
            boolean[][] myField = setupPanel.getField();
            for (int i = 0; i < BOARD_SIZE; i++) {
                for (int j = 0; j < BOARD_SIZE; j++) {
                    myCells[i][j] = myField[i][j] ? 1 : 0;
                }
            }
            myBoard.setCells(copyCells(myCells));
            enemyBoard.setCells(copyCells(enemyCells));
        }

        void setMyTurn(boolean turn) {
            myTurn = turn;
            if (turn) {
                lblStatus.setText("ВАШ ХОД — выберите клетку на поле противника");
                lblStatus.setForeground(SUCCESS);
                lblStatusIcon.setText("🎯");
                log.addEvent("Ваш ход!", EventType.SUCCESS);
            } else {
                lblStatus.setText("Ход противника...");
                lblStatus.setForeground(TEXT_SECONDARY);
                lblStatusIcon.setText("⏳");
            }
        }

        void applyMyMove(int x, int y, String result) {
            switch (result) {
                case "HIT":
                    enemyCells[x][y] = 2;
                    log.addEvent("Выстрел (" + x + "," + y + "): Попадание!", EventType.SUCCESS);
                    break;
                case "MISS":
                    enemyCells[x][y] = 3;
                    log.addEvent("Выстрел (" + x + "," + y + "): Мимо", EventType.INFO);
                    break;
                case "SUNK":
                    enemyCells[x][y] = 2;
                    enemyShipsSunk++;
                    lblShipsRemaining.setText("Ваши корабли: " + (10 - myShipsSunk) + "  |  Корабли врага: " + (10 - enemyShipsSunk));
                    log.addEvent("Выстрел (" + x + "," + y + "): КОРАБЛЬ УНИЧТОЖЕН!", EventType.SUCCESS);
                    break;
                case "WIN":
                    enemyCells[x][y] = 2;
                    log.addEvent("Выстрел (" + x + "," + y + "): ПОБЕДА!", EventType.SUCCESS);
                    break;
            }
            enemyBoard.setCells(copyCells(enemyCells));
        }

        void applyOpponentMove(int x, int y, String result) {
            switch (result) {
                case "HIT":
                    myCells[x][y] = 2;
                    log.addEvent("Противник (" + x + "," + y + "): Попадание по вашему кораблю!", EventType.WARNING);
                    break;
                case "MISS":
                    myCells[x][y] = 3;
                    log.addEvent("Противник (" + x + "," + y + "): Мимо", EventType.INFO);
                    break;
                case "SUNK":
                    myCells[x][y] = 2;
                    myShipsSunk++;
                    lblShipsRemaining.setText("Ваши корабли: " + (10 - myShipsSunk) + "  |  Корабли врага: " + (10 - enemyShipsSunk));
                    log.addEvent("Противник (" + x + "," + y + "): Ваш корабль УНИЧТОЖЕН!", EventType.DANGER);
                    break;
                case "WIN":
                    myCells[x][y] = 2;
                    log.addEvent("Противник (" + x + "," + y + "): Потопил последний корабль", EventType.DANGER);
                    break;
            }
            myBoard.setCells(copyCells(myCells));
        }

        void showAgain() {
            log.addEvent("Попадание! Ходите ещё", EventType.SUCCESS);
            lblStatus.setText("ВАШ ХОД (попадание!) — стреляйте ещё");
            lblStatus.setForeground(new Color(250, 204, 21));
        }

        void endGame() {
            inGame = false;
            myTurn = false;
        }

        void log(String msg) {
            log.addEvent(msg, EventType.INFO);
        }

        private int[][] copyCells(int[][] src) {
            int[][] dst = new int[src.length][];
            for (int i = 0; i < src.length; i++) dst[i] = src[i].clone();
            return dst;
        }
    }

    enum EventType { SYSTEM, SUCCESS, WARNING, DANGER, INFO }

    class EventLog extends JPanel {
        private final List<EventItem> events = new ArrayList<>();
        private final JPanel contentPanel;

        EventLog() {
            setLayout(new BorderLayout());
            setOpaque(false);
            contentPanel = new JPanel();
            contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
            contentPanel.setOpaque(false);
            add(contentPanel, BorderLayout.NORTH);
        }

        void addEvent(String message, EventType type) {
            EventItem item = new EventItem(message, type);
            events.add(item);
            contentPanel.add(item);
            contentPanel.add(Box.createVerticalStrut(4));
            contentPanel.revalidate();

            SwingUtilities.invokeLater(() -> {
                Rectangle bounds = item.getBounds();
                scrollRectToVisible(bounds);
            });
        }

        void clear() {
            events.clear();
            contentPanel.removeAll();
            contentPanel.revalidate();
            contentPanel.repaint();
        }
    }

    class EventItem extends JPanel {
        EventItem(String message, EventType type) {
            setLayout(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

            Color textColor;
            String prefix;
            switch (type) {
                case SUCCESS: textColor = SUCCESS; prefix = "✓ "; break;
                case WARNING: textColor = ACCENT_GOLD; prefix = "⚠ "; break;
                case DANGER: textColor = DANGER; prefix = "✗ "; break;
                case SYSTEM: textColor = TEXT_SECONDARY; prefix = "• "; break;
                default: textColor = TEXT_PRIMARY; prefix = "→ "; break;
            }

            JLabel label = new JLabel(prefix + message);
            label.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
            label.setForeground(textColor);
            add(label, BorderLayout.WEST);
        }
    }

    static class BoardPanel extends JPanel {
        enum Mode { SETUP, MY_FIELD, ENEMY_FIELD }
        private final int bSize;
        private final int cSize;
        private final Mode mode;
        private int[][] cells;

        private int hRow = -1, hCol = -1, hSize = 0;
        private boolean hHoriz = true, hValid = false;
        private final List<CellAnimation> animations = new ArrayList<>();
        private javax.swing.Timer animTimer;

        BoardPanel(int size, int cellSize, Mode mode) {
            this.bSize = size;
            this.cSize = cellSize;
            this.mode = mode;
            this.cells = new int[size][size];
            setPreferredSize(new Dimension(size * cellSize + 1, size * cellSize + 1));
            setOpaque(false);

            animTimer = new javax.swing.Timer(16, e -> {
                boolean needsRepaint = false;
                for (CellAnimation anim : animations) {
                    anim.progress += 0.05f;
                    if (anim.progress >= 1f) {
                        anim.finished = true;
                    } else {
                        needsRepaint = true;
                    }
                }
                animations.removeIf(a -> a.finished);
                if (needsRepaint || !animations.isEmpty()) {
                    repaint();
                } else {
                    animTimer.stop();
                }
            });
        }

        void setCells(int[][] c) {
            for (int i = 0; i < bSize; i++) {
                for (int j = 0; j < bSize; j++) {
                    if (cells[i][j] != c[i][j]) {
                        if (c[i][j] == 2) {
                            animations.add(new CellAnimation(i, j, AnimationType.EXPLOSION));
                        } else if (c[i][j] == 3) {
                            animations.add(new CellAnimation(i, j, AnimationType.SPLASH));
                        }
                    }
                }
            }
            this.cells = c;
            repaint();
            if (!animations.isEmpty() && !animTimer.isRunning()) {
                animTimer.start();
            }
        }

        void setCell(int r, int col, int v) {
            cells[r][col] = v;
            repaint();
        }

        void setHighlight(int r, int col, int size, boolean horiz, boolean valid) {
            hRow = r; hCol = col; hSize = size; hHoriz = horiz; hValid = valid;
            repaint();
        }

        void clearHighlight() {
            hRow = -1; hCol = -1; hSize = 0;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            for (int i = 0; i < bSize; i++) {
                for (int j = 0; j < bSize; j++) {
                    int x = j * cSize;
                    int y = i * cSize;
                    int st = cells[i][j];

                    paintCell(g2, x, y, st, i, j);
                }
            }

            for (CellAnimation anim : animations) {
                paintAnimation(g2, anim);
            }

            if (mode == Mode.SETUP && hSize > 0 && hRow >= 0 && hCol >= 0) {
                Color highlightColor = hValid 
                    ? new Color(42, 157, 143, 120) 
                    : new Color(239, 68, 68, 120);
                for (int k = 0; k < hSize; k++) {
                    int r = hRow + (hHoriz ? 0 : k);
                    int c = hCol + (hHoriz ? k : 0);
                    if (r >= 0 && r < bSize && c >= 0 && c < bSize) {
                        g2.setColor(highlightColor);
                        g2.fillRoundRect(c * cSize + 2, r * cSize + 2, cSize - 3, cSize - 3, 6, 6);
                    }
                }
            }

            g2.setColor(TEXT_SECONDARY);
            g2.setFont(new Font("JetBrains Mono", Font.BOLD, 11));
            for (int i = 0; i < bSize; i++) {
                g2.drawString(String.valueOf(i), 4, i * cSize + cSize/2 + 5);
                g2.drawString(String.valueOf(i), i * cSize + cSize/2 - 4, bSize * cSize + 16);
            }

            g2.dispose();
        }

        private void paintCell(Graphics2D g2, int x, int y, int st, int row, int col) {
            int pad = 1;
            int w = cSize - pad * 2;
            int h = cSize - pad * 2;

            GradientPaint waterGrad = new GradientPaint(
                x, y, WATER_MID,
                x, y + h, WATER_DEEP
            );

            switch (st) {
                case 1:
                    if (mode != Mode.ENEMY_FIELD) {
                        GradientPaint shipGrad = new GradientPaint(
                            x, y, SHIP_HIGHLIGHT,
                            x, y + h, SHIP_BASE
                        );
                        g2.setPaint(shipGrad);
                        g2.fillRoundRect(x + pad, y + pad, w, h, 6, 6);
                        g2.setColor(new Color(70, 80, 100));
                        g2.setStroke(new BasicStroke(1f));
                        g2.drawRoundRect(x + pad, y + pad, w, h, 6, 6);
                        g2.drawLine(x + pad + 4, y + pad + h/2, x + pad + w - 4, y + pad + h/2);
                    } else {
                        g2.setPaint(waterGrad);
                        g2.fillRect(x + pad, y + pad, w, h);
                    }
                    break;

                case 2:
                    GradientPaint hitGrad = new GradientPaint(
                        x, y, HIT_RED,
                        x, y + h, new Color(180, 30, 50)
                    );
                    g2.setPaint(hitGrad);
                    g2.fillRoundRect(x + pad, y + pad, w, h, 6, 6);

                    g2.setColor(HIT_ORANGE);
                    g2.setStroke(new BasicStroke(2.5f));
                    int cx = x + cSize/2;
                    int cy = y + cSize/2;
                    int r1 = cSize/3;
                    int r2 = cSize/5;
                    for (int a = 0; a < 8; a++) {
                        double angle = Math.PI * a / 4;
                        int x1 = cx + (int)(r2 * Math.cos(angle));
                        int y1 = cy + (int)(r2 * Math.sin(angle));
                        int x2 = cx + (int)(r1 * Math.cos(angle));
                        int y2 = cy + (int)(r1 * Math.sin(angle));
                        g2.drawLine(x1, y1, x2, y2);
                    }
                    g2.setColor(new Color(255, 200, 100, 180));
                    g2.fillOval(cx - 4, cy - 4, 8, 8);
                    break;

                case 3:
                    g2.setPaint(waterGrad);
                    g2.fillRect(x + pad, y + pad, w, h);

                    g2.setColor(MISS_BLUE);
                    g2.setStroke(new BasicStroke(2f));
                    int rippleR = cSize/4;
                    g2.drawOval(x + cSize/2 - rippleR, y + cSize/2 - rippleR, rippleR*2, rippleR*2);
                    g2.setColor(MISS_LIGHT);
                    g2.fillOval(x + cSize/2 - 3, y + cSize/2 - 3, 6, 6);
                    break;

                default:
                    g2.setPaint(waterGrad);
                    g2.fillRect(x + pad, y + pad, w, h);

                    if ((row * 7 + col * 13) % 23 == 0) {
                        g2.setColor(new Color(100, 160, 200, 40));
                        int br = 2 + ((row + col) % 3);
                        g2.fillOval(x + cSize/2 - br, y + cSize/2 - br, br*2, br*2);
                    }
                    break;
            }

            g2.setColor(new Color(60, 100, 130, 80));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(x, y, cSize, cSize);
        }

        private void paintAnimation(Graphics2D g2, CellAnimation anim) {
            int x = anim.col * cSize;
            int y = anim.row * cSize;
            int cx = x + cSize/2;
            int cy = y + cSize/2;

            float alpha = 1f - anim.progress;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            if (anim.type == AnimationType.EXPLOSION) {
                int maxR = cSize;
                int r = (int)(anim.progress * maxR);
                g2.setColor(new Color(255, 200, 50, (int)(200 * alpha)));
                g2.setStroke(new BasicStroke(3f * alpha));
                g2.drawOval(cx - r, cy - r, r*2, r*2);

                int r2 = (int)(anim.progress * maxR * 0.6f);
                g2.setColor(new Color(255, 100, 50, (int)(150 * alpha)));
                g2.drawOval(cx - r2, cy - r2, r2*2, r2*2);

                g2.setColor(new Color(255, 255, 200, (int)(255 * alpha)));
                Random rand = new Random(anim.row * 31 + anim.col * 17);
                for (int p = 0; p < 6; p++) {
                    double angle = rand.nextDouble() * Math.PI * 2;
                    int dist = (int)(anim.progress * cSize * 0.8);
                    int px = cx + (int)(dist * Math.cos(angle));
                    int py = cy + (int)(dist * Math.sin(angle));
                    int ps = (int)(4 * alpha);
                    g2.fillOval(px - ps/2, py - ps/2, ps, ps);
                }
            } else if (anim.type == AnimationType.SPLASH) {
                int maxR = cSize;
                int r = (int)(anim.progress * maxR);
                g2.setColor(new Color(168, 218, 220, (int)(150 * alpha)));
                g2.setStroke(new BasicStroke(2f * alpha));
                g2.drawOval(cx - r, cy - r, r*2, r*2);

                int r2 = (int)(anim.progress * maxR * 0.5f);
                g2.setColor(new Color(200, 235, 240, (int)(100 * alpha)));
                g2.drawOval(cx - r2, cy - r2, r2*2, r2*2);
            }

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        enum AnimationType { EXPLOSION, SPLASH }

        class CellAnimation {
            int row, col;
            AnimationType type;
            float progress = 0;
            boolean finished = false;

            CellAnimation(int row, int col, AnimationType type) {
                this.row = row; this.col = col; this.type = type;
            }
        }
    }

    class GradientButton extends JButton {
        private final Color color1, color2;
        private boolean hovered = false;
        private boolean pressed = false;

        GradientButton(String text, Color color1, Color color2) {
            super(text);
            this.color1 = color1;
            this.color2 = color2;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setForeground(Color.WHITE);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                @Override
                public void mouseExited(MouseEvent e) { hovered = false; pressed = false; repaint(); }
                @Override
                public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
                @Override
                public void mouseReleased(MouseEvent e) { pressed = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            Color c1 = hovered ? color1.brighter() : color1;
            Color c2 = hovered ? color2.brighter() : color2;
            if (pressed) {
                c1 = c1.darker();
                c2 = c2.darker();
            }

            GradientPaint gp = new GradientPaint(0, 0, c1, 0, h, c2);
            g2.setPaint(gp);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);

            g2.setColor(new Color(255, 255, 255, 30));
            g2.fillRoundRect(0, 0, w - 1, h / 2, 10, 10);

            g2.setColor(new Color(255, 255, 255, 40));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    class RoundedTextField extends JTextField {
        RoundedTextField(String text, int columns) {
            super(text, columns);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            setBackground(BG_PANEL);
            setForeground(TEXT_PRIMARY);
            setCaretColor(TEXT_PRIMARY);
            setFont(new Font("Segoe UI", Font.PLAIN, 14));

            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) { repaint(); }
                @Override
                public void focusLost(FocusEvent e) { repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

            boolean focused = hasFocus();
            g2.setColor(focused ? ACCENT_GOLD : BORDER_COLOR);
            g2.setStroke(new BasicStroke(focused ? 2f : 1.5f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    class RoundedBorder extends AbstractBorder {
        private final int radius;
        private final Color color;
        private final float thickness;

        RoundedBorder(int radius, Color color, float thickness) {
            this.radius = radius;
            this.color = color;
            this.thickness = thickness;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(radius/2, radius/2, radius/2, radius/2);
        }
    }

    public static void main(String[] args) {
        String os = System.getProperty("os.name").toLowerCase();
        boolean isLinux = os.contains("linux");

        if (isLinux) {
            System.setProperty("java.awt.headless", "false");
            System.setProperty("awt.toolkit", "sun.awt.X11.XToolkit");
            System.setProperty("sun.java2d.uiScale", "1");
            System.setProperty("awt.useSystemAAFontSettings", "on");
            System.setProperty("swing.aatext", "true");
            String wmNonreparent = System.getenv("_JAVA_AWT_WM_NONREPARENTING");
            if (wmNonreparent == null) {
                System.setProperty("_JAVA_AWT_WM_NONREPARENTING", "1");
            }
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("ERROR: No graphical display available.");
            System.exit(1);
        }

        SwingUtilities.invokeLater(() -> {
            BattleshipClientGUI frame = new BattleshipClientGUI();
            frame.setVisible(true);
        });
    }
}
