import java.net.*;
import java.nio.charset.StandardCharsets;

public class UDPMulticastReceiver {
    public static void main(String[] args) throws Exception {
        String groupIp = (args.length>=1)? args[0] : "230.0.0.1";
        int port = (args.length>=2)? Integer.parseInt(args[1]) : 8888;

        InetAddress group = InetAddress.getByName(groupIp);
        try (MulticastSocket ms = new MulticastSocket(port)) {
            // API simple (dépréciée mais pratique pour TP)
            ms.joinGroup(group);
            System.out.printf("Joined multicast group %s:%d%n", groupIp, port);

            byte[] buf = new byte[1024];
            while (true) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                ms.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                System.out.printf("[multicast recv from %s] %s%n", p.getSocketAddress(), msg);
            }
        }
    }
}