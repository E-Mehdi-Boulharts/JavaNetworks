import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ConnectionThread extends Thread {

    private final Socket clientSocket;
    private final int clientId;

    public ConnectionThread(Socket clientSocket, int clientId) {
        this.clientSocket = clientSocket;
        this.clientId = clientId;

        // Nom de thread lisible dans les logs (demande du TP)
        this.setName("ClientHandler-" + clientId);
    }

    @Override
    public void run() {
        String clientIp = clientSocket.getInetAddress().getHostAddress();
        System.out.println("[" + new java.util.Date() + "] Client " + clientId
                + " connected from " + clientIp);

        BufferedReader in = null;
        PrintWriter out = null;

        try {
            in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(
                    new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);

            // Message de bienvenue demandé dans le TP4
            out.println("Welcome! You are client #" + clientId);

            String line;
            while ((line = in.readLine()) != null) {
                // Gestion de la commande "quit" côté serveur (TP4)
                if ("quit".equalsIgnoreCase(line)) {
                    out.println("Goodbye client #" + clientId);
                    break;
                }

                String tagged = "[#" + clientId + " " + clientIp + "] " + line;

                // Affiche sur la console serveur comme dans ton TP3
                System.out.println(tagged);

                // Ici on pourrait appeler addToHistory(tagged) si on lui avait accès
                // Mais pour le TP4, l'historique n'est pas exigé, donc on simplifie.

                // Echo vers le client
                out.println(tagged);
            }

        } catch (IOException e) {
            System.err.println("Client " + clientId + " error: " + e.getMessage());
        } finally {
            cleanup(in, out);
        }
    }

    private void cleanup(BufferedReader in, PrintWriter out) {
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {}

        if (out != null) {
            out.close();
        }

        try {
            clientSocket.close();
        } catch (IOException ignored) {}

        String clientIp = clientSocket.getInetAddress().getHostAddress();
        System.out.println("[" + new java.util.Date() + "] Client " + clientId
                + " (" + clientIp + ") disconnected");
    }
}
