import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-client TCP echo server for the lab.
 * Enhancements for section 3.9:
 * - application-level message format (ChatMessage)
 * - per-client sessions with duration and inactivity timeout
 * - better protocol error reporting
 */
public class NewTCPServer {

    private static final int DEFAULT_PORT = 8080;

    // Session timeout for inactivity (3.9.2)
    private static final int SESSION_TIMEOUT_MS = 60_000; // 60 seconds

    private final int port;
    private final AtomicInteger clientCounter = new AtomicInteger(0);
    private final Deque<String> lastMessages = new ArrayDeque<>(10);
    private volatile boolean running = true;

    public NewTCPServer(int port) {
        this.port = port;
    }

    public NewTCPServer() {
        this(DEFAULT_PORT);
    }

    public void launch() {
        System.out.println(this);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log("Server started on port " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();

                int clientId = clientCounter.incrementAndGet();
                String clientIp = clientSocket.getInetAddress().getHostAddress();

                log("New connection from " + clientIp + " (client #" + clientId + ")");

                Thread t = new Thread(new ClientHandler(clientId, clientSocket));
                t.start();
            }
        } catch (IOException e) {
            log("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void log(String msg) {
        String time = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.println("[" + time + "] " + msg);
    }

    private synchronized void addToHistory(String message) {
        if (lastMessages.size() == 10) {
            lastMessages.removeFirst();
        }
        lastMessages.addLast(message);
    }

    /**
     * Handle a single client session (3.9.2 Session Management).
     */
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
            long startTime = System.currentTimeMillis();

            try {
                // Inactivity timeout (3.9.2)
                socket.setSoTimeout(SESSION_TIMEOUT_MS);

                try (BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter out = new PrintWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                    String wire;
                    long lastActivity = startTime;

                    while (true) {
                        try {
                            wire = in.readLine();
                        } catch (SocketTimeoutException e) {
                            log("Session timeout for client #" + clientId + " (" + clientIp +
                                ") after " + SESSION_TIMEOUT_MS + " ms of inactivity");
                            break;
                        }

                        if (wire == null) {
                            // Client closed connection
                            break;
                        }

                        lastActivity = System.currentTimeMillis();

                        try {
                            // Decode and validate the message (3.9.1 + 3.9.3)
                            ChatMessage msg = ChatMessage.decode(wire);

                            String tagged = String.format(
                                    "[#%d %s seq=%d type=%s len=%d] %s",
                                    clientId, clientIp, msg.seq, msg.type, msg.length, msg.payload);

                            System.out.println(tagged);
                            addToHistory(tagged);

                            // Echo back as SYSTEM message to show we parsed it
                            ChatMessage echoMsg = new ChatMessage(
                                    ChatMessage.Type.SYSTEM,
                                    msg.seq,
                                    msg.payload);

                            out.println(ChatMessage.encode(echoMsg));

                        } catch (ProtocolException pe) {
                            log("Protocol error with client #" + clientId + " (" + clientIp + "): "
                                    + pe.getMessage());

                            // Send an ERROR message to the client
                            ChatMessage err = new ChatMessage(
                                    ChatMessage.Type.ERROR,
                                    0,
                                    "Protocol error: " + pe.getMessage());
                            out.println(ChatMessage.encode(err));
                        }
                    }

                    long endTime = System.currentTimeMillis();
                    long durationSec = (endTime - startTime) / 1000;
                    log("Session for client #" + clientId + " (" + clientIp + ") closed. Duration = "
                            + durationSec + " s");

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
        NewTCPServer server = new NewTCPServer(port);
        server.launch();
    }
}
