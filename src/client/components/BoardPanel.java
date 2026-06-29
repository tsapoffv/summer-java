package client.components;

import client.utils.Colors;
import client.utils.Constants;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;

/**
 * Компонент для отображения игрового поля.
 *
 * Поддерживает:
 * - Режим расстановки (SETUP)
 * - Режим своего поля (MY_FIELD)
 * - Режим поля противника (ENEMY_FIELD)
 * - Анимации попаданий и промахов
 * - Подсветку при наведении в режиме расстановки
 */
public class BoardPanel extends JPanel {
    /** Режимы отображения поля */
    public enum Mode { SETUP, MY_FIELD, ENEMY_FIELD }

    private final int boardSize;
    private final int cellSize;
    private final Mode mode;
    private int[][] cells;

    // Данные для подсветки при расстановке
    private int highlightRow = -1, highlightCol = -1, highlightSize = 0;
    private boolean highlightHoriz = true, highlightValid = false;

    private final java.util.List<CellAnimation> animations = new ArrayList<>();
    private Timer animTimer;

    /**
     * @param size размер поля (количество клеток)
     * @param cellSize размер одной клетки в пикселях
     * @param mode режим отображения
     */
    public BoardPanel(int size, int cellSize, Mode mode) {
        this.boardSize = size;
        this.cellSize = cellSize;
        this.mode = mode;
        this.cells = new int[size][size];
        setPreferredSize(new Dimension(size * cellSize + 1, size * cellSize + 1));
        setOpaque(false);

        // Таймер для анимаций
        animTimer = new Timer(16, e -> {
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

    /**
     * Устанавливает состояние всех клеток поля.
     * @param c двумерный массив состояний
     */
    public void setCells(int[][] c) {
        for (int i = 0; i < boardSize; i++) {
            for (int j = 0; j < boardSize; j++) {
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

    /**
     * Устанавливает состояние одной клетки.
     */
    public void setCell(int row, int col, int value) {
        cells[row][col] = value;
        repaint();
    }

    /**
     * Устанавливает подсветку для предпросмотра размещения.
     */
    public void setHighlight(int row, int col, int size, boolean horiz, boolean valid) {
        highlightRow = row;
        highlightCol = col;
        highlightSize = size;
        highlightHoriz = horiz;
        highlightValid = valid;
        repaint();
    }

    /**
     * Очищает подсветку.
     */
    public void clearHighlight() {
        highlightRow = -1;
        highlightCol = -1;
        highlightSize = 0;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Отрисовка клеток
        for (int i = 0; i < boardSize; i++) {
            for (int j = 0; j < boardSize; j++) {
                int x = j * cellSize;
                int y = i * cellSize;
                paintCell(g2, x, y, cells[i][j], i, j);
            }
        }

        // Отрисовка анимаций поверх клеток
        for (CellAnimation anim : animations) {
            paintAnimation(g2, anim);
        }

        // Подсветка при расстановке
        if (mode == Mode.SETUP && highlightSize > 0 && highlightRow >= 0 && highlightCol >= 0) {
            drawPlacementHighlight(g2);
        }

        // Координатные метки
        drawCoordinates(g2);

        g2.dispose();
    }

    /**
     * Отрисовывает одну клетку поля.
     */
    private void paintCell(Graphics2D g2, int x, int y, int state, int row, int col) {
        int pad = 1;
        int w = cellSize - pad * 2;
        int h = cellSize - pad * 2;

        GradientPaint waterGrad = new GradientPaint(
                x, y, Colors.WATER_MID,
                x, y + h, Colors.WATER_DEEP
        );

        switch (state) {
            case 1: // Корабль
                if (mode != Mode.ENEMY_FIELD) {
                    GradientPaint shipGrad = new GradientPaint(
                            x, y, Colors.SHIP_HIGHLIGHT,
                            x, y + h, Colors.SHIP_BASE
                    );
                    g2.setPaint(shipGrad);
                    g2.fillRoundRect(x + pad, y + pad, w, h, 6, 6);
                    g2.setColor(new Color(70, 80, 100));
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRoundRect(x + pad, y + pad, w, h, 6, 6);
                    // Декоративная линия
                    g2.drawLine(x + pad + 4, y + pad + h/2, x + pad + w - 4, y + pad + h/2);
                } else {
                    g2.setPaint(waterGrad);
                    g2.fillRect(x + pad, y + pad, w, h);
                }
                break;

            case 2: // Попадание
                GradientPaint hitGrad = new GradientPaint(
                        x, y, Colors.HIT_RED,
                        x, y + h, new Color(180, 30, 50)
                );
                g2.setPaint(hitGrad);
                g2.fillRoundRect(x + pad, y + pad, w, h, 6, 6);

                // Взрывная звезда
                g2.setColor(Colors.HIT_ORANGE);
                g2.setStroke(new BasicStroke(2.5f));
                int cx = x + cellSize/2;
                int cy = y + cellSize/2;
                int r1 = cellSize/3;
                int r2 = cellSize/5;
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

            case 3: // Промах
                g2.setPaint(waterGrad);
                g2.fillRect(x + pad, y + pad, w, h);

                g2.setColor(Colors.MISS_BLUE);
                g2.setStroke(new BasicStroke(2f));
                int rippleR = cellSize/4;
                g2.drawOval(x + cellSize/2 - rippleR, y + cellSize/2 - rippleR, rippleR*2, rippleR*2);
                g2.setColor(Colors.MISS_LIGHT);
                g2.fillOval(x + cellSize/2 - 3, y + cellSize/2 - 3, 6, 6);
                break;

            default: // Вода
                g2.setPaint(waterGrad);
                g2.fillRect(x + pad, y + pad, w, h);

                // Случайные волны
                if ((row * 7 + col * 13) % 23 == 0) {
                    g2.setColor(new Color(100, 160, 200, 40));
                    int br = 2 + ((row + col) % 3);
                    g2.fillOval(x + cellSize/2 - br, y + cellSize/2 - br, br*2, br*2);
                }
                break;
        }

        // Сетка
        g2.setColor(new Color(60, 100, 130, 80));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRect(x, y, cellSize, cellSize);
    }

    /**
     * Отрисовывает подсветку при расстановке.
     */
    private void drawPlacementHighlight(Graphics2D g2) {
        Color highlightColor = highlightValid
                ? new Color(42, 157, 143, 120)
                : new Color(239, 68, 68, 120);
        for (int k = 0; k < highlightSize; k++) {
            int r = highlightRow + (highlightHoriz ? 0 : k);
            int c = highlightCol + (highlightHoriz ? k : 0);
            if (r >= 0 && r < boardSize && c >= 0 && c < boardSize) {
                g2.setColor(highlightColor);
                g2.fillRoundRect(c * cellSize + 2, r * cellSize + 2,
                        cellSize - 3, cellSize - 3, 6, 6);
            }
        }
    }

    /**
     * Отрисовывает координатные метки.
     */
    private void drawCoordinates(Graphics2D g2) {
        g2.setColor(Colors.TEXT_SECONDARY);
        g2.setFont(new Font("JetBrains Mono", Font.BOLD, 11));
        for (int i = 0; i < boardSize; i++) {
            g2.drawString(String.valueOf(i), 4, i * cellSize + cellSize/2 + 5);
            g2.drawString(String.valueOf(i), i * cellSize + cellSize/2 - 4, boardSize * cellSize + 16);
        }
    }

    /**
     * Отрисовывает анимацию на клетке.
     */
    private void paintAnimation(Graphics2D g2, CellAnimation anim) {
        int x = anim.col * cellSize;
        int y = anim.row * cellSize;
        int cx = x + cellSize/2;
        int cy = y + cellSize/2;

        float alpha = 1f - anim.progress;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        if (anim.type == AnimationType.EXPLOSION) {
            drawExplosionAnimation(g2, cx, cy, alpha, anim);
        } else if (anim.type == AnimationType.SPLASH) {
            drawSplashAnimation(g2, cx, cy, alpha);
        }

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    /**
     * Отрисовывает анимацию взрыва (попадание).
     */
    private void drawExplosionAnimation(Graphics2D g2, int cx, int cy, float alpha, CellAnimation anim) {
        int maxR = cellSize;
        int r = (int)((1f - alpha) * maxR);

        // Внешнее кольцо
        g2.setColor(new Color(255, 200, 50, (int)(200 * alpha)));
        g2.setStroke(new BasicStroke(3f * alpha));
        g2.drawOval(cx - r, cy - r, r*2, r*2);

        // Внутреннее кольцо
        int r2 = (int)((1f - alpha) * maxR * 0.6f);
        g2.setColor(new Color(255, 100, 50, (int)(150 * alpha)));
        g2.drawOval(cx - r2, cy - r2, r2*2, r2*2);

        // Искры - используем anim.row и anim.col для генерации случайных чисел
        g2.setColor(new Color(255, 255, 200, (int)(255 * alpha)));
        Random rand = new Random(anim.row * 31 + anim.col * 17);
        for (int p = 0; p < 6; p++) {
            double angle = rand.nextDouble() * Math.PI * 2;
            int dist = (int)((1f - alpha) * cellSize * 0.8);
            int px = cx + (int)(dist * Math.cos(angle));
            int py = cy + (int)(dist * Math.sin(angle));
            int ps = (int)(4 * alpha);
            g2.fillOval(px - ps/2, py - ps/2, ps, ps);
        }
    }

    /**
     * Отрисовывает анимацию брызг (промах).
     */
    private void drawSplashAnimation(Graphics2D g2, int cx, int cy, float alpha) {
        int maxR = cellSize;
        int r = (int)((1f - alpha) * maxR);

        g2.setColor(new Color(168, 218, 220, (int)(150 * alpha)));
        g2.setStroke(new BasicStroke(2f * alpha));
        g2.drawOval(cx - r, cy - r, r*2, r*2);

        int r2 = (int)((1f - alpha) * maxR * 0.5f);
        g2.setColor(new Color(200, 235, 240, (int)(100 * alpha)));
        g2.drawOval(cx - r2, cy - r2, r2*2, r2*2);
    }

    /**
     * Типы анимаций.
     */
    private enum AnimationType { EXPLOSION, SPLASH }

    /**
     * Класс для хранения данных анимации.
     */
    private class CellAnimation {
        int row, col;
        AnimationType type;
        float progress = 0;
        boolean finished = false;

        CellAnimation(int row, int col, AnimationType type) {
            this.row = row;
            this.col = col;
            this.type = type;
        }
    }
}