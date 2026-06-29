package client.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Кнопка с градиентной заливкой.
 *
 * Особенности:
 * - Плавный градиент от одного цвета к другому
 * - Эффект наведения (hover)
 * - Эффект нажатия (pressed)
 * - Закруглённые углы
 */
public class GradientButton extends JButton {
    private final Color color1;
    private final Color color2;
    private boolean hovered = false;
    private boolean pressed = false;

    /**
     * Конструктор кнопки.
     * @param text текст на кнопке
     * @param color1 начальный цвет градиента
     * @param color2 конечный цвет градиента
     */
    public GradientButton(String text, Color color1, Color color2) {
        super(text);
        this.color1 = color1;
        this.color2 = color2;
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setForeground(Color.WHITE);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Обработчики для эффектов
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

        // Выбор цветов с учётом состояния
        Color c1 = hovered ? color1.brighter() : color1;
        Color c2 = hovered ? color2.brighter() : color2;
        if (pressed) {
            c1 = c1.darker();
            c2 = c2.darker();
        }

        // Заливка градиентом
        GradientPaint gp = new GradientPaint(0, 0, c1, 0, h, c2);
        g2.setPaint(gp);
        g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);

        // Блик (светлая полоса сверху)
        g2.setColor(new Color(255, 255, 255, 30));
        g2.fillRoundRect(0, 0, w - 1, h / 2, 10, 10);

        // Обводка
        g2.setColor(new Color(255, 255, 255, 40));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

        g2.dispose();
        super.paintComponent(g);
    }
}