import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {
    public static final int BOARD_SIZE = 10;
    private static final int PORT = 12345;

    private final Map<Integer, ClientHandler> clients = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public static void main(String[] args) {
        new Server().start();
    }

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

    public void removeClient(int id) {
        clients.remove(id);
    }

    public Collection<ClientHandler> getClients() {
        return clients.values();
    }

    public ClientHandler getClient(int id) {
        return clients.get(id);
    }

    // ---- внутренняя игра ----
    class Game {
        ClientHandler p1, p2;
        int currentTurn;
        boolean active = true;

        Game(ClientHandler a, ClientHandler b) {
            p1 = a;
            p2 = b;
            currentTurn = a.getId();
        }

        void notifyTurn() throws IOException {
            if (!active) return;
            if (currentTurn == p1.getId()) {
                p1.out.writeUTF("CMD_YOUR_TURN");
                p2.out.writeUTF("CMD_OPPONENT_TURN");
            } else {
                p2.out.writeUTF("CMD_YOUR_TURN");
                p1.out.writeUTF("CMD_OPPONENT_TURN");
            }
            p1.out.flush();
            p2.out.flush();
        }

        void makeMove(ClientHandler mover, int x, int y) throws IOException {
            if (!active) return;
            ClientHandler opp = (mover == p1) ? p2 : p1;
            boolean hit = opp.myField[x][y];
            mover.opponentHits[x][y] = true;

            String result;
            if (!hit) {
                result = "MISS";
            } else {
                // Check if the ship at (x,y) is completely destroyed
                boolean sunk = isShipSunk(opp.myField, mover.opponentHits, x, y);
                if (sunk) {
                    result = "SUNK";
                } else {
                    result = "HIT";
                }
            }

            // Check for win condition
            boolean win = true;
            for (int i = 0; i < BOARD_SIZE && win; i++)
                for (int j = 0; j < BOARD_SIZE && win; j++)
                    if (opp.myField[i][j] && !mover.opponentHits[i][j]) win = false;
            if (win) result = "WIN";

            mover.out.writeUTF("CMD_MOVE_RESULT:" + result + ":" + x + ":" + y);
            opp.out.writeUTF("CMD_OPPONENT_MOVE:" + result + ":" + x + ":" + y);
            mover.out.flush();
            opp.out.flush();

            if (win) {
                endGame(mover, false);
                return;
            }

            if (hit) {
                mover.out.writeUTF("CMD_AGAIN");
                mover.out.flush();
                notifyTurn();
            } else {
                currentTurn = opp.getId();
                notifyTurn();
            }
        }

        /**
         * Checks if the ship containing cell (x,y) is completely sunk.
         * Uses flood-fill to find all connected ship cells and checks if all are hit.
         */
        private boolean isShipSunk(boolean[][] field, boolean[][] hits, int x, int y) {
            // Find all cells belonging to this ship using BFS/DFS
            boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];
            List<int[]> shipCells = new ArrayList<>();

            // DFS to find connected ship cells
            findShipCells(field, visited, x, y, shipCells);

            // Check if all ship cells are hit
            for (int[] cell : shipCells) {
                if (!hits[cell[0]][cell[1]]) {
                    return false; // Ship still has unhit cells
                }
            }
            return true; // All cells hit = ship sunk
        }

        private void findShipCells(boolean[][] field, boolean[][] visited, int x, int y, List<int[]> shipCells) {
            if (x < 0 || x >= BOARD_SIZE || y < 0 || y >= BOARD_SIZE) return;
            if (visited[x][y]) return;
            if (!field[x][y]) return;

            visited[x][y] = true;
            shipCells.add(new int[]{x, y});

            // Check 4-connected neighbors (ships are straight lines, but check all 4 for safety)
            findShipCells(field, visited, x - 1, y, shipCells);
            findShipCells(field, visited, x + 1, y, shipCells);
            findShipCells(field, visited, x, y - 1, shipCells);
            findShipCells(field, visited, x, y + 1, shipCells);
        }

        void endGame(ClientHandler loser, boolean surrender) throws IOException {
            if (!active) return;
            active = false;
            ClientHandler winner = (loser == p1) ? p2 : p1;
            winner.out.writeUTF("CMD_GAME_OVER:WIN");
            loser.out.writeUTF("CMD_GAME_OVER:LOSE");
            winner.out.flush();
            loser.out.flush();
            p1.currentGame = null;
            p2.currentGame = null;
        }
    }

    class ClientHandler implements Runnable {
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;
        private final int id;
        private final Server server;
        private boolean[][] myField;
        private boolean[][] opponentHits;
        private Game currentGame;
        private boolean ready = false;

        ClientHandler(Socket socket, int id, Server server) throws IOException {
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

        @Override
        public void run() {
            try {
                // ---- ЦИКЛ ПРИНЯТИЯ ПОЛЯ С ПОВТОРАМИ ----
                while (true) {
                    out.writeUTF("CMD_REQUIRE_FIELD");
                    out.flush();
                    System.out.println("[SERVER] CMD_REQUIRE_FIELD -> client " + id);

                    String line;
                    try {
                        line = in.readUTF();
                    } catch (EOFException e) {
                        break;
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

                // ---- ОСНОВНОЙ ЦИКЛ КОМАНД (игра) ----
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
            } catch (EOFException e) {
                // нормальное отключение
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try { socket.close(); } catch (IOException e) {}
                if (currentGame != null) {
                    try { currentGame.endGame(this, true); } catch (IOException ignored) {}
                }
                server.removeClient(id);
                System.out.println("Клиент " + id + " отключён");
            }
        }

        private boolean parseAndSetField(String data) {
            if (data.length() != BOARD_SIZE * BOARD_SIZE) return false;
            boolean[][] field = new boolean[BOARD_SIZE][BOARD_SIZE];
            for (int i = 0; i < BOARD_SIZE; i++)
                for (int j = 0; j < BOARD_SIZE; j++)
                    field[i][j] = (data.charAt(i * BOARD_SIZE + j) == '1');
            myField = field;
            opponentHits = new boolean[BOARD_SIZE][BOARD_SIZE];
            return true;
        }

        private void sendPlayerList() throws IOException {
            StringBuilder sb = new StringBuilder("PLAYERS:");
            for (ClientHandler ch : server.getClients()) {
                if (ch != this && ch.ready && ch.currentGame == null)
                    sb.append(ch.id).append(",");
            }
            if (sb.length() > 8) sb.setLength(sb.length() - 1);
            out.writeUTF(sb.toString());
            out.flush();
        }

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
            currentGame = new Game(this, opp);
            opp.currentGame = currentGame;
            out.writeUTF("CMD_GAME_START:You are playing vs " + opp.id);
            opp.out.writeUTF("CMD_GAME_START:Game started vs " + id);
            out.flush();
            opp.out.flush();
            currentGame.notifyTurn();
        }

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
            if (x < 0 || x >= BOARD_SIZE || y < 0 || y >= BOARD_SIZE) {
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
}

