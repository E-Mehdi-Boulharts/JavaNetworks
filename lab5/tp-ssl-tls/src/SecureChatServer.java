import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SecureChatServer {

    private final int port;
    private final String keystorePath;
    private final String keystorePassword;

    private SSLServerSocket serverSocket;
    private volatile boolean isRunning = true;

    // Sessions actives : username -> session
    private final Map<String, ClientSession> activeSessions = new ConcurrentHashMap<>();

    // Rooms : roomName -> ChatRoom
    private final Map<String, ChatRoom> chatRooms = new ConcurrentHashMap<>();

    public SecureChatServer(int port, String keystorePath, String keystorePassword) {
        this.port = port;
        this.keystorePath = keystorePath;
        this.keystorePassword = keystorePassword;
    }

    private void log(String msg) {
        String time = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.println("[" + time + "] " + msg);
    }

    // ================= SSL INIT =================

    private SSLContext createSSLContext() throws Exception {
        // Sur ton JDK, le type par défaut est PKCS12
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, keystorePassword.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keystorePassword.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }

    public void launch() {
        try {
            SSLContext sslContext = createSSLContext();
            SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
            serverSocket = (SSLServerSocket) factory.createServerSocket(port);

            log("SecureChatServer listening on port " + port);

            while (isRunning) {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                InetAddress addr = clientSocket.getInetAddress();
                String ip = (addr != null) ? addr.getHostAddress() : "unknown";

                log("New connection from " + ip);

                Thread t = new Thread(() -> handleClient(clientSocket));
                t.setName("SecureChatClient-" + ip);
                t.start();
            }
        } catch (Exception e) {
            log("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    public void shutdown() {
        isRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }
        log("SecureChatServer stopped.");
    }

    // ================ CLIENT HANDLING ================

    private void handleClient(SSLSocket clientSocket) {
        ClientSession session = null;

        try (SSLSocket socket = clientSocket) {

            socket.startHandshake();
            String ip = socket.getInetAddress().getHostAddress();
            log("TLS handshake successful with " + ip);

            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            while (true) {
                ChatMessage msg = readMessage(in);
                if (msg == null) {
                    log("Client " + (session != null ? session.getUsername() : ip)
                            + " disconnected.");
                    break;
                }

                if (session == null) {
                    // On attend LOGIN_REQUEST comme premier message
                    if (msg.getType() == MessageType.LOGIN_REQUEST) {
                        session = handleLogin(msg, socket, out);
                        if (session == null) {
                            // login refusé -> on ferme
                            log("Login failed, closing connection from " + ip);
                            break;
                        }
                    } else {
                        sendError(out, "You must login first.");
                    }
                } else {
                    // Utilisateur déjà loggé -> on traite les messages de chat
                    handleProtocolMessage(session, msg);
                }
            }

        } catch (IOException e) {
            log("Client I/O error: " + e.getMessage());
        } finally {
            // Nettoyage session + room
            if (session != null) {
                activeSessions.remove(session.getUsername());
                String roomName = session.getCurrentRoom();
                if (roomName != null) {
                    ChatRoom room = chatRooms.get(roomName);
                    if (room != null) {
                        room.leave(session);
                    }
                }
                log("Session closed for user " + session.getUsername());
            }
        }
    }

    private ChatMessage readMessage(DataInputStream in) throws IOException {
        try {
            int len = in.readInt();
            if (len <= 0 || len > 64 * 1024) { // 64 KB max pour la démo
                throw new IOException("Invalid message length: " + len);
            }

            byte[] jsonBytes = new byte[len];
            in.readFully(jsonBytes);

            // On recrée le buffer attendu par ChatMessage.fromBytes()
            byte[] data = new byte[4 + len];
            data[0] = (byte) ((len >> 24) & 0xFF);
            data[1] = (byte) ((len >> 16) & 0xFF);
            data[2] = (byte) ((len >> 8) & 0xFF);
            data[3] = (byte) (len & 0xFF);
            System.arraycopy(jsonBytes, 0, data, 4, len);

            return ChatMessage.fromBytes(data);

        } catch (EOFException e) {
            // Client a fermé la connexion
            return null;
        }
    }

    // ================ LOGIN & MESSAGE HANDLERS ================

    private ClientSession handleLogin(ChatMessage loginMsg,
                                      SSLSocket socket,
                                      DataOutputStream out) throws IOException {
        String username = loginMsg.getSender();

        if (username == null || username.isBlank()) {
            sendError(out, "Username must not be empty.");
            return null;
        }

        if (activeSessions.containsKey(username)) {
            sendError(out, "Username already in use: " + username);
            return null;
        }

        ClientSession session = new ClientSession(username, socket, out);
        activeSessions.put(username, session);

        log("User logged in: " + username);

        ChatMessage response = new ChatMessage(
                MessageType.LOGIN_RESPONSE,
                loginMsg.getVersion() != null ? loginMsg.getVersion() : "1.0",
                java.time.Instant.now(),
                "server",
                username,
                null,
                "Welcome " + username + "!"
        );
        session.send(response);

        return session;
    }

    private void handleProtocolMessage(ClientSession session, ChatMessage message) {
        try {
            switch (message.getType()) {
                case JOIN_ROOM_REQUEST:
                    handleJoinRoom(session, message);
                    break;
                case TEXT_MESSAGE:
                    handleTextMessage(session, message);
                    break;
                case PRIVATE_MESSAGE:
                    handlePrivateMessage(session, message);
                    break;
                case USER_LIST_REQUEST:
                    handleUserListRequest(session);
                    break;
                default:
                    sendError(session, "Unsupported message type: " + message.getType());
            }
        } catch (IOException e) {
            log("Error handling message from " + session.getUsername() +
                    ": " + e.getMessage());
        }
    }

    // ================ ROOMS & BROADCAST ================

    private ChatRoom getOrCreateRoom(String roomName) {
        return chatRooms.computeIfAbsent(roomName, ChatRoom::new);
    }

    private void handleJoinRoom(ClientSession session, ChatMessage msg) throws IOException {
        String roomName = msg.getRoom();
        if (roomName == null || roomName.isBlank()) {
            sendError(session, "Room name must not be empty.");
            return;
        }

        ChatRoom room = getOrCreateRoom(roomName);

        // Quitter l'ancienne room si besoin
        String previous = session.getCurrentRoom();
        if (previous != null && !previous.equals(roomName)) {
            ChatRoom oldRoom = chatRooms.get(previous);
            if (oldRoom != null) {
                oldRoom.leave(session);
            }
        }

        room.join(session);
        log("User " + session.getUsername() + " joined room " + roomName);

        // Message d'info aux membres
        ChatMessage info = new ChatMessage(
                MessageType.TEXT_MESSAGE,
                "1.0",
                java.time.Instant.now(),
                "server",
                null,
                roomName,
                session.getUsername() + " joined the room."
        );
        room.broadcast(info);
    }

    private void handleTextMessage(ClientSession session, ChatMessage msg) throws IOException {
        String roomName = msg.getRoom();
        if (roomName == null || roomName.isBlank()) {
            // Si pas précisé dans le message, on prend la room courante
            roomName = session.getCurrentRoom();
        }

        if (roomName == null) {
            sendError(session, "You are not in any room.");
            return;
        }

        ChatRoom room = chatRooms.get(roomName);
        if (room == null) {
            sendError(session, "Room does not exist: " + roomName);
            return;
        }

        ChatMessage broadcastMsg = new ChatMessage(
            msg.getType(), // TEXT_MESSAGE
            msg.getVersion() != null ? msg.getVersion() : "1.0",
            java.time.Instant.now(),
            session.getUsername(),
            null,
            roomName,
            msg.getContent()
        );
        room.broadcast(broadcastMsg);
    }

    private void handlePrivateMessage(ClientSession session, ChatMessage msg) throws IOException {
        String recipientName = msg.getRecipient();
        if (recipientName == null || recipientName.isBlank()) {
            sendError(session, "Recipient is required for private message.");
            return;
        }

        ClientSession target = activeSessions.get(recipientName);
        if (target == null) {
            sendError(session, "User not found: " + recipientName);
            return;
        }

        ChatMessage pm = new ChatMessage(
                MessageType.PRIVATE_MESSAGE,
                msg.getVersion() != null ? msg.getVersion() : "1.0",
                java.time.Instant.now(),
                session.getUsername(),
                recipientName,
                null,
                msg.getContent()
        );
        target.send(pm);
    }

    private void handleUserListRequest(ClientSession session) throws IOException {
        StringBuilder sb = new StringBuilder("Active users: ");
        boolean first = true;
        for (String user : activeSessions.keySet()) {
            if (!first) sb.append(", ");
            sb.append(user);
            first = false;
        }

        ChatMessage listMsg = new ChatMessage(
                MessageType.USER_LIST_REQUEST, // on réutilise ce type
                "1.0",
                java.time.Instant.now(),
                "server",
                session.getUsername(),
                null,
                sb.toString()
        );
        session.send(listMsg);
    }

    // ================ ERREURS ================

    private void sendError(DataOutputStream out, String message) throws IOException {
        ChatMessage errorMsg = new ChatMessage(
                MessageType.ERROR_RESPONSE,
                "1.0",
                java.time.Instant.now(),
                "server",
                null,
                null,
                message
        );
        byte[] data = errorMsg.toBytes();
        out.write(data);
        out.flush();
    }

    private void sendError(ClientSession session, String message) throws IOException {
        ChatMessage errorMsg = new ChatMessage(
                MessageType.ERROR_RESPONSE,
                "1.0",
                java.time.Instant.now(),
                "server",
                session.getUsername(),
                null,
                message
        );
        session.send(errorMsg);
    }

    // ================ MAIN ================

    public static void main(String[] args) {
        int port = 8444; // différent de 8443 pour ne pas gêner SSLTCPServer
        String keystorePath = "server.jks";
        String password = "password123";

        SecureChatServer server = new SecureChatServer(port, keystorePath, password);
        server.launch();
    }
}
