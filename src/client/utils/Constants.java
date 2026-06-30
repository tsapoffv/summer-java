package client.utils;

/**
 * Централизованное хранение констант приложения.
 *
 * Содержит игровые константы, используемые как на клиенте,
 * так и на сервере.
 */
public final class Constants {
    private Constants() {} // Запрет создания экземпляров

    /** Размер игрового поля (10x10) */
    public static final int BOARD_SIZE = 10;

    /** Размер клетки в пикселях */
    public static final int CELL_SIZE = 40;

    /** Размеры кораблей для расстановки */
    public static final int[] SHIP_SIZES = {4, 3, 3, 2, 2, 2, 1, 1, 1, 1};
}
