package server;

import java.io.*;
import java.net.*;

/**
 * Обработчик подключения одного клиента.
 *
 * Отвечает за:
 * - Приём и отправку сообщений клиенту
 * - Обработку команд от клиента
 * - Управление состоянием клиента (поле, готовая, игра)
 *
 * Работает в отдельном потоке для каждого клиента.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final int id;
    private final Server server;

    /** Поле клиента (расстановка кораблей) */
    private boolean[][] myField;

    /** Карта попаданий по полю противника */
    private boolean[][] opponentHits;

    /** Текущая игра клиента */
    public Game currentGame;

    /** Готов ли клиент к игре (поле расставлено) */
    public boolean ready = false;

    /**
     * Конструктор обработчика клиента.
     * @param socket сокет соединения
     * @param id уникальный ID клиента
     * @param server ссылка на сервер
     * @throws IOException при ошибках ввода/вывода
     */
    public ClientHandler(Socket socket, int id, Server server) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        this.id = id;
        this.server = server;
    }

    public int getId() { return id; }
    public DataOutputStream getOut() { return out; }
    public boolean[][] getMyField() { return myField; }
    public boolean[][] getOpponentHits() { return opponentHits; }
    public void setCurrentGame(Game game) { currentGame = game; }

    /**
     * Основной метод обработки клиента.
     * Выполняется в отдельном потоке.
     */
    @Override
    public void run() {
        try {
            // ---- Этап 1: Получение поля от клиента ----
            receiveField();

            // ---- Этап 2: Основной цикл обработки команд ----
            processCommands();

        } catch (EOFException e) {
            // Нормальное отключение клиента
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Очистка ресурсов при отключении
            cleanup();
        }
    }

    /**
     * Получает поле от клиента с валидацией.
     * @throws IOException при ошибке связи
     */
    private void receiveField() throws IOException {
        while (true) {
            out.writeUTF("CMD_REQUIRE_FIELD");
            out.flush();
            System.out.println("[SERVER] CMD_REQUIRE_FIELD -> client " + id);

            String line;
            try {
                line = in.readUTF();
            } catch (EOFException e) {
                throw e;
            }
            System.out.println("[SERVER] from client " + id + ": " +
                    (line.length() > 60 ? line.substring(0,60)+"..." : line));

            if (!line.startsWith("SET_FIELD:")) {
                out.writeUTF("CMD_ERROR:Expected SET_FIELD");
                out.flush();
                continue;
            }

            String fieldData = line.substring(10);
            if (!parseAndSetField(fieldData)) {
                out.writeUTF("CMD_ERROR:Invalid field layout");
                out.flush();
                continue;
            }

            ready = true;
            out.writeUTF("CMD_OK:Field accepted");
            out.flush();
            System.out.println("[SERVER] CMD_OK -> client " + id);
            break;
        }
    }

    /**
     * Обрабатывает команды от клиента.
     * @throws IOException при ошибке связи
     */
    private void processCommands() throws IOException {
        while (true) {
            String cmd = in.readUTF();
            System.out.println("[SERVER] cmd from " + id + ": " + cmd);

            if (cmd.equals("GET_PLAYERS")) {
                sendPlayerList();
            } else if (cmd.startsWith("SELECT_PLAYER:")) {
                int target = Integer.parseInt(cmd.substring(14));
                selectOpponent(target);
            } else if (cmd.startsWith("MOVE:")) {
                String[] p = cmd.split(":");
                int x = Integer.parseInt(p[1]);
                int y = Integer.parseInt(p[2]);
                makeMove(x, y);
            } else if (cmd.equals("SURRENDER")) {
                surrender();
            } else {
                out.writeUTF("CMD_ERROR:Unknown command");
                out.flush();
            }
        }
    }

    /**
     * Очищает ресурсы при отключении клиента.
     */
    private void cleanup() {
        try { socket.close(); } catch (IOException e) {}
        if (currentGame != null) {
            try { currentGame.endGame(this, true); } catch (IOException ignored) {}
        }
        server.removeClient(id);
        System.out.println("Клиент " + id + " отключён");
    }

    /**
     * Парсит и устанавливает поле клиента.
     * @param data строка с данными поля (100 символов 0/1)
     * @return true если поле валидно
     */
    private boolean parseAndSetField(String data) {
        if (data.length() != Server.BOARD_SIZE * Server.BOARD_SIZE) return false;

        boolean[][] field = new boolean[Server.BOARD_SIZE][Server.BOARD_SIZE];
        for (int i = 0; i < Server.BOARD_SIZE; i++) {
            for (int j = 0; j < Server.BOARD_SIZE; j++) {
                field[i][j] = (data.charAt(i * Server.BOARD_SIZE + j) == '1');
            }
        }

        if (!FieldValidator.validate(field)) return false;

        myField = field;
        opponentHits = new boolean[Server.BOARD_SIZE][Server.BOARD_SIZE];
        return true;
    }

    /**
     * Отправляет клиенту список доступных игроков.
     * @throws IOException при ошибке связи
     */
    private void sendPlayerList() throws IOException {
        StringBuilder sb = new StringBuilder("PLAYERS:");
        for (ClientHandler ch : server.getClients()) {
            if (ch != this && ch.ready && ch.currentGame == null) {
                sb.append(ch.id).append(",");
            }
        }
        if (sb.length() > 8) sb.setLength(sb.length() - 1);
        out.writeUTF(sb.toString());
        out.flush();
    }

    /**
     * Обрабатывает выбор противника.
     * @param targetId ID выбранного противника
     * @throws IOException при ошибке связи
     */
    private void selectOpponent(int targetId) throws IOException {
        if (currentGame != null) {
            out.writeUTF("CMD_ERROR:Already in game");
            out.flush();
            return;
        }

        ClientHandler opp = server.getClient(targetId);
        if (opp == null || opp == this || !opp.ready || opp.currentGame != null) {
            out.writeUTF("CMD_ERROR:Invalid opponent");
            out.flush();
            return;
        }

        if (!server.startGame(this, opp)) {
            out.writeUTF("CMD_ERROR:Cannot start game");
            out.flush();
            return;
        }

        out.writeUTF("CMD_GAME_START:You are playing vs " + opp.id);
        opp.out.writeUTF("CMD_GAME_START:Game started vs " + id);
        out.flush();
        opp.out.flush();

        currentGame.notifyTurn();
    }

    /**
     * Обрабатывает ход игрока.
     * @param x координата X (строка)
     * @param y координата Y (столбец)
     * @throws IOException при ошибке связи
     */
    private void makeMove(int x, int y) throws IOException {
        if (currentGame == null) {
            out.writeUTF("CMD_ERROR:No active game");
            out.flush();
            return;
        }
        if (currentGame.currentTurn != id) {
            out.writeUTF("CMD_ERROR:Not your turn");
            out.flush();
            return;
        }
        if (x < 0 || x >= Server.BOARD_SIZE || y < 0 || y >= Server.BOARD_SIZE) {
            out.writeUTF("CMD_ERROR:Invalid coordinates");
            out.flush();
            return;
        }
        if (opponentHits[x][y]) {
            out.writeUTF("CMD_ERROR:Already shot there");
            out.flush();
            return;
        }
        currentGame.makeMove(this, x, y);
    }

    /**
     * Обрабатывает сдачу игрока.
     * @throws IOException при ошибке связи
     */
    private void surrender() throws IOException {
        if (currentGame == null) {
            out.writeUTF("CMD_ERROR:Not in game");
            out.flush();
            return;
        }
        currentGame.endGame(this, true);
        currentGame = null;
    }
}