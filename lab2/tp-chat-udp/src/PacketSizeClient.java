import java.net.*;
import java.util.Arrays;

public class PacketSizeClient {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: java PacketSizeClient <host> <port> <bytes>");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        int bytes = Integer.parseInt(args[2]); // ex: 64, 2048, 65507, 70000
        byte[] data = new byte[bytes];
        Arrays.fill(data, (byte) 'A');
        try (DatagramSocket s = new DatagramSocket()) {
            DatagramPacket p = new DatagramPacket(data, data.length, InetAddress.getByName(host), port);
            s.send(p);
            System.out.printf("sent %d bytes to %s:%d%n", bytes, host, port);
        } catch (IllegalArgumentException iae) {
            System.err.println("Error: payload too large for UDP (limit ~65507 bytes).");
            iae.printStackTrace();
        }
    }
}