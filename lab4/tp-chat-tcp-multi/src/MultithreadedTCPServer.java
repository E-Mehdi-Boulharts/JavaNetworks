import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

public class MultithreadedTCPServer {

    private static final int DEFAULT_PORT = 8080;

    private final int port;
    private static final AtomicInteger clientCounter = new AtomicInteger(0);

    public MultithreadedTCPServer(int port) {
        this.port = port;
    }

    public MultithreadedTCPServer() {
        this(DEFAULT_PORT);
    }

    public void launch() {
        System.out.println(this);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Multithreaded Server started on port " + port);

            while (true) {
                // Thread principal : accepte les connexions
                Socket clientSocket = serverSocket.accept();

                // ID unique et thread-safe
                int clientId = clientCounter.incrementAndGet();

                // Un thread par client (Step 3 du TP)
                ConnectionThread clientThread =
                        new ConnectionThread(clientSocket, clientId);
                clientThread.start();

                // Monitoring des threads (section 6.2 du TP)
                printThreadStats();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Méthode demandée dans 6.2.1 "Monitoring Implementation" du TP :contentReference[oaicite:8]{index=8}
    private void printThreadStats() {
        Runtime runtime = Runtime.getRuntime();
        System.out.println("=== Thread Statistics ===");
        System.out.println(" Active threads : " + (Thread.activeCount() - 1));
        System.out.println(" Memory usage : " +
                (runtime.totalMemory() - runtime.freeMemory()) / 1024 + " KB");
    }

    @Override
    public String toString() {
        return "MultithreadedTCPServer(port=" + port + ")";
    }

    public static void main(String[] args) {
        int port = (args.length >= 1) ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        MultithreadedTCPServer server = new MultithreadedTCPServer(port);
        server.launch();
    }
}
