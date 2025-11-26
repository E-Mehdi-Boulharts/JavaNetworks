import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;


public class TCPClient {

    private final String host;
    private final int port;

    public TCPClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** Start the client: connect and run the send/receive loop. */
    public void start() {
        final int maxAttempts = 3;
        int attempt = 0;

        while (attempt < maxAttempts) {
            attempt++;
            System.out.printf("Connecting to %s:%d (attempt %d/%d)...%n",
                    host, port, attempt, maxAttempts);

            try (Socket socket = new Socket()) {

                // 2. Create Socket connection with a timeout for the connect()
                socket.connect(new InetSocketAddress(host, port), 3000);

                // Set read timeout: if no response after 30 seconds, raise exception
                socket.setSoTimeout(30_000);

                System.out.println("Connection established.");
                System.out.println("Type messages to send. Use /quit to exit.");

                try (BufferedReader stdin = new BufferedReader(
                            new InputStreamReader(System.in, StandardCharsets.UTF_8));
                     BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter out = new PrintWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                    // Read a welcome message from server (lab4)
                    try {
                        String welcome = in.readLine();
                        if (welcome != null && !welcome.isEmpty()) {
                            System.out.println(welcome);
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("[WARNING] No welcome message from server (read timeout).");
                    }

                    String line;

                    while (true) {
                        System.out.print("> ");
                        line = stdin.readLine();

                        // User pressed CTRL+D or closed input
                        if (line == null) {
                            System.out.println("End of input. Closing connection.");
                            break;
                        }

                        line = line.trim();

                        // 6. Implement graceful shutdown on user command
                        // accept both "/quit" and "quit" (lab4)
                        if ("/quit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
                            System.out.println("User requested to quit. Closing connection.");
                            break;
                        }

                        // 6. Input validation: ignore empty lines
                        if (line.isEmpty()) {
                            continue;
                        }

                        // Send message to server
                        out.println(line);

                        // Read echo from server
                        try {
                            String response = in.readLine();
                            if (response == null) {
                                System.out.println("Server closed the connection.");
                                break;
                            }
                            System.out.println(response);
                        } catch (SocketTimeoutException e) {
                            System.out.println("[WARNING] No response from server (read timeout).");
                        }
                    }
                }

                System.out.println("Disconnected from server. Bye.");
                return; // Normal end of the client

            } catch (IOException e) {
                // 7. Handle connection errors and timeouts
                System.err.println("Connection failed: " + e.getMessage());

                if (attempt >= maxAttempts) {
                    System.err.println("Maximum number of attempts reached. Giving up.");
                    break;
                } else {
                    System.out.println("Reconnecting in 2 seconds...");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java TCPClient <host> <port>");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        TCPClient client = new TCPClient(host, port);
        client.start();
    }
}
