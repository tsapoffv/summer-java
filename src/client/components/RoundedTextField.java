package client.components;

import client.utils.Colors;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Текстовое поле с закруглёнными углами.
 *
 * Особенности:
 * - Закруглённые углы
 * - Подсветка рамки при фокусе
 * - Прозрачный фон
 */
public class RoundedTextField extends JTextField {

    /**
     * Конструктор текстового поля.
     * @param text начальный текст
     * @param columns количество колонок
     */
    public RoundedTextField(String text, int columns) {
        super(text, columns);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        setBackground(Colors.BG_PANEL);
        setForeground(Colors.TEXT_PRIMARY);
        setCaretColor(Colors.TEXT_PRIMARY);
        setFont(new Font("Segoe UI", Font.PLAIN, 14));

        // Перерисовка при изменении фокуса
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

        // Заливка фона
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

        // Рамка
        boolean focused = hasFocus();
        g2.setColor(focused ? Colors.ACCENT_GOLD : Colors.BORDER_COLOR);
        g2.setStroke(new BasicStroke(focused ? 2f : 1.5f));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

        g2.dispose();
        super.paintComponent(g);
    }
}