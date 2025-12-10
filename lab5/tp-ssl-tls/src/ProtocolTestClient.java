import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;

public class ProtocolTestClient {

    private final String host;
    private final int port;

    public ProtocolTestClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * mode = "scenario" (par défaut), "interactive" ou "invalid-no-login"
     */
    public void run(String username, String room, String mode) throws Exception {
        SSLContext ctx = createTrustAllContext();
        SSLSocketFactory factory = ctx.getSocketFactory();

        try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {

            socket.startHandshake();

            InetAddress addr = socket.getInetAddress();
            System.out.println("Connected to " + addr.getHostAddress() + ":" + port);
            System.out.println("Mode = " + mode);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            String lowerMode = mode.toLowerCase();

            if ("invalid-no-login".equals(lowerMode)) {
                runInvalidNoLogin(username, room, in, out);
            } else if ("interactive".equals(lowerMode)) {
                runInteractive(username, room, in, out);
            } else { // "scenario" ou inconnu -> scénario simple comme avant
                runScenario(username, room, in, out);
            }

            System.out.println("Client closing socket.");
        }
    }

    /* ====================== Scénario simple (comme avant) ======================= */

    private void runScenario(String username, String room,
                             DataInputStream in, DataOutputStream out) throws IOException {

        // 1) LOGIN_REQUEST
        ChatMessage login = new ChatMessage(
                MessageType.LOGIN_REQUEST,
                "1.0",
                Instant.now(),
                username,
                null,
                null,
                null
        );
        sendMessage(out, login);
        System.out.println("Sent LOGIN_REQUEST as " + username);
        ChatMessage resp1 = readMessage(in);
        System.out.println("Received: " + resp1);

        // 2) JOIN_ROOM_REQUEST
        ChatMessage join = new ChatMessage(
                MessageType.JOIN_ROOM_REQUEST,
                "1.0",
                Instant.now(),
                username,
                null,
                room,
                null
        );
        sendMessage(out, join);
        System.out.println("Sent JOIN_ROOM_REQUEST for room '" + room + "'");
        ChatMessage resp2 = readMessage(in);
        System.out.println("Received: " + resp2);

        // 3) TEXT_MESSAGE pour la room
        ChatMessage text = new ChatMessage(
                MessageType.TEXT_MESSAGE,
                "1.0",
                Instant.now(),
                username,
                null,      // pas de destinataire -> broadcast dans la room
                room,
                "Hello from " + username
        );
        sendMessage(out, text);
        System.out.println("Sent TEXT_MESSAGE to room '" + room + "'");

        // On lit quelques messages puis on termine
        for (int i = 0; i < 5; i++) {
            ChatMessage msg = readMessage(in);
            if (msg == null) {
                System.out.println("Server closed connection.");
                break;
            }
            System.out.println("Received: " + msg);
        }
    }

    /* ====================== Mode interactif (chat temps réel) ======================= */

