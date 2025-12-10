import javax.net.ssl.*;
import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public class SSLTCPServer {

    private static final int DEFAULT_PORT = 8443;

    // === Fields (comme dans les labs précédents) ===
    private final int port;
    private SSLServerSocket serverSocket;
    private volatile boolean isRunning = true;
    private static final AtomicInteger clientCounter = new AtomicInteger(0);

    // === Constructor (demande du TP) ===
    public SSLTCPServer(int port, String keystorePath, String password) {
        this.port = port;
        try {
            SSLContext sslContext = createSSLContext(keystorePath, password);

            SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
            this.serverSocket = (SSLServerSocket) factory.createServerSocket(port);

            // Optionnel : limiter les versions TLS
            // this.serverSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});

            log("SSL server socket created on port " + port);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SSLTCPServer: " + e.getMessage(), e);
        }
    }

    // === Méthode de log (copie l’esprit de TCPServer.log()) ===
    private void log(String msg) {
        String time = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.println("[" + time + "] " + msg);
    }

    // === Core method: launch() (boucle d’acceptation, comme dans les labs) ===
    public void launch() {
        log("SSLTCPServer(port=" + port + ") starting...");

        try {
            while (isRunning) {
                // Accepte une nouvelle connexion SSL
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();

                int clientId = clientCounter.incrementAndGet();
                InetAddress addr = clientSocket.getInetAddress();
                String clientIp = (addr != null) ? addr.getHostAddress() : "unknown";

                log("New SSL connection from " + clientIp + " (client #" + clientId + ")");

                // Un thread par client (comme ConnectionThread)
                Thread t = new Thread(() -> handleClient(clientId, clientSocket));
                t.setName("SSLClientHandler-" + clientId);
                t.start();

                // Ici tu pourrais faire des stats (comme printThreadStats() dans MultithreadedTCPServer)
            }
        } catch (IOException e) {
            if (isRunning) {
                log("Server error: " + e.getMessage());
                e.printStackTrace();
            } else {
                log("Server stopped.");
            }
        } finally {
            shutdown();
        }
    }

    // === createSSLContext() (exactement ce que veut le TP) ===
    private SSLContext createSSLContext(String keystorePath, String password) throws Exception {
        // 1) Charger le keystore JKS
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, password.toCharArray());
        }

        // 2) Initialiser le KeyManagerFactory avec ce keystore
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password.toCharArray());

        // 3) Créer et init l’SSLContext (TLS)
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        return sslContext;
    }

    // === handleClient() (version SSL de ConnectionThread.run()) ===
    private void handleClient(int clientId, SSLSocket clientSocket) {
        String clientIp = clientSocket.getInetAddress().getHostAddress();
        log("Handling SSL client #" + clientId + " from " + clientIp);

        try (SSLSocket socket = clientSocket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            // Handshake explicite (demande du TP)
            try {
                socket.startHandshake();
                log("TLS handshake successful with client #" + clientId + " (" + clientIp + ")");
            } catch (SSLHandshakeException e) {
                log("TLS handshake failed with client #" + clientId + " (" + clientIp + "): " + e.getMessage());
                return;
            }

            // Message de bienvenue (comme lab4, mais version SSL)
            out.println("Welcome (over TLS)! You are secure client #" + clientId);

            String line;
            while ((line = in.readLine()) != null) {
                // Gestion commande quit côté serveur comme dans ConnectionThread
                if ("quit".equalsIgnoreCase(line) || "/quit".equalsIgnoreCase(line)) {
                    out.println("Goodbye secure client #" + clientId);
                    break;
                }

                String tagged = "[#" + clientId + " " + clientIp + "] " + line;

                // Affichage côté serveur (echo + log)
                System.out.println(tagged);

                // Echo vers le client (fonctionnalité demandée dans le TP)
                out.println(tagged);
            }

        } catch (IOException e) {
            log("I/O error with client #" + clientId + " (" + clientIp + "): " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {
            }
            log("SSL client #" + clientId + " (" + clientIp + ") disconnected");
        }
    }

    // === Méthode de shutdown (graceful shutdown, demandée dans le TP) ===
    public void shutdown() {
        isRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                log("Server socket closed.");
            } catch (IOException e) {
                log("Error while closing server socket: " + e.getMessage());
            }
        }
    }

    // === main pour lancer le serveur facilement, comme dans tes labs ===
    public static void main(String[] args) {
        int port = (args.length >= 1) ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        // On suppose que server.jks est dans le dossier courant
        String keystorePath = "server.jks";
        String password = "password123"; // même mot de passe que keytool

        SSLTCPServer server = new SSLTCPServer(port, keystorePath, password);
        server.launch();
    }
}
