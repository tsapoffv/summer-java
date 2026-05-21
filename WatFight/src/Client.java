import java.io.*;
import java.net.*;
import java.util.*;

public class Client {
    private static final int BOARD_SIZE = 10;
    private static final int[] SHIP_SIZES = {4, 3, 3, 2, 2, 2, 1, 1, 1, 1};

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private volatile boolean inGame = false;
    private volatile boolean myTurn = false;

    public static void main(String[] args) {
        new Client().start();
    }

    public void start() {
        try {
            System.out.println("Подключение к серверу...");
            socket = new Socket("127.0.0.1", 12345);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            socket.setSoTimeout(5000); // таймаут на время рукопожатия

            // ---- ЦИКЛ ОТПРАВКИ ПОЛЯ ДО УСПЕХА ----
            boolean fieldAccepted = false;
            while (!fieldAccepted) {
                // Ждём запроса поля от сервера
                String firstMsg;
                try {
                    firstMsg = in.readUTF();
                } catch (SocketTimeoutException e) {
                    System.err.println("Таймаут ожидания запроса поля, повторяем...");
                    continue;
                }
                if (!firstMsg.equals("CMD_REQUIRE_FIELD")) {
                    System.err.println("Неожиданное сообщение: " + firstMsg + ", ожидаем CMD_REQUIRE_FIELD");
                    continue;
                }
                System.out.println("Сервер запросил поле. Генерируем...");
                String field = generateValidField();
                out.writeUTF("SET_FIELD:" + field);
                out.flush();

                String response;
                try {
                    response = in.readUTF();
                } catch (SocketTimeoutException e) {
                    System.err.println("Таймаут ожидания ответа, повторяем...");
                    continue;
                }
                System.out.println("Ответ сервера: " + response);
                if (response.startsWith("CMD_OK")) {
                    System.out.println("Поле принято! Добро пожаловать.");
                    fieldAccepted = true;
                } else if (response.startsWith("CMD_ERROR:")) {
                    System.err.println("Ошибка: " + response.substring(10) + ". Генерируем новое поле и пробуем снова...");
                    // сервер повторно пришлёт CMD_REQUIRE_FIELD, цикл продолжается
                } else {
                    System.err.println("Неожиданный ответ: " + response);
                }
            }

            // После успешного принятия поля убираем таймаут (чтобы не мешал в игре)
            socket.setSoTimeout(0);

            // Фоновый поток для сообщений
            Thread receiver = new Thread(this::receiveMessages);
            receiver.setDaemon(true);
            receiver.start();

            // Меню
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                if (!inGame) {
                    System.out.println("\n=== МЕНЮ ===");
                    System.out.println("1. Список игроков");
                    System.out.println("2. Выбрать противника");
                    System.out.println("3. Выход");
                    System.out.print("> ");
                    String cmd = console.readLine();
                    switch (cmd) {
                        case "1":
                            out.writeUTF("GET_PLAYERS");
                            break;
                        case "2":
                            System.out.print("ID противника: ");
                            out.writeUTF("SELECT_PLAYER:" + console.readLine());
                            break;
                        case "3":
                            return;
                        default:
                            System.out.println("Неизвестная команда");
                    }
                } else if (myTurn) {
                    System.out.print("Ваш ход (x y) или 'surrender': ");
                    String[] xy = console.readLine().trim().split(" ");
                    if (xy.length == 2) {
                        try {
                            int x = Integer.parseInt(xy[0]);
                            int y = Integer.parseInt(xy[1]);
                            out.writeUTF("MOVE:" + x + ":" + y);
                        } catch (NumberFormatException e) {
                            System.out.println("Числа от 0 до 9");
                        }
                    } else if (xy[0].equalsIgnoreCase("surrender")) {
                        out.writeUTF("SURRENDER");
                        inGame = false;
                        myTurn = false;
                    } else {
                        System.out.println("Неверный ввод");
                    }
                } else {
                    System.out.println("Ожидание хода противника...");
                    Thread.sleep(1000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException e) {}
        }
    }

    private void receiveMessages() {
        try {
            while (true) {
                String msg = in.readUTF();
                if (msg.startsWith("PLAYERS:")) {
                    String list = msg.substring(8);
                    System.out.println(list.isEmpty() ? "Нет игроков" : "Игроки: " + list);
                } else if (msg.startsWith("CMD_GAME_START:")) {
                    System.out.println(msg.substring(15));
                    inGame = true;
                } else if (msg.equals("CMD_YOUR_TURN")) {
                    myTurn = true;
                    System.out.println(">>> ВАШ ХОД! <<<");
                } else if (msg.equals("CMD_OPPONENT_TURN")) {
                    myTurn = false;
                    System.out.println(">>> ХОД ПРОТИВНИКА... <<<");
                } else if (msg.startsWith("CMD_MOVE_RESULT:")) {
                    String[] p = msg.split(":");
                    System.out.printf("Выстрел (%s,%s): %s\n", p[2], p[3], p[1]);
                    if (p[1].equals("WIN")) {
                        System.out.println("ПОБЕДА!");
                        inGame = false;
                        myTurn = false;
                    }
                } else if (msg.startsWith("CMD_OPPONENT_MOVE:")) {
                    String[] p = msg.split(":");
                    System.out.printf("Противник (%s,%s): %s\n", p[2], p[3], p[1]);
                    if (p[1].equals("WIN")) {
                        System.out.println("ПОРАЖЕНИЕ!");
                        inGame = false;
                        myTurn = false;
                    }
                } else if (msg.equals("CMD_AGAIN")) {
                    System.out.println("Попадание! Ходите ещё.");
                } else if (msg.startsWith("CMD_GAME_OVER:")) {
                    System.out.println(msg);
                    inGame = false;
                    myTurn = false;
                } else if (msg.startsWith("CMD_ERROR:")) {
                    System.err.println("Ошибка: " + msg.substring(10));
                } else {
                    System.out.println("Сервер: " + msg);
                }
            }
        } catch (IOException e) {
            System.out.println("Соединение потеряно");
        }
    }

    // Генератор поля, который старается быть корректным (корабли не касаются)
    private String generateValidField() {
        boolean[][] field = new boolean[BOARD_SIZE][BOARD_SIZE];
        Random rand = new Random();
        List<Integer> sizes = new ArrayList<>();
        for (int s : SHIP_SIZES) sizes.add(s);
        Collections.shuffle(sizes, rand);

        for (int size : sizes) {
            boolean placed = false;
            int attempts = 0;
            while (!placed && attempts < 5000) {
                attempts++;
                int x = rand.nextInt(BOARD_SIZE);
                int y = rand.nextInt(BOARD_SIZE);
                boolean horiz = rand.nextBoolean();
                if (canPlace(field, x, y, size, horiz)) {
                    placeShip(field, x, y, size, horiz);
                    placed = true;
                }
            }
            if (!placed) {
                // сброс и повтор
                return generateValidField(); // рекурсивно пробуем заново
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < BOARD_SIZE; i++)
            for (int j = 0; j < BOARD_SIZE; j++)
                sb.append(field[i][j] ? '1' : '0');
        return sb.toString();
    }

    private boolean canPlace(boolean[][] field, int x, int y, int size, boolean horiz) {
        if (horiz) {
            if (y + size > BOARD_SIZE) return false;
            for (int i = -1; i <= size; i++) {
                for (int j = -1; j <= 1; j++) {
                    int nx = x + j;
                    int ny = y + i;
                    if (nx >= 0 && nx < BOARD_SIZE && ny >= 0 && ny < BOARD_SIZE) {
                        if (i >= 0 && i < size && j == 0) {
                            if (field[nx][ny]) return false;
                        } else {
                            if (field[nx][ny]) return false;
                        }
                    }
                }
            }
        } else {
            if (x + size > BOARD_SIZE) return false;
            for (int i = -1; i <= size; i++) {
                for (int j = -1; j <= 1; j++) {
                    int nx = x + i;
                    int ny = y + j;
                    if (nx >= 0 && nx < BOARD_SIZE && ny >= 0 && ny < BOARD_SIZE) {
                        if (i >= 0 && i < size && j == 0) {
                            if (field[nx][ny]) return false;
                        } else {
                            if (field[nx][ny]) return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private void placeShip(boolean[][] field, int x, int y, int size, boolean horiz) {
        if (horiz) {
            for (int i = 0; i < size; i++) field[x][y + i] = true;
        } else {
            for (int i = 0; i < size; i++) field[x + i][y] = true;
        }
    }
}