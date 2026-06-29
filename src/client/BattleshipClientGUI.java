package client;

import client.panels.*;
import client.utils.Constants;
import client.utils.Colors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

import server.ClientHandler;

import java.awt.*;
import java.io.*;
import java.net.*;

/**
 * Главный класс клиента морского боя.
 *
 * Отвечает за:
 * - Создание и отображение главного окна
 * - Управление панелями (CardLayout)
 * - Сетевое взаимодействие с сервером
 * - Маршрутизацию сообщений от сервера
 */
public class BattleshipClientGUI extends JFrame {
    /** Панели для разных экранов */
    private final CardLayout cardLayout;
    private final JPanel mainPanel;

    /** Сетевое соединение */
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private Thread receiverThread;
    private volatile boolean connected = false;

    /** Панели приложения */
    private final ConnectionPanel connectionPanel;
    private final SetupPanel setupPanel;
    private final LobbyPanel lobbyPanel;
    private final BattlePanel battlePanel;

    /**
     * Конструктор главного окна.
     * Инициализирует UI и подготавливает все панели.
     */
    public BattleshipClientGUI() {
        super("Морской бой");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        getContentPane().setBackground(Colors.BG_DARK);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        mainPanel.setOpaque(false);
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Создание панелей
        connectionPanel = new ConnectionPanel(this);
        setupPanel = new SetupPanel(this);
        lobbyPanel = new LobbyPanel(this);
        battlePanel = new BattlePanel(this);

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

    /**
     * Показывает указанную панель.
     * @param name имя панели (CONNECT, SETUP, LOBBY, BATTLE)
     */
    public void showPanel(String name) {
        cardLayout.show(mainPanel, name);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    /**
     * Подключается к серверу.
     * @param host адрес сервера
     * @param port порт сервера
     */
    public void connect(String host, int port) {
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

    /**
     * Отключается от сервера.
     */
    public void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        socket = null;
        in = null;
        out = null;
    }

    /**
     * Основной цикл приёма сообщений от сервера.
     */
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

    /**
     * Обрабатывает сообщение от сервера.
     * @param msg сообщение от сервера
     */
    private void handleMessage(String msg) {
        // --- Расстановка поля ---
        if (msg.equals("CMD_REQUIRE_FIELD")) {
            setupPanel.reset();
            showPanel("SETUP");
        }
        // --- Поле принято ---
        else if (msg.startsWith("CMD_OK")) {
            showPanel("LOBBY");
            lobbyPanel.refreshPlayers();
        }
        // --- Ошибка ---
        else if (msg.startsWith("CMD_ERROR:")) {
            // Игнорируем, так как ошибки выводятся в интерфейсе
        }
        // --- Список игроков ---
        else if (msg.startsWith("PLAYERS:")) {
            String list = msg.substring(8);
            lobbyPanel.updatePlayers(list);
        }
        // --- Начало игры ---
        else if (msg.startsWith("CMD_GAME_START:")) {
            String info = msg.substring(15);
            battlePanel.initGame(info);
            showPanel("BATTLE");
        }
        // --- Ходы ---
        else if (msg.equals("CMD_YOUR_TURN")) {
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
        }
        // --- Ещё ход (попадание) ---
        else if (msg.equals("CMD_AGAIN")) {
            battlePanel.showAgain();
        }
        // --- Конец игры ---
        else if (msg.startsWith("CMD_GAME_OVER:")) {
            battlePanel.endGame();
            showPanel("LOBBY");
            lobbyPanel.refreshPlayers();
        }
        // --- Неизвестная команда ---
        else {
            battlePanel.log("Сервер: " + msg);
        }
    }

    /**
     * Отправляет команду на сервер.
     * @param cmd команда для отправки
     */
    public void send(String cmd) {
        if (out == null) return;
        try {
            out.writeUTF(cmd);
            out.flush();
        } catch (IOException e) {
            // Ошибка отправки
        }
    }

    // --- Геттеры для доступа из панелей ---
    public ConnectionPanel getConnectionPanel() { return connectionPanel; }
    public SetupPanel getSetupPanel() { return setupPanel; }
    public LobbyPanel getLobbyPanel() { return lobbyPanel; }
    public BattlePanel getBattlePanel() { return battlePanel; }

    /**
     * Точка входа в клиентское приложение.
     */
    public static void main(String[] args) {
        // Настройка для Linux
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("linux")) {
            System.setProperty("java.awt.headless", "false");
            System.setProperty("awt.toolkit", "sun.awt.X11.XToolkit");
            System.setProperty("sun.java2d.uiScale", "1");
            System.setProperty("awt.useSystemAAFontSettings", "on");
            System.setProperty("swing.aatext", "true");
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