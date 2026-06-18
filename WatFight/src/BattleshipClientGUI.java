import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

public class BattleshipClientGUI extends JFrame {
    private static final int BOARD_SIZE = 10;
    private static final int[] SHIP_SIZES = {4, 3, 3, 2, 2, 2, 1, 1, 1, 1};
    private static final int CELL_SIZE = 44;

    private CardLayout cardLayout;
    private JPanel mainPanel;

    // Network
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private Thread receiverThread;
    private volatile boolean connected = false;

    // Panels
    private final ConnectionPanel connectionPanel;
    private final SetupPanel setupPanel;
    private final LobbyPanel lobbyPanel;
    private final BattlePanel battlePanel;

    public BattleshipClientGUI() {
        super("Морской бой");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        mainPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        connectionPanel = new ConnectionPanel();
        setupPanel = new SetupPanel();
        lobbyPanel = new LobbyPanel();
        battlePanel = new BattlePanel();

        mainPanel.add(connectionPanel, "CONNECT");
        mainPanel.add(setupPanel, "SETUP");
        mainPanel.add(lobbyPanel, "LOBBY");
        mainPanel.add(battlePanel, "BATTLE");

        add(mainPanel);
        pack();
        setLocationRelativeTo(null);
        showPanel("CONNECT");
    }

