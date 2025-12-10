import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ChatRoom {

    private final String name;
    private final Set<ClientSession> members =
            Collections.synchronizedSet(new HashSet<>());

    public ChatRoom(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void join(ClientSession session) {
        members.add(session);
        session.setCurrentRoom(name);
    }

    public void leave(ClientSession session) {
        members.remove(session);
        if (name.equals(session.getCurrentRoom())) {
            session.setCurrentRoom(null);
        }
    }

    public void broadcast(ChatMessage message) {
        synchronized (members) {
            for (ClientSession s : members) {
                try {
                    s.send(message);
                } catch (IOException e) {
                    System.err.println("Failed to send to " + s.getUsername() + ": " + e.getMessage());
                }
            }
        }
    }
}
