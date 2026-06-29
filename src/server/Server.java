package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Главный класс сервера морского боя.
 * Отвечает за:
 * - Приём подключений клиентов
 * - Управление пулом клиентов
 * - Создание игр между клиентами
 *
 * Сервер работает на порту 12345
 */
public class Server {
    /** Размер игрового поля (10x10) */
    public static final int BOARD_SIZE = 10;

    /** Порт для подключения клиентов */
    private static final int PORT = 12345;

    /** Размеры кораблей для игры */
    private static final int[] SHIP_SIZES = {4, 3, 3, 2, 2, 2, 1, 1, 1, 1};

    /** Карта подключенных клиентов (ID -> обработчик) */
    private final Map<Integer, ClientHandler> clients = new ConcurrentHashMap<>();

    /** Генератор уникальных ID для клиентов */
    private final AtomicInteger nextId = new AtomicInteger(1);

    /**
     * Точка входа в серверное приложение.
     */
    public static void main(String[] args) {
        new Server().start();
    }

    /**
     * Запускает сервер.
     * Создаёт серверный сокет и начинает принимать подключения.
     * Каждый клиент обрабатывается в отдельном потоке.
     */
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Сервер запущен на порту " + PORT);
            ExecutorService pool = Executors.newCachedThreadPool();

            while (true) {
                Socket socket = serverSocket.accept();
                int id = nextId.getAndIncrement();
                ClientHandler handler = new ClientHandler(socket, id, this);
                clients.put(id, handler);
                pool.execute(handler);
                System.out.println("Клиент " + id + " подключён");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Удаляет клиента из списка подключенных.
     * @param id ID клиента для удаления
     */
    public synchronized void removeClient(int id) {
        clients.remove(id);
    }

    /**
     * Возвращает коллекцию всех подключенных клиентов.
     * @return коллекция обработчиков клиентов
     */
    public Collection<ClientHandler> getClients() {
        return clients.values();
    }

    /**
     * Возвращает обработчик клиента по ID.
     * @param id ID клиента
     * @return обработчик клиента или null
     */
    public ClientHandler getClient(int id) {
        return clients.get(id);
    }

    /**
     * Запускает игру между двумя клиентами.
     * @param a первый игрок
     * @param b второй игрок
     * @return true если игра успешно создана
     */
    public synchronized boolean startGame(ClientHandler a, ClientHandler b) {
        if (a.currentGame != null || b.currentGame != null) return false;
        if (!a.ready || !b.ready) return false;
        Game game = new Game(a, b);
        a.currentGame = game;
        b.currentGame = game;
        return true;
    }
}