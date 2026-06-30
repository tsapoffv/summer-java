package client.panels;

import client.BattleshipClientGUI;
import client.components.BoardPanel;
import client.components.GradientButton;
import client.utils.Colors;
import client.utils.Constants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/**
 * Панель расстановки кораблей.
 *
 * Позволяет:
 * - Выбирать корабли из палитры
 * - Размещать корабли на поле кликом
 * - Поворачивать корабли клавишей R
 * - Автоматически расставлять корабли
 * - Очищать поле
 * - Отправлять готовое поле на сервер
 */
public class SetupPanel extends JPanel {
    private final BattleshipClientGUI client;
    private BoardPanel board;
    private JPanel shipPalette;
    private JLabel lblInfo;
    private GradientButton btnRotate;
    private GradientButton btnAuto;
    private GradientButton btnClear;
    private GradientButton btnReady;

    /** Игровое поле (true - корабль) */
    private boolean[][] field = new boolean[Constants.BOARD_SIZE][Constants.BOARD_SIZE];

    /** Оставшиеся корабли для расстановки */
    private final java.util.List<Integer> remainingShips = new ArrayList<>();

    /** Выбранный размер корабля */
    private int selectedShipSize = -1;

    /** Ориентация корабля (true - горизонтально) */
    private boolean horizontal = true;

    /**
     * Конструктор панели расстановки.
     * @param client ссылка на главное окно клиента
     */
    public SetupPanel(BattleshipClientGUI client) {
        this.client = client;
        setOpaque(false);
        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(15, 15, 15, 15));

        // Инициализация компонентов
        initComponents();

        // Заголовок
        JPanel header = createHeader();
        add(header, BorderLayout.NORTH);

        // Центральная часть: поле и палитра
        JPanel center = createCenterPanel();
        add(center, BorderLayout.CENTER);

        // Нижняя панель с кнопками
        JPanel bottom = createBottomPanel();
        add(bottom, BorderLayout.SOUTH);

        // Настройка горячих клавиш
        setupKeyBindings();

        // Обработчики мыши для поля
        setupMouseHandlers();

