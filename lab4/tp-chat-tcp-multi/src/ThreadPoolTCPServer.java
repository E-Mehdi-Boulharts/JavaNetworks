import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolTCPServer {

    private static final int DEFAULT_PORT = 8080;

    private final int port;
    private final ExecutorService threadPool;
    private static final AtomicInteger clientCounter = new AtomicInteger(0);

    public ThreadPoolTCPServer(int port) {
        this.port = port;
        // Fixed-size pool: maximum 10 concurrent client handlers
        this.threadPool = Executors.newFixedThreadPool(10);
    }

    public ThreadPoolTCPServer() {
        this(DEFAULT_PORT);
    }

    public void launch() {
        System.out.println(this);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Thread Pool Server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                int clientId = clientCounter.incrementAndGet();

                // Submit a task to the pool instead of creating a new thread
                threadPool.execute(() -> {
                    ConnectionThread handler =
                            new ConnectionThread(clientSocket, clientId);
                    handler.run(); // run() is executed by a pooled thread
                });

                printThreadStats();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    private void printThreadStats() {
        Runtime runtime = Runtime.getRuntime();
        System.out.println("=== Thread Statistics ===");
        System.out.println(" Active threads : " + (Thread.activeCount() - 1));
        System.out.println(" Memory usage : " +
                (runtime.totalMemory() - runtime.freeMemory()) / 1024 + " KB");
    }

    public void shutdown() {
        threadPool.shutdown();
        System.out.println("Server shutdown initiated");
    }

    @Override
    public String toString() {
        return "ThreadPoolTCPServer(port=" + port + ")";
    }

    public static void main(String[] args) {
        int port = (args.length >= 1) ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        ThreadPoolTCPServer server = new ThreadPoolTCPServer(port);
        server.launch();
    }
}
