import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class SSLClient {

    private SSLSocket socket;
    private BufferedReader in;
    private PrintWriter out;

    private final String host;
    private final int port;
    private final boolean trustAllCerts; // Testing vs production mode

    public SSLClient(String host, int port, boolean trustAllCerts) {
        this.host = host;
        this.port = port;
        this.trustAllCerts = trustAllCerts;
    }

    /** Create SSL context according to trustAllCerts flag. */
    private SSLContext createSSLContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");

        if (trustAllCerts) {
            // --- TESTING MODE: trust any certificate (self-signed, etc.) ---
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        // Do not validate client certificates (testing only)
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        // Do not validate server certificates (testing only)
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };

            sslContext.init(null, trustAll, new SecureRandom());
            System.out.println("[WARNING] Trust-all SSL context enabled (TESTING ONLY!)");

        } else {
            // --- PRODUCTION MODE: use system/default CA trust store ---
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null); // load default system trust store
            sslContext.init(null, tmf.getTrustManagers(), null);
        }

        return sslContext;
    }

    /** Establish SSL connection and perform handshake. */
    public void connect() throws Exception {
        SSLContext sslContext = createSSLContext();
        SSLSocketFactory factory = sslContext.getSocketFactory();

        // We use a manual connect to keep a timeout, like in your TCPClient
        this.socket = (SSLSocket) factory.createSocket();
        this.socket.connect(new InetSocketAddress(host, port), 3000);   // 3s connect timeout
        this.socket.setSoTimeout(30_000);                               // 30s read timeout

        // Explicit TLS handshake (comme demandé dans le sujet)
        this.socket.startHandshake();

        this.in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

        System.out.println("Connected to SSL server " + host + ":" + port);
    }

    /** Send one line of text to the server. */
    public void sendMessage(String message) throws IOException {
        if (out == null) {
            throw new IllegalStateException("Not connected");
        }
        out.println(message);
    }

    /** Read one line of response from the server. */
    public String receiveResponse() throws IOException {
        if (in == null) {
            throw new IllegalStateException("Not connected");
        }
        return in.readLine();
    }

    /** Close SSL connection and streams. */
    public void disconnect() {
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {}

        if (out != null) {
            out.close();
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}

        System.out.println("Disconnected from SSL server.");
    }

    /** Simple interactive client, same spirit as your TCPClient (lab3/lab4). */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java SSLClient <host> <port> [trustAll]");
            System.err.println("Example (testing with self-signed cert): java SSLClient localhost 8443 true");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        boolean trustAll = (args.length >= 3) && Boolean.parseBoolean(args[2]);

        SSLClient client = new SSLClient(host, port, trustAll);

        try {
            client.connect();

            try (BufferedReader stdin = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

                System.out.println("Type messages to send over TLS. Use /quit to exit.");

                while (true) {
                    System.out.print("> ");
                    String line = stdin.readLine();

                    // User pressed CTRL+D or closed input
                    if (line == null) {
                        System.out.println("End of input. Closing connection.");
                        break;
                    }

                    line = line.trim();

                    if (line.isEmpty()) {
                        continue;
                    }

                    // Same convention as lab4: /quit or quit
                    if ("/quit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
                        System.out.println("User requested to quit.");
                        break;
                    }

                    // Send message to server
                    client.sendMessage(line);

                    // Read echo from server (may be null if server closed)
                    String response = client.receiveResponse();
                    if (response == null) {
                        System.out.println("Server closed the connection.");
                        break;
                    }
                    System.out.println(response);
                }
            }

        } catch (Exception e) {
            System.err.println("SSL client error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.disconnect();
        }
    }
}
