import javax.net.ssl.SSLSocket;
import java.io.DataOutputStream;
import java.io.IOException;

public class ClientSession {

    private final String username;
    private final SSLSocket socket;
    private final DataOutputStream out;
    private String currentRoom;

    public ClientSession(String username, SSLSocket socket, DataOutputStream out) {
        this.username = username;
        this.socket = socket;
        this.out = out;
    }

    public String getUsername() {
        return username;
    }

    public SSLSocket getSocket() {
        return socket;
    }

    public String getCurrentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(String currentRoom) {
        this.currentRoom = currentRoom;
    }

    public void send(ChatMessage message) throws IOException {
        byte[] data = message.toBytes();
        out.write(data);
        out.flush();
    }
}
