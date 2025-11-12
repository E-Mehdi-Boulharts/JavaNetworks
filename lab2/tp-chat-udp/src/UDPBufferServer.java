import java.net.*;

public class UDPBufferServer {
    public static void main(String[] args) throws Exception {
        int port = (args.length>=1)? Integer.parseInt(args[0]) : 8082;
        int bufSize = (args.length>=2)? Integer.parseInt(args[1]) : 2048;
        try (DatagramSocket s = new DatagramSocket(port)) {
            System.out.printf("UDPBufferServer{port=%d, buffer=%d}%n", port, bufSize);
            byte[] buf = new byte[bufSize];
            while (true) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                s.receive(p);
                int len = p.getLength();
                boolean maybeTruncated = (len == bufSize); // si datagram > buffer -> tronqué à buffer
                System.out.printf("recv len=%d (buffer=%d)%s from %s%n",
                        len, bufSize, maybeTruncated?" [POSSIBLE TRUNCATION]":"", p.getSocketAddress());
            }
        }
    }
}