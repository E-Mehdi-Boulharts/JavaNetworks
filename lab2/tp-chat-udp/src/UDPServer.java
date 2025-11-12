import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class UDPServer {
    private static final int DEFAULT_PORT = 8080;       // port par défaut demandé dans l’énoncé (exemple)
    private static final int MAX_BYTES = 1024;           // taille max du message encodé UTF-8
    private final int port;
    private boolean running = false;
    private DatagramSocket socket;

    // Constructeur avec port
    public UDPServer(int port) {
        this.port = port;
    }

    // Constructeur par défaut
    public UDPServer() {
        this(DEFAULT_PORT);
    }

    // Démarrage (sans Threads pour l’instant)
    public void launch() throws IOException {
        socket = new DatagramSocket(port);
        running = true;
        System.out.println(this.toString());

        byte[] buffer = new byte[MAX_BYTES];
        while (running) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet); // bloquant

            int len = packet.getLength();
            InetAddress clientAddr = packet.getAddress();
            int clientPort = packet.getPort();

            // Décoder uniquement les 'len' premiers octets
            String msg = new String(packet.getData(), 0, len, StandardCharsets.UTF_8);

            // Affichage demandé : chaîne précédée de l’adresse du client
            System.out.printf("[%s:%d] %s%n", clientAddr.getHostAddress(), clientPort, msg);
        }
    }

    @Override
    public String toString() {
        return "UDPServer{port=" + port + ", running=" + running + "}";
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Port invalide, utilisation du port par défaut " + DEFAULT_PORT);
            }
        }
        UDPServer server = new UDPServer(port);
        try {
            server.launch();
        } catch (IOException e) {
            System.err.println("Erreur serveur: " + e.getMessage());
        }
    }
}