    private void showPanel(String name) {
        cardLayout.show(mainPanel, name);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    /* ========================= NETWORK ========================= */

    private void connect(String host, int port) {
        new Thread(() -> {
            try {
                socket = new Socket(host, port);
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
                socket.setSoTimeout(5000);
                connected = true;

                SwingUtilities.invokeLater(() -> {
                    connectionPanel.setStatus("Ожидание запроса поля от сервера...");
                });

                receiverThread = new Thread(this::receiveLoop, "NetworkReceiver");
                receiverThread.setDaemon(true);
                receiverThread.start();

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                        "Не удалось подключиться: " + e.getMessage(),
                        "Ошибка сети", JOptionPane.ERROR_MESSAGE);
                    connectionPanel.setStatus("Не подключено");
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
                    JOptionPane.showMessageDialog(this,
                        "Соединение с сервером потеряно.",
                        "Разрыв", JOptionPane.WARNING_MESSAGE);
                    disconnect();
                    showPanel("CONNECT");
                    connectionPanel.setConnectEnabled(true);
                    connectionPanel.setStatus("Не подключено");
                });
            }
        }
    }

    private void handleMessage(String msg) {
        if (msg.equals("CMD_REQUIRE_FIELD")) {
            setupPanel.reset();
            showPanel("SETUP");
        } else if (msg.startsWith("CMD_OK")) {
            try { socket.setSoTimeout(0); } catch (IOException ignored) {}
            showPanel("LOBBY");
            lobbyPanel.refreshPlayers();
        } else if (msg.startsWith("CMD_ERROR:")) {
            String err = msg.substring(10);
            JOptionPane.showMessageDialog(this, err, "Ошибка сервера", JOptionPane.ERROR_MESSAGE);
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
            JOptionPane.showMessageDialog(this,
                win ? "ПОБЕДА! Вы уничтожили все корабли противника." : "ПОРАЖЕНИЕ! Ваш флот уничтожен.",
                "Игра окончена", win ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
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
            JOptionPane.showMessageDialog(this, "Ошибка отправки: " + e.getMessage(), "Сеть", JOptionPane.ERROR_MESSAGE);
        }
    }

    /* ========================= PANELS ========================= */

    class ConnectionPanel extends JPanel {
        private final JTextField txtHost = new JTextField("127.0.0.1", 12);
        private final JTextField txtPort = new JTextField("12345", 6);
        private final JLabel lblStatus = new JLabel("Не подключено");
        private final JButton btnConnect = new JButton("Подключиться");

        ConnectionPanel() {
            setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6, 6, 6, 6);

            gbc.gridx = 0; gbc.gridy = 0;
            add(new JLabel("Сервер:"), gbc);
            gbc.gridx = 1;
            add(txtHost, gbc);

            gbc.gridx = 0; gbc.gridy = 1;
            add(new JLabel("Порт:"), gbc);
            gbc.gridx = 1;
            add(txtPort, gbc);

            gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
            add(btnConnect, gbc);

            gbc.gridy = 3;
            add(lblStatus, gbc);

            btnConnect.addActionListener(e -> {
                btnConnect.setEnabled(false);
                lblStatus.setText("Подключение...");
                connect(txtHost.getText().trim(), Integer.parseInt(txtPort.getText().trim()));
            });
        }

        void setStatus(String s) { lblStatus.setText(s); }
        void setConnectEnabled(boolean b) { btnConnect.setEnabled(b); }
    }

    class SetupPanel extends JPanel {
        private final BoardPanel board;
        private final JPanel shipPalette = new JPanel();
        private final JLabel lblInfo = new JLabel("Выберите корабль и кликните на поле");
        private final JButton btnRotate = new JButton("Повернуть (R)");
        private final JButton btnAuto = new JButton("Авто");
        private final JButton btnClear = new JButton("Очистить");
        private final JButton btnReady = new JButton("Готов");

        private boolean[][] field = new boolean[BOARD_SIZE][BOARD_SIZE];
        private final List<Integer> remainingShips = new ArrayList<>();
        private int selectedShipSize = -1;
        private boolean horizontal = true;

        SetupPanel() {
            setLayout(new BorderLayout(10, 10));
            setBorder(new EmptyBorder(10, 10, 10, 10));

            lblInfo.setFont(new Font("Segoe UI", Font.BOLD, 14));
            add(lblInfo, BorderLayout.NORTH);

            board = new BoardPanel(BOARD_SIZE, CELL_SIZE, BoardPanel.Mode.SETUP);
            add(board, BorderLayout.CENTER);

            // Palette
            shipPalette.setLayout(new GridLayout(0, 1, 6, 6));
            shipPalette.setBorder(new TitledBorder("Доступные корабли"));
            add(shipPalette, BorderLayout.EAST);

            // Bottom controls
            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            btnReady.setEnabled(false);
            btnReady.setBackground(new Color(50, 150, 50));
            btnReady.setForeground(Color.WHITE);
            btnReady.setFocusPainted(false);
            bottom.add(btnRotate);
            bottom.add(btnAuto);
            bottom.add(btnClear);
            bottom.add(btnReady);
            add(bottom, BorderLayout.SOUTH);

            // Listeners
            btnRotate.addActionListener(e -> {
                horizontal = !horizontal;
                board.repaint();
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
            lblInfo.setText("Выберите корабль и кликните на поле");
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
                    JButton btn = new JButton(size + " палуб ×" + cnt);
                    btn.setFocusPainted(false);
                    btn.addActionListener(e -> {
                        selectedShipSize = size;
                        lblInfo.setText("Выбран " + size + "-палубный. Клик на поле. Поворот: " + (horizontal ? "→" : "↓") + " (R)");
                    });
                    shipPalette.add(btn);
                }
            }
            lblInfo.setText("Осталось разместить: " + remainingShips.size() + " кораблей");
            shipPalette.revalidate();
            shipPalette.repaint();
        }

        private void checkReady() {
            btnReady.setEnabled(remainingShips.isEmpty());
            if (remainingShips.isEmpty()) {
                lblInfo.setText("Все корабли на месте! Нажмите «Готов»");
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
        }

        private void submitField() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < BOARD_SIZE; i++)
                for (int j = 0; j < BOARD_SIZE; j++)
                    sb.append(field[i][j] ? '1' : '0');
            send("SET_FIELD:" + sb.toString());
        }

        // ----- Field logic from Client.java -----
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
        private final DefaultListModel<String> listModel = new DefaultListModel<>();
        private final JList<String> playerList = new JList<>(listModel);
        private final JButton btnRefresh = new JButton("Обновить список");
        private final JButton btnChallenge = new JButton("Вызвать на бой");

        LobbyPanel() {
            setLayout(new BorderLayout(10, 10));
            setBorder(new EmptyBorder(10, 10, 10, 10));
            add(new JLabel("Доступные игроки в лобби:"), BorderLayout.NORTH);

            playerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            playerList.setFont(new Font("Consolas", Font.PLAIN, 14));
            JScrollPane sp = new JScrollPane(playerList);
            sp.setPreferredSize(new Dimension(300, 200));
            add(sp, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout());
            bottom.add(btnRefresh);
            bottom.add(btnChallenge);
            add(bottom, BorderLayout.SOUTH);

            btnRefresh.addActionListener(e -> refreshPlayers());
            btnChallenge.addActionListener(e -> {
                String sel = playerList.getSelectedValue();
                if (sel == null || sel.startsWith("(")) {
                    JOptionPane.showMessageDialog(this, "Выберите противника из списка", "Внимание", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                int id = Integer.parseInt(sel.split(" ")[0]);
                send("SELECT_PLAYER:" + id);
            });
        }

        void refreshPlayers() {
            listModel.clear();
            send("GET_PLAYERS");
        }

        void updatePlayers(String data) {
            listModel.clear();
            if (data.isEmpty()) {
                listModel.addElement("(нет свободных игроков)");
                return;
            }
            for (String id : data.split(",")) {
                listModel.addElement(id + " — игрок");
            }
        }
    }

    class BattlePanel extends JPanel {
        private final BoardPanel myBoard;
        private final BoardPanel enemyBoard;
        private final JLabel lblStatus = new JLabel("Ожидание начала боя...");
        private final JTextArea log = new JTextArea(6, 40);
        private final JButton btnSurrender = new JButton("Сдаться");

        private boolean inGame = false;
        private boolean myTurn = false;
        private final int[][] myCells = new int[BOARD_SIZE][BOARD_SIZE];   // 0 water, 1 ship, 2 hit, 3 miss
        private final int[][] enemyCells = new int[BOARD_SIZE][BOARD_SIZE]; // 0 unknown, 2 hit, 3 miss

        BattlePanel() {
            setLayout(new BorderLayout(10, 10));
            setBorder(new EmptyBorder(10, 10, 10, 10));

            // Top status
            lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 16));
            lblStatus.setHorizontalAlignment(SwingConstants.CENTER);
            add(lblStatus, BorderLayout.NORTH);

            // Center boards
            JPanel boards = new JPanel(new GridLayout(1, 2, 20, 0));
            myBoard = new BoardPanel(BOARD_SIZE, CELL_SIZE, BoardPanel.Mode.MY_FIELD);
            enemyBoard = new BoardPanel(BOARD_SIZE, CELL_SIZE, BoardPanel.Mode.ENEMY_FIELD);

            JPanel leftWrap = new JPanel(new BorderLayout());
            leftWrap.add(new JLabel("Ваше поле", SwingConstants.CENTER), BorderLayout.NORTH);
            leftWrap.add(myBoard, BorderLayout.CENTER);

            JPanel rightWrap = new JPanel(new BorderLayout());
            rightWrap.add(new JLabel("Поле противника", SwingConstants.CENTER), BorderLayout.NORTH);
            rightWrap.add(enemyBoard, BorderLayout.CENTER);

            boards.add(leftWrap);
            boards.add(rightWrap);
            add(boards, BorderLayout.CENTER);

            // Bottom: log + surrender
            JPanel bottom = new JPanel(new BorderLayout(5, 5));
            log.setEditable(false);
            log.setFont(new Font("Consolas", Font.PLAIN, 12));
            JScrollPane logScroll = new JScrollPane(log);
            bottom.add(logScroll, BorderLayout.CENTER);

            JPanel btnPanel = new JPanel(new FlowLayout());
            btnSurrender.addActionListener(e -> {
                send("SURRENDER");
                inGame = false;
                myTurn = false;
            });
            btnPanel.add(btnSurrender);
            bottom.add(btnPanel, BorderLayout.SOUTH);
            add(bottom, BorderLayout.SOUTH);

            // Enemy board click
            enemyBoard.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!inGame || !myTurn) return;
                    int row = e.getY() / CELL_SIZE;
                    int col = e.getX() / CELL_SIZE;
                    if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) return;
                    if (enemyCells[row][col] != 0) {
                        log("Уже стреляли сюда!");
                        return;
                    }
                    send("MOVE:" + row + ":" + col);
                }
            });
        }

        void initGame(String info) {
            inGame = true;
            myTurn = false;
            lblStatus.setText("Бой начался! " + info);
            log.setText("");
            log("=== Бой начался ===");
            log(info);

            for (int i = 0; i < BOARD_SIZE; i++) {
                Arrays.fill(myCells[i], 0);
                Arrays.fill(enemyCells[i], 0);
            }
            // Load my field from setup
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
            lblStatus.setText(turn ? "ВАШ ХОД — кликайте по полю противника" : "Ход противника...");
            lblStatus.setForeground(turn ? new Color(0, 128, 0) : Color.GRAY);
            if (turn) log("Ваш ход!");
        }

        void applyMyMove(int x, int y, String result) {
            if (result.equals("HIT")) {
                enemyCells[x][y] = 2;
                log("Выстрел (" + x + "," + y + "): ПОПАДАНИЕ!");
            } else if (result.equals("MISS")) {
                enemyCells[x][y] = 3;
                log("Выстрел (" + x + "," + y + "): Мимо.");
            } else if (result.equals("WIN")) {
                enemyCells[x][y] = 2;
                log("Выстрел (" + x + "," + y + "): ПОТОПЛЕН! Победа!");
            }
            enemyBoard.setCells(copyCells(enemyCells));
        }

        void applyOpponentMove(int x, int y, String result) {
            if (result.equals("HIT")) {
                myCells[x][y] = 2;
                log("Противник (" + x + "," + y + "): ПОПАДАНИЕ по вашему кораблю!");
            } else if (result.equals("MISS")) {
                myCells[x][y] = 3;
                log("Противник (" + x + "," + y + "): Мимо.");
            } else if (result.equals("WIN")) {
                myCells[x][y] = 2;
                log("Противник (" + x + "," + y + "): Потопил последний корабль.");
            }
            myBoard.setCells(copyCells(myCells));
        }

        void showAgain() {
            log("Попадание! Ходите ещё.");
            lblStatus.setText("ВАШ ХОД (попадание!) — стреляйте ещё");
        }

        void endGame() {
            inGame = false;
            myTurn = false;
        }

        void log(String msg) {
            log.append(msg + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        }

        private int[][] copyCells(int[][] src) {
            int[][] dst = new int[src.length][];
            for (int i = 0; i < src.length; i++) dst[i] = src[i].clone();
            return dst;
        }
    }

    /* ========================= BOARD PANEL ========================= */

    static class BoardPanel extends JPanel {
        enum Mode { SETUP, MY_FIELD, ENEMY_FIELD }
        private final int bSize;
        private final int cSize;
        private final Mode mode;
        private int[][] cells;

        private int hRow = -1, hCol = -1, hSize = 0;
        private boolean hHoriz = true, hValid = false;

        BoardPanel(int size, int cellSize, Mode mode) {
            this.bSize = size;
            this.cSize = cellSize;
            this.mode = mode;
            this.cells = new int[size][size];
            setPreferredSize(new Dimension(size * cellSize + 1, size * cellSize + 1));
            setBackground(new Color(0, 90, 160));
        }

        void setCells(int[][] c) {
            this.cells = c;
            repaint();
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
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            for (int i = 0; i < bSize; i++) {
                for (int j = 0; j < bSize; j++) {
                    int x = j * cSize;
                    int y = i * cSize;
                    int st = cells[i][j];

                    // Background
                    if (st == 1 && mode != Mode.ENEMY_FIELD) {
                        g2.setColor(new Color(110, 110, 110)); // ship
                    } else if (st == 2) {
                        g2.setColor(new Color(210, 60, 60)); // hit
                    } else if (st == 3) {
                        g2.setColor(new Color(180, 200, 230)); // miss
                    } else {
                        g2.setColor(new Color(30, 120, 200)); // water
                    }
                    g2.fillRect(x + 1, y + 1, cSize - 1, cSize - 1);

                    // Grid
                    g2.setColor(new Color(0, 40, 80));
                    g2.drawRect(x, y, cSize, cSize);

                    // Markers
                    if (st == 2) {
                        g2.setColor(Color.WHITE);
                        g2.setStroke(new BasicStroke(2.5f));
                        int pad = 6;
                        g2.drawLine(x + pad, y + pad, x + cSize - pad, y + cSize - pad);
                        g2.drawLine(x + cSize - pad, y + pad, x + pad, y + cSize - pad);
                    } else if (st == 3) {
                        g2.setColor(Color.WHITE);
                        int r = 4;
                        g2.fillOval(x + cSize/2 - r, y + cSize/2 - r, r*2, r*2);
                    }
                }
            }

            // Setup highlight
            if (mode == Mode.SETUP && hSize > 0 && hRow >= 0 && hCol >= 0) {
                g2.setColor(hValid ? new Color(0, 255, 0, 120) : new Color(255, 0, 0, 120));
                for (int k = 0; k < hSize; k++) {
                    int r = hRow + (hHoriz ? 0 : k);
                    int c = hCol + (hHoriz ? k : 0);
                    if (r >= 0 && r < bSize && c >= 0 && c < bSize) {
                        g2.fillRect(c * cSize + 1, r * cSize + 1, cSize - 1, cSize - 1);
                    }
                }
            }

            // Coordinates
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("Arial", Font.BOLD, 11));
            for (int i = 0; i < bSize; i++) {
                g2.drawString(String.valueOf(i), 3, i * cSize + cSize/2 + 4);
                g2.drawString(String.valueOf(i), i * cSize + cSize/2 - 3, bSize * cSize + 14);
            }
        }
    }

    public static void main(String[] args) {
        // --- Linux Wayland / X11 compatibility ---
        String os = System.getProperty("os.name").toLowerCase();
        boolean isLinux = os.contains("linux");

        if (isLinux) {
            // Force X11 backend for Java Swing (works via XWayland on Wayland)
            System.setProperty("java.awt.headless", "false");
            System.setProperty("awt.toolkit", "sun.awt.X11.XToolkit");
            System.setProperty("sun.java2d.uiScale", "1");
            System.setProperty("awt.useSystemAAFontSettings", "on");
            System.setProperty("swing.aatext", "true");

            // Ensure DISPLAY is set for XWayland
            String display = System.getenv("DISPLAY");
            if (display == null || display.isEmpty()) {
                System.err.println("WARNING: DISPLAY not set. Trying :0 ...");
            }

            // Workaround for some KDE/Wayland compositors
            String wmNonreparent = System.getenv("_JAVA_AWT_WM_NONREPARENTING");
            if (wmNonreparent == null) {
                System.setProperty("_JAVA_AWT_WM_NONREPARENTING", "1");
            }
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Check headless after setting properties
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("ERROR: No graphical display available.");
            System.err.println("If on Wayland, ensure XWayland is running: pgrep Xwayland");
            System.err.println("If remote, use: ssh -X user@host");
            System.exit(1);
        }

        SwingUtilities.invokeLater(() -> new BattleshipClientGUI().setVisible(true));
    }
}

