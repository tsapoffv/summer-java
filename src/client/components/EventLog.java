package client.components;

import client.utils.Colors;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Компонент для отображения лога событий в реальном времени.
 *
 * Особенности:
 * - Автопрокрутка к новым сообщениям
 * - Цветовое кодирование типов сообщений
 * - Поддержка системных, информационных, успешных, предупреждающих и опасных сообщений
 */
public class EventLog extends JPanel {
    /** Типы событий для цветового кодирования */
    public enum EventType { SYSTEM, SUCCESS, WARNING, DANGER, INFO }

    private final List<EventItem> events = new ArrayList<>();
    private final JPanel contentPanel;

    /**
     * Конструктор лога событий.
     */
    public EventLog() {
        setLayout(new BorderLayout());
        setOpaque(false);
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        add(contentPanel, BorderLayout.NORTH);
    }

    /**
     * Добавляет событие в лог.
     * @param message текст сообщения
     * @param type тип события
     */
    public void addEvent(String message, EventType type) {
        EventItem item = new EventItem(message, type);
        events.add(item);
        contentPanel.add(item);
        contentPanel.add(Box.createVerticalStrut(4));
        contentPanel.revalidate();

        // Прокрутка к новому сообщению
        SwingUtilities.invokeLater(() -> {
            Rectangle bounds = item.getBounds();
            scrollRectToVisible(bounds);
        });
    }

    /**
     * Очищает лог.
     */
    public void clear() {
        events.clear();
        contentPanel.removeAll();
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    /**
     * Элемент лога - одна строка события.
     */
    private static class EventItem extends JPanel {
        /**
         * Конструктор элемента лога.
         * @param message текст сообщения
         * @param type тип события
         */
        EventItem(String message, EventType type) {
            setLayout(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

            // Выбор цвета и префикса в зависимости от типа
            Color textColor;
            String prefix;
            switch (type) {
                case SUCCESS: textColor = Colors.SUCCESS; prefix = "✓ "; break;
                case WARNING: textColor = Colors.ACCENT_GOLD; prefix = "⚠ "; break;
                case DANGER: textColor = Colors.DANGER; prefix = "✗ "; break;
                case SYSTEM: textColor = Colors.TEXT_SECONDARY; prefix = "• "; break;
                default: textColor = Colors.TEXT_PRIMARY; prefix = "→ "; break;
            }

            JLabel label = new JLabel(prefix + message);
            label.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
            label.setForeground(textColor);
            add(label, BorderLayout.WEST);
        }
    }
}