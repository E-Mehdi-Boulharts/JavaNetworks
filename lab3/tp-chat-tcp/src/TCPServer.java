import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class TCPServer {
    private static final int DEFAULT_PORT = 8080;
    private final int port;

    public TCPServer(int port) { this.port = port; }
    public TCPServer() { this(DEFAULT_PORT); }

    public void launch() throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println(this);
            System.out.println("Waiting for a TCP client...");
            try (Socket sock = server.accept()) {
                String clientIp = sock.getInetAddress().getHostAddress();
                System.out.println("Accepted connection from " + clientIp);

                try (BufferedReader in = new BufferedReader(
                         new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter out = new PrintWriter(
                         new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8), true)) {

                    String line;
                    while ((line = in.readLine()) != null) {
                        // Affiche et renvoie l'écho préfixé par l'IP du client
                        System.out.printf("[%s] %s%n", clientIp, line);
                        out.println("[" + clientIp + "] " + line);
                    }
                }
            }
        }
    }

    @Override public String toString() { return "TCPServer{port=" + port + "}"; }

    public static void main(String[] args) {
        int port = (args.length >= 1) ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        TCPServer s = new TCPServer(port);
        try { s.launch(); } catch (IOException e) { e.printStackTrace(); }
    }
}
