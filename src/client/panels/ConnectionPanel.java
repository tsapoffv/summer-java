package client.panels;

import client.BattleshipClientGUI;
import client.components.GradientButton;
import client.components.RoundedTextField;
import client.utils.Colors;

import javax.swing.*;
import java.awt.*;

/**
 * Панель подключения к серверу.
 *
 * Позволяет пользователю:
 * - Ввести адрес и порт сервера
 * - Подключиться к серверу
 * - Видеть статус подключения
 */
public class ConnectionPanel extends JPanel {
    private final BattleshipClientGUI client;
    private final JTextField txtHost;
    private final JTextField txtPort;
    private final JLabel lblStatus;
    private final GradientButton btnConnect;
    private final JLabel statusIndicator;

    /**
     * Конструктор панели подключения.
     * @param client ссылка на главное окно клиента
     */
    public ConnectionPanel(BattleshipClientGUI client) {
        this.client = client;
        setOpaque(false);
        setLayout(new GridBagLayout());

        // Заголовок
        JLabel title = new JLabel(" МОРСКОЙ БОЙ", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 32));
        title.setForeground(Colors.ACCENT_GOLD);

        JLabel subtitle = new JLabel("Подключитесь к серверу для начала игры", SwingConstants.CENTER);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitle.setForeground(Colors.TEXT_SECONDARY);

        // Поля ввода
        txtHost = new RoundedTextField("127.0.0.1", 12);
        txtPort = new RoundedTextField("12345", 6);

        // Статус
        lblStatus = new JLabel("Не подключено", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblStatus.setForeground(Colors.DANGER);

        statusIndicator = new JLabel("");
        statusIndicator.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusIndicator.setForeground(Colors.DANGER);

        // Кнопка подключения
        btnConnect = new GradientButton("Подключиться", Colors.ACCENT_GOLD, Colors.ACCENT_GOLD_HOVER);
        btnConnect.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btnConnect.setPreferredSize(new Dimension(200, 44));

        // Размещение компонентов
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
        lblHost.setForeground(Colors.TEXT_PRIMARY);
        lblHost.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        add(lblHost, gbc);
        gbc.gridx = 1;
        add(txtHost, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        JLabel lblPort = new JLabel("Порт:");
        lblPort.setForeground(Colors.TEXT_PRIMARY);
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

        // Обработчик кнопки подключения
        btnConnect.addActionListener(e -> {
            btnConnect.setEnabled(false);
            lblStatus.setText("Подключение...");
            statusIndicator.setForeground(Colors.ACCENT_GOLD);
            client.connect(txtHost.getText().trim(), Integer.parseInt(txtPort.getText().trim()));
        });
    }

    /**
     * Устанавливает статус подключения.
     * @param s текст статуса
     * @param connecting true если процесс подключения активен
     */
    public void setStatus(String s, boolean connecting) {
        lblStatus.setText(s);
        statusIndicator.setForeground(connecting ? Colors.ACCENT_GOLD : Colors.DANGER);
        lblStatus.setForeground(connecting ? Colors.ACCENT_GOLD : Colors.DANGER);
    }

    /**
     * Включает/отключает кнопку подключения.
     * @param b true если кнопка активна
     */
    public void setConnectEnabled(boolean b) { btnConnect.setEnabled(b); }
}
