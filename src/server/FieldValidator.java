package server;

import java.util.*;

/**
 * Класс для валидации игрового поля.
 * Проверяет:
 * - Правильное количество кораблей каждого размера
 * - Отсутствие касаний между кораблями
 * - Прямолинейность кораблей
 * - Корабли не выходят за границы поля
 */
public class FieldValidator {

    /** Размеры кораблей для проверки */
    private static final int[] SHIP_SIZES = {4, 3, 3, 2, 2, 2, 1, 1, 1, 1};

    /**
     * Проверяет валидность игрового поля.
     * @param field двумерный массив true/false
     * @return true если поле корректно
     */
    public static boolean validate(boolean[][] field) {
        int size = field.length;
        int[] counts = new int[5];
        boolean[][] visited = new boolean[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (field[i][j] && !visited[i][j]) {
                    List<int[]> shipCells = new ArrayList<>();
                    findShipCells(field, visited, i, j, shipCells);

                    int shipSize = shipCells.size();
                    if (shipSize == 0 || shipSize > 4) return false;
                    counts[shipSize]++;

                    // Проверка прямолинейности
                    if (!isShipStraight(shipCells)) return false;

                    // Проверка отсутствия касаний с другими кораблями
                    if (!hasNoTouching(field, shipCells)) return false;
                }
            }
        }

        // Проверка количества кораблей каждого размера
        int[] expected = new int[5];
        for (int s : SHIP_SIZES) expected[s]++;

        for (int s = 1; s <= 4; s++) {
            if (counts[s] != expected[s]) return false;
        }
        return true;
    }

    /**
     * Находит все клетки корабля рекурсивно.
     */
    private static void findShipCells(boolean[][] field, boolean[][] visited, int x, int y, List<int[]> shipCells) {
        int size = field.length;
        if (x < 0 || x >= size || y < 0 || y >= size) return;
        if (visited[x][y]) return;
        if (!field[x][y]) return;

        visited[x][y] = true;
        shipCells.add(new int[]{x, y});

        findShipCells(field, visited, x - 1, y, shipCells);
        findShipCells(field, visited, x + 1, y, shipCells);
        findShipCells(field, visited, x, y - 1, shipCells);
        findShipCells(field, visited, x, y + 1, shipCells);
    }

    /**
     * Проверяет, что корабль расположен по прямой линии.
     */
    private static boolean isShipStraight(List<int[]> cells) {
        if (cells.isEmpty()) return false;
        int firstX = cells.get(0)[0];
        int firstY = cells.get(0)[1];
        boolean sameRow = true, sameCol = true;
        for (int[] c : cells) {
            if (c[0] != firstX) sameRow = false;
            if (c[1] != firstY) sameCol = false;
            if (!sameRow && !sameCol) return false;
        }
        return true;
    }

    /**
     * Проверяет, что корабль не касается других кораблей.
     */
    private static boolean hasNoTouching(boolean[][] field, List<int[]> shipCells) {
        int size = field.length;
        for (int[] cell : shipCells) {
            int x = cell[0], y = cell[1];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = x + dx, ny = y + dy;
                    if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
                        if (field[nx][ny]) {
                            boolean same = false;
                            for (int[] sc : shipCells) {
                                if (sc[0] == nx && sc[1] == ny) { same = true; break; }
                            }
                            if (!same) return false;
                        }
                    }
                }
            }
        }
        return true;
    }
}