import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ReliableUDPServer {
    private static final int DEFAULT_PORT = 8081;
    private static final int MAX_BYTES = 1024; // payload+header <= 1024
    private static final byte TYPE_DATA = 'D';
    private static final byte TYPE_ACK  = 'A';

    private final int port;
    private final double dropRate; // probabilité de "jeter" un paquet (simulation)
    private DatagramSocket socket;
    private final Random rnd = new Random();

    // mémorise le dernier seq reçu par client (anti-dup)
    private final Map<SocketAddress, Integer> lastSeqByClient = new HashMap<>();

    public ReliableUDPServer(int port, double dropRate) {
        this.port = port;
        this.dropRate = dropRate;
    }
    public ReliableUDPServer() { this(DEFAULT_PORT, 0.0); }

    private static int getInt(byte[] b, int off) {
        return ByteBuffer.wrap(b, off, 4).getInt();
    }
    private static void putInt(byte[] b, int off, int v) {
        ByteBuffer.wrap(b, off, 4).putInt(v);
    }

    public void launch() throws IOException {
        socket = new DatagramSocket(port);
        System.out.printf("ReliableUDPServer{port=%d, dropRate=%.2f}%n", port, dropRate);

        byte[] buf = new byte[MAX_BYTES];
        long totalReceived = 0, duplicates = 0, droppedSimulated = 0;

        while (true) {
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            socket.receive(p);
            totalReceived++;

            // Décodage en-tête: [type(1) | seq(4) | payload...]
            byte type = p.getData()[0];
            if (type != TYPE_DATA) continue;
            int seq = getInt(p.getData(), 1);
            SocketAddress client = p.getSocketAddress();

            // Simulation de perte (pour expérience)
            if (rnd.nextDouble() < dropRate) {
                droppedSimulated++;
                System.out.printf("(sim drop) from %s seq=%d%n", client, seq);
                continue; // on "perd" le paquet => pas d'ACK
            }

            Integer last = lastSeqByClient.get(client);
            boolean isDup = (last != null && seq <= last);
            if (isDup) duplicates++;

            int payloadLen = p.getLength() - 5; // 1(type)+4(seq)
            String msg = new String(p.getData(), 5, Math.max(0, payloadLen), StandardCharsets.UTF_8);

            // Affiche et ACK
            System.out.printf("[%s] seq=%d%s msg=%s%n", client, seq, (isDup?" (dup)":""),
                    msg);

            // envoi ACK: [TYPE_ACK | seq]
            byte[] ack = new byte[5];
            ack[0] = TYPE_ACK;
            putInt(ack, 1, seq);
            DatagramPacket ackPkt = new DatagramPacket(ack, ack.length, p.getAddress(), p.getPort());
            socket.send(ackPkt);

            // met à jour seq
            if (last == null || seq > last) lastSeqByClient.put(client, seq);

            // stats compactes (tape 'CTRL+C' pour arrêter)
            if (seq % 50 == 0) {
                System.out.printf("Stats: received=%d, duplicates=%d, simDropped=%d%n",
                        totalReceived, duplicates, droppedSimulated);
            }
        }
    }

    public static void main(String[] args) {
        int port = (args.length>=1)? Integer.parseInt(args[0]) : DEFAULT_PORT;
        double drop = (args.length>=2)? Double.parseDouble(args[1]) : 0.0; // ex: 0.2
        try {
            new ReliableUDPServer(port, drop).launch();
        } catch (Exception e) { e.printStackTrace(); }
    }
}
