package client.panels;

import client.BattleshipClientGUI;
import client.components.GradientButton;
import client.models.PlayerItem;
import client.utils.Colors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Панель лобби.
 *
 * Позволяет:
 * - Просматривать список доступных игроков
 * - Обновлять список
 * - Вызывать игрока на бой
 */
public class LobbyPanel extends JPanel {
    private final BattleshipClientGUI client;
    private final DefaultListModel<PlayerItem> listModel = new DefaultListModel<>();
    private final JList<PlayerItem> playerList;
    private final GradientButton btnRefresh;
    private final GradientButton btnChallenge;
    private final JLabel lblStatus;

    /**
     * Конструктор панели лобби.
     * @param client ссылка на главное окно клиента
     */
    public LobbyPanel(BattleshipClientGUI client) {
        this.client = client;
        setOpaque(false);
        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(15, 15, 15, 15));

        // Заголовок
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Лобби", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(Colors.ACCENT_GOLD);
        header.add(title, BorderLayout.NORTH);

        JLabel subtitle = new JLabel("Выберите противника для боя", SwingConstants.CENTER);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitle.setForeground(Colors.TEXT_SECONDARY);
        header.add(subtitle, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // Список игроков
        playerList = new JList<>(listModel);
        playerList.setOpaque(false);
        playerList.setCellRenderer(new PlayerCellRenderer());
        playerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playerList.setBorder(null);

        JScrollPane sp = new JScrollPane(playerList);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(12, Colors.BORDER_COLOR, 1.5f),
                new EmptyBorder(8, 8, 8, 8)
        ));
        sp.setPreferredSize(new Dimension(350, 280));

        JPanel listWrapper = new JPanel(new BorderLayout());
        listWrapper.setOpaque(false);
        listWrapper.add(sp, BorderLayout.CENTER);
        add(listWrapper, BorderLayout.CENTER);

        // Нижняя панель
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);

        lblStatus = new JLabel(" ", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatus.setForeground(Colors.TEXT_SECONDARY);
        bottom.add(lblStatus, BorderLayout.NORTH);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnPanel.setOpaque(false);
        btnPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        btnRefresh = new GradientButton(" Обновить", new Color(59, 130, 246), new Color(37, 99, 235));
        btnChallenge = new GradientButton(" Вызвать на бой", Colors.ACCENT_GOLD, Colors.ACCENT_GOLD_HOVER);

        for (GradientButton btn : new GradientButton[]{btnRefresh, btnChallenge}) {
            btn.setPreferredSize(new Dimension(160, 42));
            btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        }

        btnPanel.add(btnRefresh);
        btnPanel.add(btnChallenge);
        bottom.add(btnPanel, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        // Обработчики кнопок
        btnRefresh.addActionListener(e -> refreshPlayers());
        btnChallenge.addActionListener(e -> {
            PlayerItem sel = playerList.getSelectedValue();
            if (sel == null) {
                lblStatus.setText("Выберите противника из списка");
                lblStatus.setForeground(Colors.DANGER);
                return;
            }
            client.send("SELECT_PLAYER:" + sel.id);
            lblStatus.setText("Вызов отправлен...");
            lblStatus.setForeground(Colors.ACCENT_GOLD);
        });
    }

    /**
     * Запрашивает обновление списка игроков.
     */
    public void refreshPlayers() {
        listModel.clear();
        client.send("GET_PLAYERS");
        lblStatus.setText("Список обновлён");
        lblStatus.setForeground(Colors.TEXT_SECONDARY);
    }

    /**
     * Обновляет список игроков данными от сервера.
     * @param data строка с ID игроков через запятую
     */
    public void updatePlayers(String data) {
        listModel.clear();
        if (data.isEmpty()) {
            lblStatus.setText("Нет доступных игроков");
            lblStatus.setForeground(Colors.TEXT_SECONDARY);
            return;
        }
        for (String idStr : data.split(",")) {
            try {
                int id = Integer.parseInt(idStr.trim());
                listModel.addElement(new PlayerItem(id, "Игрок " + id));
            } catch (NumberFormatException ignored) {}
        }
        lblStatus.setText("Доступно игроков: " + listModel.size());
        lblStatus.setForeground(Colors.TEXT_SECONDARY);
    }

    /**
     * Ячейка списка игроков.
     */
    private static class PlayerCellRenderer extends JPanel implements ListCellRenderer<PlayerItem> {
        private final JLabel iconLabel = new JLabel("");
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
            nameLabel.setForeground(Colors.TEXT_PRIMARY);
            idLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            idLabel.setForeground(Colors.TEXT_SECONDARY);
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
                g2.setColor(Colors.ACCENT_GOLD);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(4, 2, getWidth() - 8, getHeight() - 4, 10, 10);
            }
            g2.dispose();
            super.paintComponent(g);
        }
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
            return new Insets(radius/2, radius/2, radius/2, radius/2);
        }
    }
}