        // Инициализация
        reset();
    }

    /**
     * Инициализирует все компоненты.
     */
    private void initComponents() {
        board = new BoardPanel(Constants.BOARD_SIZE, Constants.CELL_SIZE, BoardPanel.Mode.SETUP);
        shipPalette = new JPanel();
        lblInfo = new JLabel("Выберите корабль и разместите на поле", SwingConstants.CENTER);
        btnRotate = new GradientButton(" Повернуть (R)", new Color(59, 130, 246), new Color(37, 99, 235));
        btnAuto = new GradientButton(" Авто", new Color(139, 92, 246), new Color(124, 58, 237));
        btnClear = new GradientButton(" Очистить", new Color(100, 116, 139), new Color(71, 85, 105));
        btnReady = new GradientButton(" Готов", Colors.ACCENT_TEAL, new Color(20, 130, 120));

        lblInfo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblInfo.setForeground(Colors.TEXT_SECONDARY);
        btnReady.setEnabled(false);
    }

    /**
     * Создаёт заголовок панели.
     */
    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Расстановка кораблей", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Colors.ACCENT_GOLD);
        header.add(title, BorderLayout.NORTH);

        header.add(lblInfo, BorderLayout.SOUTH);

        return header;
    }

    /**
     * Создаёт центральную панель с полем и палитрой.
     */
    private JPanel createCenterPanel() {
        JPanel center = new JPanel(new BorderLayout(20, 0));
        center.setOpaque(false);

        // Игровое поле
        JPanel boardWrap = createPanelWrapper("Ваше поле", board);
        center.add(boardWrap, BorderLayout.CENTER);

        // Палитра кораблей
        shipPalette.setLayout(new GridLayout(0, 1, 8, 8));
        shipPalette.setOpaque(false);
        shipPalette.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel paletteWrap = createPanelWrapper("Доступные корабли", shipPalette);
        paletteWrap.setPreferredSize(new Dimension(200, 0));
        center.add(paletteWrap, BorderLayout.EAST);

        return center;
    }

    /**
     * Создаёт нижнюю панель с кнопками управления.
     */
    private JPanel createBottomPanel() {
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        bottom.setOpaque(false);
        bottom.setBorder(new EmptyBorder(10, 0, 0, 0));

        for (GradientButton btn : new GradientButton[]{btnRotate, btnAuto, btnClear, btnReady}) {
            btn.setPreferredSize(new Dimension(130, 40));
            btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        }

        bottom.add(btnRotate);
        bottom.add(btnAuto);
        bottom.add(btnClear);
        bottom.add(btnReady);

        // Обработчики кнопок
        btnRotate.addActionListener(e -> {
            horizontal = !horizontal;
            board.repaint();
            updateInfo();
        });
        btnAuto.addActionListener(e -> autoPlace());
        btnClear.addActionListener(e -> reset());
        btnReady.addActionListener(e -> submitField());

        return bottom;
    }

    /**
     * Настраивает горячие клавиши.
     */
    private void setupKeyBindings() {
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
    }

    /**
     * Настраивает обработчики мыши для поля.
     */
    private void setupMouseHandlers() {
        // Обработка кликов для размещения корабля
        board.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (selectedShipSize <= 0) return;
                int row = e.getY() / Constants.CELL_SIZE;
                int col = e.getX() / Constants.CELL_SIZE;
                if (row < 0 || row >= Constants.BOARD_SIZE || col < 0 || col >= Constants.BOARD_SIZE) return;

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
                    lblInfo.setForeground(Colors.DANGER);
                }
            }
        });

        // Отображение предпросмотра при наведении
        board.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = e.getY() / Constants.CELL_SIZE;
                int col = e.getX() / Constants.CELL_SIZE;
                if (selectedShipSize > 0) {
                    boolean ok = canPlace(field, row, col, selectedShipSize, horizontal);
                    board.setHighlight(row, col, selectedShipSize, horizontal, ok);
                } else {
                    board.clearHighlight();
                }
            }
        });
    }

    /**
     * Создаёт обёртку для панели с заголовком.
     */
    private JPanel createPanelWrapper(String title, JComponent content) {
        JLabel lbl = new JLabel(title, SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lbl.setForeground(Colors.TEXT_SECONDARY);
        lbl.setBorder(new EmptyBorder(0, 0, 8, 0));

        JPanel wrapper = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Colors.BG_PANEL);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(Colors.BORDER_COLOR);
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

    /**
     * Сбрасывает поле к начальному состоянию.
     */
    public void reset() {
        field = new boolean[Constants.BOARD_SIZE][Constants.BOARD_SIZE];
        remainingShips.clear();
        for (int s : Constants.SHIP_SIZES) remainingShips.add(s);
        selectedShipSize = -1;
        horizontal = true;
        refreshBoard();
        rebuildPalette();
        btnReady.setEnabled(false);
        lblInfo.setText("Выберите корабль и разместите на поле");
        lblInfo.setForeground(Colors.TEXT_SECONDARY);
    }

    /**
     * Обновляет информационную строку.
     */
    private void updateInfo() {
        if (selectedShipSize > 0) {
            lblInfo.setText("Выбран " + selectedShipSize + "-палубный. Поворот: " + (horizontal ? "→" : "↓") + " (R)");
            lblInfo.setForeground(Colors.ACCENT_GOLD);
        } else {
            lblInfo.setText("Осталось разместить: " + remainingShips.size() + " кораблей");
            lblInfo.setForeground(Colors.TEXT_SECONDARY);
        }
    }

    /**
     * Обновляет отображение поля.
     */
    private void refreshBoard() {
        int[][] cells = new int[Constants.BOARD_SIZE][Constants.BOARD_SIZE];
        for (int i = 0; i < Constants.BOARD_SIZE; i++) {
            for (int j = 0; j < Constants.BOARD_SIZE; j++) {
                cells[i][j] = field[i][j] ? 1 : 0;
            }
        }
        board.setCells(cells);
    }

    /**
     * Перестраивает палитру кораблей.
     */
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

    /**
     * Создаёт элемент корабля в палитре.
     */
    private JPanel createShipItem(int size, int count) {
        JPanel item = new JPanel(new BorderLayout(8, 0));
        item.setOpaque(false);
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        item.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        // Визуализация корабля
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
                    GradientPaint gp = new GradientPaint(x, 0, Colors.SHIP_HIGHLIGHT, x, cellH, Colors.SHIP_BASE);
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

        JLabel countLabel = new JLabel("" + count);
        countLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        countLabel.setForeground(Colors.ACCENT_GOLD);

        item.add(shipVisual, BorderLayout.WEST);
        item.add(countLabel, BorderLayout.EAST);

        // Выбор корабля при клике
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

    /**
     * Проверяет, все ли корабли расставлены.
     */
    private void checkReady() {
        btnReady.setEnabled(remainingShips.isEmpty());
        if (remainingShips.isEmpty()) {
            lblInfo.setText("Все корабли на месте! Нажмите «Готов»");
            lblInfo.setForeground(Colors.SUCCESS);
        }
    }

    /**
     * Автоматически расставляет корабли.
     */
    private void autoPlace() {
        String gen = generateValidField();
        for (int i = 0; i < Constants.BOARD_SIZE; i++) {
            for (int j = 0; j < Constants.BOARD_SIZE; j++) {
                field[i][j] = (gen.charAt(i * Constants.BOARD_SIZE + j) == '1');
            }
        }
        remainingShips.clear();
        selectedShipSize = -1;
        refreshBoard();
        rebuildPalette();
        btnReady.setEnabled(true);
        lblInfo.setText("Поле сгенерировано автоматически");
        lblInfo.setForeground(Colors.ACCENT_TEAL);
    }

    /**
     * Отправляет поле на сервер.
     */
    private void submitField() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Constants.BOARD_SIZE; i++) {
            for (int j = 0; j < Constants.BOARD_SIZE; j++) {
                sb.append(field[i][j] ? '1' : '0');
            }
        }
        client.send("SET_FIELD:" + sb.toString());
    }

    /**
     * Проверяет возможность размещения корабля.
     */
    private boolean canPlace(boolean[][] f, int x, int y, int size, boolean horiz) {
        if (horiz) {
            if (y + size > Constants.BOARD_SIZE) return false;
            for (int i = -1; i <= size; i++) {
                for (int j = -1; j <= 1; j++) {
                    int nx = x + j, ny = y + i;
                    if (nx >= 0 && nx < Constants.BOARD_SIZE && ny >= 0 && ny < Constants.BOARD_SIZE && f[nx][ny])
                        return false;
                }
            }
        } else {
            if (x + size > Constants.BOARD_SIZE) return false;
            for (int i = -1; i <= size; i++) {
                for (int j = -1; j <= 1; j++) {
                    int nx = x + i, ny = y + j;
                    if (nx >= 0 && nx < Constants.BOARD_SIZE && ny >= 0 && ny < Constants.BOARD_SIZE && f[nx][ny])
                        return false;
                }
            }
        }
        return true;
    }

    /**
     * Размещает корабль на поле.
     */
    private void placeShip(boolean[][] f, int x, int y, int size, boolean horiz) {
        if (horiz) {
            for (int i = 0; i < size; i++) f[x][y + i] = true;
        } else {
            for (int i = 0; i < size; i++) f[x + i][y] = true;
        }
    }

    /**
     * Генерирует валидное поле случайным образом
     */
    private String generateValidField() {
        boolean[][] f = new boolean[Constants.BOARD_SIZE][Constants.BOARD_SIZE];
        Random rand = new Random();
        java.util.List<Integer> sizes = new ArrayList<>();
        for (int s : Constants.SHIP_SIZES) sizes.add(s);
        Collections.shuffle(sizes, rand);

        for (int size : sizes) {
            boolean placed = false;
            int attempts = 0;
            while (!placed && attempts < 5000) {
                attempts++;
                int x = rand.nextInt(Constants.BOARD_SIZE);
                int y = rand.nextInt(Constants.BOARD_SIZE);
                boolean horiz = rand.nextBoolean();
                if (canPlace(f, x, y, size, horiz)) {
                    placeShip(f, x, y, size, horiz);
                    placed = true;
                }
            }
            if (!placed) return generateValidField();
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Constants.BOARD_SIZE; i++) {
            for (int j = 0; j < Constants.BOARD_SIZE; j++) {
                sb.append(f[i][j] ? '1' : '0');
            }
        }
        return sb.toString();
    }


    public boolean[][] getField() {
        return field;
    }
}
