package client.panels;

import client.BattleshipClientGUI;
import client.components.BoardPanel;
import client.components.EventLog;
import client.components.GradientButton;
import client.utils.Colors;
import client.utils.Constants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;

/**
 * Панель боя.
 *
 * Отвечает за:
 * - Отображение двух игровых полей
 * - Обработку ходов игрока
 * - Отображение статуса игры
 * - Ведение лога событий
 * - Кнопку сдачи
 */
public class BattlePanel extends JPanel {
    private final BattleshipClientGUI client;
    private BoardPanel myBoard;
    private BoardPanel enemyBoard;
    private JLabel lblStatus;
    private JLabel lblStatusIcon;
    private EventLog log;
    private GradientButton btnSurrender;
    private JLabel lblShipsRemaining;
    private JPanel statusBanner;

    private boolean inGame = false;
    private boolean myTurn = false;

    /** Состояние клеток: 0-пусто, 1-корабль, 2-попадание, 3-промах */
    private final int[][] myCells = new int[Constants.BOARD_SIZE][Constants.BOARD_SIZE];
    private final int[][] enemyCells = new int[Constants.BOARD_SIZE][Constants.BOARD_SIZE];

    private int myShipsSunk = 0;
    private int enemyShipsSunk = 0;

    /**
     * Конструктор панели боя.
     * @param client ссылка на главное окно клиента
     */
    public BattlePanel(BattleshipClientGUI client) {
        this.client = client;
        setOpaque(false);
        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(15, 15, 15, 15));

        // Инициализация компонентов
        initComponents();

        // Сборка UI
        buildUI();

