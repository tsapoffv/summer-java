package client.utils;

import java.awt.Color;

/**
 * Централизованное хранение цветовой схемы приложения.
 *
 * Все цвета определены как статические финальные поля для
 * единообразного использования во всём приложении.
 */
public final class Colors {
    private Colors() {} // Запрет создания экземпляров

    // Фоновые цвета
    public static final Color BG_DARK = new Color(15, 23, 42);
    public static final Color BG_PANEL = new Color(30, 41, 59);
    public static final Color BORDER_COLOR = new Color(71, 85, 105);

    // Акцентные цвета
    public static final Color ACCENT_GOLD = new Color(244, 162, 97);
    public static final Color ACCENT_GOLD_HOVER = new Color(231, 111, 80);
    public static final Color ACCENT_TEAL = new Color(42, 157, 143);

    // Цвета воды
    public static final Color WATER_DEEP = new Color(14, 60, 90);
    public static final Color WATER_MID = new Color(27, 73, 101);

    // Цвета кораблей
    public static final Color SHIP_BASE = new Color(92, 103, 125);
    public static final Color SHIP_HIGHLIGHT = new Color(139, 155, 180);

    // Цвета результатов
    public static final Color HIT_RED = new Color(230, 57, 70);
    public static final Color HIT_ORANGE = new Color(255, 140, 0);
    public static final Color MISS_BLUE = new Color(168, 218, 220);
    public static final Color MISS_LIGHT = new Color(200, 235, 240);

    // Цвета текста
    public static final Color TEXT_PRIMARY = new Color(248, 250, 252);
    public static final Color TEXT_SECONDARY = new Color(148, 163, 184);

    // Статусные цвета
    public static final Color SUCCESS = new Color(34, 197, 94);
    public static final Color DANGER = new Color(239, 68, 68);
}