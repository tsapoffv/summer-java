package server;

import java.io.IOException;
import java.util.*;

/**
 * Класс, представляющий одну игру между двумя игроками.
 *
 * Отвечает за:
 * - Хранение состояния игры
 * - Определение текущего хода
 * - Обработку ходов
 * - Проверку попаданий и потопления кораблей
 * - Завершение игры
 */
public class Game {
    /** Первый игрок */
    private final ClientHandler p1;

    /** Второй игрок */
    private final ClientHandler p2;

    /** ID игрока, чей ход сейчас */
    public int currentTurn;

    /** Активна ли игра */
    public boolean active = true;

    /**
     * Конструктор игры.
     * @param a первый игрок
     * @param b второй игрок
     */
    public Game(ClientHandler a, ClientHandler b) {
        this.p1 = a;
        this.p2 = b;
        this.currentTurn = a.getId();
    }

    /**
     * Уведомляет игроков о том, чей ход.
     * @throws IOException при ошибке связи
     */
    public void notifyTurn() throws IOException {
        if (!active) return;
        if (currentTurn == p1.getId()) {
            p1.getOut().writeUTF("CMD_YOUR_TURN");
            p2.getOut().writeUTF("CMD_OPPONENT_TURN");
        } else {
            p2.getOut().writeUTF("CMD_YOUR_TURN");
            p1.getOut().writeUTF("CMD_OPPONENT_TURN");
        }
        p1.getOut().flush();
        p2.getOut().flush();
    }

    /**
     * Обрабатывает ход игрока.
     * @param mover игрок, совершающий ход
     * @param x координата X
     * @param y координата Y
     * @throws IOException при ошибке связи
     */
    public void makeMove(ClientHandler mover, int x, int y) throws IOException {
        if (!active) return;

        ClientHandler opp = (mover == p1) ? p2 : p1;
        boolean hit = opp.getMyField()[x][y];
        mover.getOpponentHits()[x][y] = true;

        String result;
        if (!hit) {
            result = "MISS";
        } else {
            boolean sunk = isShipSunk(opp.getMyField(), mover.getOpponentHits(), x, y);
            result = sunk ? "SUNK" : "HIT";
        }

        boolean win = checkWin(opp, mover.getOpponentHits());

        // Отправляем результат обоим игрокам
        mover.getOut().writeUTF("CMD_MOVE_RESULT:" + result + ":" + x + ":" + y);
        opp.getOut().writeUTF("CMD_OPPONENT_MOVE:" + result + ":" + x + ":" + y);
        mover.getOut().flush();
        opp.getOut().flush();

        if (win) {
            endGame(opp, false);
            return;
        }

        // Если попал - ходит ещё раз, иначе передаём ход
        if (hit) {
            mover.getOut().writeUTF("CMD_AGAIN");
            mover.getOut().flush();
        } else {
            currentTurn = opp.getId();
            notifyTurn();
        }
    }

    /**
     * Проверяет, потоплен ли корабль.
     * @param field поле противника
     * @param hits карта попаданий
     * @param x координата X попадания
     * @param y координата Y попадания
     * @return true если корабль потоплен
     */
    private boolean isShipSunk(boolean[][] field, boolean[][] hits, int x, int y) {
        boolean[][] visited = new boolean[Server.BOARD_SIZE][Server.BOARD_SIZE];
        List<int[]> shipCells = new ArrayList<>();
        findShipCells(field, visited, x, y, shipCells);

        for (int[] cell : shipCells) {
            if (!hits[cell[0]][cell[1]]) return false;
        }
        return true;
    }

    /**
     * Находит все клетки корабля рекурсивно.
     */
    private void findShipCells(boolean[][] field, boolean[][] visited, int x, int y, List<int[]> shipCells) {
        if (x < 0 || x >= Server.BOARD_SIZE || y < 0 || y >= Server.BOARD_SIZE) return;
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
     * Проверяет, не уничтожены ли все корабли противника.
     */
    private boolean checkWin(ClientHandler opp, boolean[][] hits) {
        for (int i = 0; i < Server.BOARD_SIZE; i++) {
            for (int j = 0; j < Server.BOARD_SIZE; j++) {
                if (opp.getMyField()[i][j] && !hits[i][j]) return false;
            }
        }
        return true;
    }

    /**
     * Завершает игру.
     * @param loser проигравший игрок
     * @param surrender была ли сдача
     * @throws IOException при ошибке связи
     */
    public void endGame(ClientHandler loser, boolean surrender) throws IOException {
        if (!active) return;
        active = false;

        ClientHandler winner = (loser == p1) ? p2 : p1;
        winner.getOut().writeUTF("CMD_GAME_OVER:WIN");
        loser.getOut().writeUTF("CMD_GAME_OVER:LOSE");
        winner.getOut().flush();
        loser.getOut().flush();

        p1.setCurrentGame(null);
        p2.setCurrentGame(null);
    }
}