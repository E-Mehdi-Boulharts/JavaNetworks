import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * TCP client for the lab.
 * Enhancements for section 3.9:
 * - Application-level messages with ChatMessage (type, seq, length)
 * - Better error reporting for protocol and network errors
 */
public class NewTCPClient {

    private final String host;
    private final int port;

    public NewTCPClient(String host, int port) {
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

                // Connection timeout
                socket.connect(new InetSocketAddress(host, port), 3000);

                // Read timeout: if no response after 30 seconds, raise exception
                socket.setSoTimeout(30_000);

                System.out.println("Connection established.");
                System.out.println("Type messages to send. Use /quit to exit.");

                try (BufferedReader stdin = new BufferedReader(
                            new InputStreamReader(System.in, StandardCharsets.UTF_8));
                     BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter out = new PrintWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                    String line;
                    int nextSeq = 1;

                    while (true) {
                        System.out.print("> ");
                        line = stdin.readLine();

                        if (line == null) {
                            System.out.println("End of input. Closing connection.");
                            break;
                        }

                        line = line.trim();

                        if ("/quit".equalsIgnoreCase(line)) {
                            System.out.println("User requested to quit. Closing connection.");
                            break;
                        }

                        if (line.isEmpty()) {
                            continue;
                        }

                        // Build a CHAT message and send it (3.9.1)
                        ChatMessage msg = ChatMessage.chat(nextSeq++, line);
                        out.println(ChatMessage.encode(msg));

                        // Read echo from server
                        try {
                            String respWire = in.readLine();
                            if (respWire == null) {
                                System.out.println("Server closed the connection.");
                                break;
                            }

                            try {
                                ChatMessage resp = ChatMessage.decode(respWire);

                                if (resp.type == ChatMessage.Type.ERROR) {
                                    System.out.println("[SERVER ERROR] " + resp.payload);
                                } else {
                                    System.out.printf("[server seq=%d type=%s len=%d] %s%n",
                                            resp.seq, resp.type, resp.length, resp.payload);
                                }

                            } catch (ProtocolException pe) {
                                System.out.println("[PROTOCOL ERROR] " + pe.getMessage());
                            }

                        } catch (SocketTimeoutException e) {
                            System.out.println("[WARNING] No response from server (read timeout).");
                        }
                    }
                }

                System.out.println("Disconnected from server. Bye.");
                return; // End of client

            } catch (IOException e) {
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

        NewTCPClient client = new NewTCPClient(host, port);
        client.start();
    }
}