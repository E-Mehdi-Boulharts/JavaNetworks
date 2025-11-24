import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;


public class TCPServer {

    private static final int DEFAULT_PORT = 8080;

    private final int port;
    private final AtomicInteger clientCounter = new AtomicInteger(0);
    private final Deque<String> lastMessages = new ArrayDeque<>(10);
    private volatile boolean running = true;

    public TCPServer(int port) {
        this.port = port;
    }

    public TCPServer() {
        this(DEFAULT_PORT);
    }

    /** Start the TCP server: accept clients in a loop. */
    public void launch() {
        System.out.println(this);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log("Server started on port " + port);

            while (running) {
                // 4. Accept incoming connection
                Socket clientSocket = serverSocket.accept();

                int clientId = clientCounter.incrementAndGet();
                String clientIp = clientSocket.getInetAddress().getHostAddress();

                log("New connection from " + clientIp + " (client #" + clientId + ")");

                // Handle each client in a separate thread (multi-client support)
                Thread t = new Thread(new ClientHandler(clientId, clientSocket));
                t.start();
            }
        } catch (IOException e) {
            log("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Small helper to format timestamps in logs. */
    private void log(String msg) {
        String time = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.println("[" + time + "] " + msg);
    }

    /** Add a message to the history buffer (max 10). */
    private synchronized void addToHistory(String message) {
        if (lastMessages.size() == 10) {
            lastMessages.removeFirst();
        }
        lastMessages.addLast(message);
    }

    /** Handler for one client connection. */
    private class ClientHandler implements Runnable {
        private final int clientId;
        private final Socket socket;

        ClientHandler(int clientId, Socket socket) {
            this.clientId = clientId;
            this.socket = socket;
        }

        @Override
        public void run() {
            String clientIp = socket.getInetAddress().getHostAddress();

            try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                String line;
                // 9. Handle client disconnection gracefully
                while ((line = in.readLine()) != null) {
                    String tagged = "[#" + clientId + " " + clientIp + "] " + line;

                    // Display on server console
                    System.out.println(tagged);

                    // Store in history
                    addToHistory(tagged);

                    // Echo back to this client
                    out.println(tagged);
                }

            } catch (IOException e) {
                log("Connection error with client #" + clientId + " (" + clientIp + "): " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                log("Client #" + clientId + " (" + clientIp + ") disconnected");
            }
        }
    }

    @Override
    public String toString() {
        return "TCPServer(port=" + port + ")";
    }

    public static void main(String[] args) {
        int port = (args.length >= 1) ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        TCPServer server = new TCPServer(port);
        server.launch();
    }
}