        // Обработчики для поля противника
        setupEnemyBoardHandlers();
    }

    /**
     * Инициализирует все компоненты.
     */
    private void initComponents() {
        myBoard = new BoardPanel(Constants.BOARD_SIZE, Constants.CELL_SIZE, BoardPanel.Mode.MY_FIELD);
        enemyBoard = new BoardPanel(Constants.BOARD_SIZE, Constants.CELL_SIZE, BoardPanel.Mode.ENEMY_FIELD);
        log = new EventLog();
        btnSurrender = new GradientButton("🏳 Сдаться", Colors.DANGER, new Color(185, 28, 28));

        lblStatus = new JLabel("Ожидание начала боя...", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblStatus.setForeground(Colors.TEXT_SECONDARY);

        lblStatusIcon = new JLabel("⚔", SwingConstants.CENTER);
        lblStatusIcon.setFont(new Font("Segoe UI", Font.PLAIN, 24));

        lblShipsRemaining = new JLabel("Ваши корабли: 10  |  Корабли врага: 10", SwingConstants.CENTER);
        lblShipsRemaining.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblShipsRemaining.setForeground(Colors.TEXT_SECONDARY);

        statusBanner = createStatusBanner();
    }

    /**
     * Собирает UI из компонентов.
     */
    private void buildUI() {
        // Верхний баннер со статусом
        add(statusBanner, BorderLayout.NORTH);

        // Игровые поля
        JPanel boards = createBoardsPanel();
        add(boards, BorderLayout.CENTER);

        // Нижняя панель с логом и кнопками
        JPanel bottom = createBottomPanel();
        add(bottom, BorderLayout.SOUTH);
    }

    /**
     * Создаёт баннер статуса.
     */
    private JPanel createStatusBanner() {
        JPanel banner = new JPanel(new BorderLayout()) {
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
        banner.setOpaque(false);
        banner.setBorder(new EmptyBorder(12, 20, 12, 20));

        JPanel statusText = new JPanel(new GridLayout(2, 1, 0, 4));
        statusText.setOpaque(false);
        statusText.add(lblStatus);
        statusText.add(lblShipsRemaining);

        banner.add(lblStatusIcon, BorderLayout.WEST);
        banner.add(statusText, BorderLayout.CENTER);

        return banner;
    }

    /**
     * Создаёт панель с двумя игровыми полями.
     */
    private JPanel createBoardsPanel() {
        JPanel boards = new JPanel(new GridLayout(1, 2, 20, 0));
        boards.setOpaque(false);

        boards.add(createBoardWrapper("🛡 Ваше поле", myBoard));
        boards.add(createBoardWrapper("🎯 Поле противника", enemyBoard));

        return boards;
    }

    /**
     * Создаёт нижнюю панель с логом и кнопками.
     */
    private JPanel createBottomPanel() {
        JPanel bottom = new JPanel(new BorderLayout(10, 10));
        bottom.setOpaque(false);

        // Лог событий
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setOpaque(false);
        logScroll.getViewport().setOpaque(false);
        logScroll.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(12, Colors.BORDER_COLOR, 1.5f),
                new EmptyBorder(8, 8, 8, 8)
        ));
        logScroll.setPreferredSize(new Dimension(0, 140));
        bottom.add(logScroll, BorderLayout.CENTER);

        // Кнопка сдачи
        btnSurrender.setPreferredSize(new Dimension(140, 40));
        btnSurrender.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSurrender.addActionListener(e -> {
            client.send("SURRENDER");
            inGame = false;
            myTurn = false;
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(btnSurrender);
        bottom.add(btnPanel, BorderLayout.SOUTH);

        return bottom;
    }

    /**
     * Настраивает обработчики мыши для поля противника.
     */
    private void setupEnemyBoardHandlers() {
        // Обработка кликов для стрельбы
        enemyBoard.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!inGame || !myTurn) return;
                int row = e.getY() / Constants.CELL_SIZE;
                int col = e.getX() / Constants.CELL_SIZE;
                if (row < 0 || row >= Constants.BOARD_SIZE || col < 0 || col >= Constants.BOARD_SIZE) return;
                if (enemyCells[row][col] != 0) {
                    log.addEvent("Уже стреляли сюда!", EventLog.EventType.WARNING);
                    return;
                }
                client.send("MOVE:" + row + ":" + col);
            }
        });

        // Изменение курсора при наведении
        enemyBoard.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (!inGame || !myTurn) {
                    enemyBoard.setCursor(Cursor.getDefaultCursor());
                    return;
                }
                int row = e.getY() / Constants.CELL_SIZE;
                int col = e.getX() / Constants.CELL_SIZE;
                if (row >= 0 && row < Constants.BOARD_SIZE && col >= 0 && col < Constants.BOARD_SIZE
                        && enemyCells[row][col] == 0) {
                    enemyBoard.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                } else {
                    enemyBoard.setCursor(Cursor.getDefaultCursor());
                }
            }
        });
    }

    /**
     * Создаёт обёртку для поля с заголовком.
     */
    private JPanel createBoardWrapper(String title, BoardPanel board) {
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
        wrapper.add(board, BorderLayout.CENTER);
        return wrapper;
    }

    /**
     * Инициализирует игру.
     * @param info информация о начале игры
     */
    public void initGame(String info) {
        inGame = true;
        myTurn = false;
        myShipsSunk = 0;
        enemyShipsSunk = 0;

        lblStatus.setText("Бой начался! " + info);
        lblStatus.setForeground(Colors.TEXT_PRIMARY);
        lblStatusIcon.setText("⚔");
        updateShipCount();

        log.clear();
        log.addEvent("=== Бой начался ===", EventLog.EventType.SYSTEM);
        log.addEvent(info, EventLog.EventType.SYSTEM);

        // Очищаем клетки
        for (int i = 0; i < Constants.BOARD_SIZE; i++) {
            Arrays.fill(myCells[i], 0);
            Arrays.fill(enemyCells[i], 0);
        }

        // Копируем поле игрока из панели расстановки
        boolean[][] myField = client.getSetupPanel().getField();
        for (int i = 0; i < Constants.BOARD_SIZE; i++) {
            for (int j = 0; j < Constants.BOARD_SIZE; j++) {
                myCells[i][j] = myField[i][j] ? 1 : 0;
            }
        }

        myBoard.setCells(copyCells(myCells));
        enemyBoard.setCells(copyCells(enemyCells));
    }

    /**
     * Устанавливает, чей сейчас ход.
     * @param turn true если ход игрока
     */
    public void setMyTurn(boolean turn) {
        myTurn = turn;
        if (turn) {
            lblStatus.setText("ВАШ ХОД — выберите клетку на поле противника");
            lblStatus.setForeground(Colors.SUCCESS);
            lblStatusIcon.setText("🎯");
            log.addEvent("Ваш ход!", EventLog.EventType.SUCCESS);
        } else {
            lblStatus.setText("Ход противника...");
            lblStatus.setForeground(Colors.TEXT_SECONDARY);
            lblStatusIcon.setText("⏳");
        }
    }

    /**
     * Применяет результат хода игрока.
     */
    public void applyMyMove(int x, int y, String result) {
        switch (result) {
            case "HIT":
                enemyCells[x][y] = 2;
                log.addEvent("Выстрел (" + x + "," + y + "): Попадание!", EventLog.EventType.SUCCESS);
                break;
            case "MISS":
                enemyCells[x][y] = 3;
                log.addEvent("Выстрел (" + x + "," + y + "): Мимо", EventLog.EventType.INFO);
                break;
            case "SUNK":
                enemyCells[x][y] = 2;
                enemyShipsSunk++;
                updateShipCount();
                log.addEvent("Выстрел (" + x + "," + y + "): КОРАБЛЬ УНИЧТОЖЕН!", EventLog.EventType.SUCCESS);
                break;
            case "WIN":
                enemyCells[x][y] = 2;
                log.addEvent("Выстрел (" + x + "," + y + "): ПОБЕДА!", EventLog.EventType.SUCCESS);
                break;
        }
        enemyBoard.setCells(copyCells(enemyCells));
    }

    /**
     * Применяет результат хода противника.
     */
    public void applyOpponentMove(int x, int y, String result) {
        switch (result) {
            case "HIT":
                myCells[x][y] = 2;
                log.addEvent("Противник (" + x + "," + y + "): Попадание по вашему кораблю!", EventLog.EventType.WARNING);
                break;
            case "MISS":
                myCells[x][y] = 3;
                log.addEvent("Противник (" + x + "," + y + "): Мимо", EventLog.EventType.INFO);
                break;
            case "SUNK":
                myCells[x][y] = 2;
                myShipsSunk++;
                updateShipCount();
                log.addEvent("Противник (" + x + "," + y + "): Ваш корабль УНИЧТОЖЕН!", EventLog.EventType.DANGER);
                break;
            case "WIN":
                myCells[x][y] = 2;
                log.addEvent("Противник (" + x + "," + y + "): Потопил последний корабль", EventLog.EventType.DANGER);
                break;
        }
        myBoard.setCells(copyCells(myCells));
    }

    /**
     * Показывает, что игрок может ходить ещё раз (попадание).
     */
    public void showAgain() {
        log.addEvent("Попадание! Ходите ещё", EventLog.EventType.SUCCESS);
        lblStatus.setText("ВАШ ХОД (попадание!) — стреляйте ещё");
        lblStatus.setForeground(new Color(250, 204, 21));
    }

    /**
     * Завершает игру.
     */
    public void endGame() {
        inGame = false;
        myTurn = false;
    }

    /**
     * Добавляет сообщение в лог.
     */
    public void log(String msg) {
        log.addEvent(msg, EventLog.EventType.INFO);
    }

    /**
     * Обновляет счётчик кораблей.
     */
    private void updateShipCount() {
        lblShipsRemaining.setText("Ваши корабли: " + (10 - myShipsSunk) +
                "  |  Корабли врага: " + (10 - enemyShipsSunk));
    }

    /**
     * Копирует двумерный массив.
     */
    private int[][] copyCells(int[][] src) {
        int[][] dst = new int[src.length][];
        for (int i = 0; i < src.length; i++) {
            dst[i] = src[i].clone();
        }
        return dst;
    }

    /**
     * Простая реализация закругленной рамки.
     */
    private static class RoundedBorder extends javax.swing.border.AbstractBorder {
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
            return new Insets(radius / 2, radius / 2, radius / 2, radius / 2);
        }
    }
}