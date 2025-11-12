import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class TCPClient {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java TCPClient <host> <port>");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);

        try (Socket sock = new Socket(host, port);
             BufferedReader stdin = new BufferedReader(
                 new InputStreamReader(System.in, StandardCharsets.UTF_8));
             BufferedReader in = new BufferedReader(
                 new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                 new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8), true)) {

            System.out.println("Type lines (end with CTRL+D).");
            String line;
            while ((line = stdin.readLine()) != null) {
                out.println(line);           // envoi
                String echo = in.readLine(); // lecture réponse
                if (echo == null) break;     // serveur a fermé
                System.out.println(echo);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
