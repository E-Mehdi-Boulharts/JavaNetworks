import java.net.*;
import java.nio.charset.StandardCharsets;

public class UDPMulticastSender {
    public static void main(String[] args) throws Exception {
        String groupIp = (args.length>=1)? args[0] : "230.0.0.1";
        int port = (args.length>=2)? Integer.parseInt(args[1]) : 8888;
        String msg  = (args.length>=3)? args[2] : "hello-multicast";

        InetAddress group = InetAddress.getByName(groupIp);
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket s = new DatagramSocket()) {
            DatagramPacket p = new DatagramPacket(data, data.length, group, port);
            s.send(p);
            System.out.printf("Sent multicast '%s' to %s:%d%n", msg, groupIp, port);
        }
    }
}
