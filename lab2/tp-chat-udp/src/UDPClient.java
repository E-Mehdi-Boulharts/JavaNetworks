import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class UDPClient {
    private static final int MAX_BYTES = 1024;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java UDPClient <host> <port>");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        try (DatagramSocket socket = new DatagramSocket();
             BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            System.out.println("Tape des lignes et valide. Fin avec Ctrl+D (mac/linux) ou Ctrl+Z puis Entrée (Windows).");

            String line;
            while ((line = in.readLine()) != null) {
                byte[] data = line.getBytes(StandardCharsets.UTF_8);
                if (data.length > MAX_BYTES) {
                    // Troncature côté client pour respecter la contrainte côté serveur (encodage UTF-8)
                    data = Arrays.copyOf(data, MAX_BYTES);
                    System.out.println("(Alerte) Message tronqué à 1024 octets.");
                }

                InetAddress addr = InetAddress.getByName(host);
                DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
                socket.send(packet);
            }
        } catch (IOException e) {
            System.err.println("Erreur client: " + e.getMessage());
        }
    }
}
