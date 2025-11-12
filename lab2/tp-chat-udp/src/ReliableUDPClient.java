import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

public class ReliableUDPClient {
    private static final int MAX_BYTES = 1024;
    private static final byte TYPE_DATA = 'D';
    private static final byte TYPE_ACK  = 'A';
    private static final int ACK_TIMEOUT_MS = 200;
    private static final int MAX_RETRIES = 5;

    private static void putInt(byte[] b, int off, int v) {
        ByteBuffer.wrap(b, off, 4).putInt(v);
    }
    private static int getInt(byte[] b, int off) {
        return ByteBuffer.wrap(b, off, 4).getInt();
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java ReliableUDPClient <host> <port> [count]");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        Integer count = (args.length>=3)? Integer.valueOf(args[2]) : null;

        try (DatagramSocket socket = new DatagramSocket();
             BufferedReader in = (count==null)
                     ? new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
                     : null) {
            socket.setSoTimeout(ACK_TIMEOUT_MS);

            long sent=0, acked=0, retries=0, failed=0;
            int seq = 1;

            if (count == null) {
                System.out.println("Type lines (EOF to quit).");
                String line;
                while ((line = in.readLine()) != null) {
                    if (sendWithRetry(socket, host, port, seq, line)) {
                        acked++;
                    } else {
                        failed++;
                    }
                    sent++; seq++;
                }
            } else {
                for (int i=0; i<count; i++, seq++, sent++) {
                    String payload = "msg-"+seq+"-"+ThreadLocalRandom.current().nextInt(1000);
                    if (sendWithRetry(socket, host, port, seq, payload)) acked++; else failed++;
                }
            }

            System.out.printf("Summary: sent=%d, acked=%d, failed(no-ACK)=%d, retries≈%d, loss≈%.2f%%%n",
                    sent, acked, failed, retries, (sent==0?0.0: (100.0*failed/sent)));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean sendWithRetry(DatagramSocket socket, String host, int port, int seq, String payload)
            throws IOException {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        // header 5 octets -> garde la place
        int maxPayload = MAX_BYTES - 5;
        if (data.length > maxPayload) {
            byte[] cut = new byte[maxPayload];
            System.arraycopy(data, 0, cut, 0, maxPayload);
            data = cut;
            System.out.println("(truncated client payload to "+maxPayload+" bytes)");
        }
        byte[] pkt = new byte[5 + data.length];
        pkt[0] = 'D'; putInt(pkt, 1, seq);
        System.arraycopy(data, 0, pkt, 5, data.length);

        InetAddress addr = InetAddress.getByName(host);

        for (int attempt=1; attempt<=MAX_RETRIES; attempt++) {
            // envoie
            socket.send(new DatagramPacket(pkt, pkt.length, addr, port));
            // attend ACK
            try {
                byte[] ackBuf = new byte[5];
                DatagramPacket ack = new DatagramPacket(ackBuf, ackBuf.length);
                socket.receive(ack);
                if (ackBuf[0] == 'A' && getInt(ackBuf, 1) == seq) {
                    return true; // success
                }
            } catch (SocketTimeoutException e) {
                // retry
            }
        }
        return false; // pas d'ACK
    }
}