    private void runInteractive(String username, String room,
                                DataInputStream in, DataOutputStream out) throws IOException {

        // LOGIN_REQUEST
        ChatMessage login = new ChatMessage(
                MessageType.LOGIN_REQUEST,
                "1.0",
                Instant.now(),
                username,
                null,
                null,
                null
        );
        sendMessage(out, login);
        System.out.println("Sent LOGIN_REQUEST as " + username);
        ChatMessage resp1 = readMessage(in);
        System.out.println("Received: " + resp1);

        // JOIN_ROOM_REQUEST
        ChatMessage join = new ChatMessage(
                MessageType.JOIN_ROOM_REQUEST,
                "1.0",
                Instant.now(),
                username,
                null,
                room,
                null
        );
        sendMessage(out, join);
        System.out.println("Sent JOIN_ROOM_REQUEST for room '" + room + "'");
        ChatMessage resp2 = readMessage(in);
        System.out.println("Received: " + resp2);

        System.out.println("Interactive mode.");
        System.out.println("  - plain text       -> message à la room '" + room + "'");
        System.out.println("  - /msg user text   -> message privé à 'user'");
        System.out.println("  - /quit            -> quitter proprement");

        BufferedReader stdin = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8)
        );

        // Thread de réception asynchrone : lit TOUT ce que le serveur envoie
        Thread receiver = new Thread(() -> {
            try {
                while (true) {
                    ChatMessage msg = readMessage(in);
                    if (msg == null) {
                        System.out.println("\n[INCOMING] Server closed connection.");
                        break;
                    }
                    System.out.println("\n[INCOMING] " + msg);
                    System.out.print("> ");
                }
            } catch (IOException e) {
                System.out.println("\n[INCOMING] Receiver stopped: " + e.getMessage());
            }
        });
        receiver.setDaemon(true);
        receiver.start();

        // Boucle d'envoi : lit le clavier, envoie des ChatMessage
        while (true) {
            System.out.print("> ");
            String line = stdin.readLine();
            if (line == null) {
                System.out.println("End of input, closing.");
                break;
            }
            line = line.trim();
            if (line.isEmpty()) continue;

            if ("/quit".equalsIgnoreCase(line)) {
                System.out.println("User requested to quit.");
                break;
            }

            ChatMessage msgToSend;


            if (line.startsWith("/msg ")) {
                // /msg david Hello private message
                String[] parts = line.split("\\s+", 3);
                if (parts.length < 3) {
                    System.out.println("Usage: /msg <user> <text>");
                    continue;
                }
                String targetUser = parts[1];
                String text = parts[2];

                // message privé : recipient rempli, room null
                msgToSend = new ChatMessage(
                        MessageType.PRIVATE_MESSAGE,  
                        "1.0",
                        Instant.now(),
                        username,
                        targetUser,
                        null,
                        text
                );
            } else {
                // message normal dans la room
                msgToSend = new ChatMessage(
                        MessageType.TEXT_MESSAGE,
                        "1.0",
                        Instant.now(),
                        username,
                        null,        // pas de destinataire -> broadcast
                        room,
                        line
                );
            }

            sendMessage(out, msgToSend);
            System.out.println("[SENT] " + msgToSend);
        }
    }

    /* ====================== Scénario invalide (texte avant login) ======================= */

    private void runInvalidNoLogin(String username, String room,
                                   DataInputStream in, DataOutputStream out) throws IOException {
        System.out.println("Running INVALID scenario: sending TEXT_MESSAGE before LOGIN.");

        ChatMessage invalidText = new ChatMessage(
                MessageType.TEXT_MESSAGE,
                "1.0",
                Instant.now(),
                username,
                null,
                room,
                "This message is sent before LOGIN_REQUEST"
        );
        sendMessage(out, invalidText);
        System.out.println("Sent invalid TEXT_MESSAGE before login.");

        ChatMessage resp = readMessage(in);
        if (resp == null) {
            System.out.println("No response (server closed connection).");
        } else {
            System.out.println("Received (should be ERROR_RESPONSE): " + resp);
        }
    }

    /* ====================== SSL CONTEXT (trust-all) ======================= */

    private SSLContext createTrustAllContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new SecureRandom());
        return ctx;
    }

    /* ====================== Envoi / réception de ChatMessage ======================= */

    private void sendMessage(DataOutputStream out, ChatMessage msg) throws IOException {
        byte[] data = msg.toBytes(); // [4 bytes length][JSON UTF-8]
        out.write(data);
        out.flush();
    }

    private ChatMessage readMessage(DataInputStream in) throws IOException {
        try {
            int len = in.readInt();
            if (len <= 0 || len > 64 * 1024) {
                throw new IOException("Invalid length: " + len);
            }
            byte[] jsonBytes = new byte[len];
            in.readFully(jsonBytes);

            byte[] data = new byte[4 + len];
            data[0] = (byte) ((len >> 24) & 0xFF);
            data[1] = (byte) ((len >> 16) & 0xFF);
            data[2] = (byte) ((len >> 8) & 0xFF);
            data[3] = (byte) (len & 0xFF);
            System.arraycopy(jsonBytes, 0, data, 4, len);

            return ChatMessage.fromBytes(data);

        } catch (EOFException e) {
            System.out.println("Server closed connection (EOF).");
            return null;
        }
    }

    /* ====================== MAIN ======================= */

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: java ProtocolTestClient <host> <port> <username> [room] [mode]");
            System.err.println("  mode: scenario | interactive | invalid-no-login (default: scenario)");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String username = args[2];
        String room = (args.length >= 4) ? args[3] : "general";
        String mode = (args.length >= 5) ? args[4] : "scenario";

        ProtocolTestClient client = new ProtocolTestClient(host, port);
        client.run(username, room, mode);
    }
}
