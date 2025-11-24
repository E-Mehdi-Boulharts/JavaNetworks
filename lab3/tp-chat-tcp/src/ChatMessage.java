public class ChatMessage {

    public enum Type { CHAT, SYSTEM, ERROR }

    public final Type type;
    public final int seq;
    public final int length;
    public final String payload;

    public ChatMessage(Type type, int seq, String payload) {
        this.type = type;
        this.seq = seq;
        this.payload = (payload != null) ? payload : "";
        this.length = this.payload.length();
    }

    /** Helper for normal chat messages. */
    public static ChatMessage chat(int seq, String payload) {
        return new ChatMessage(Type.CHAT, seq, payload);
    }

    /** Encode to wire format: TYPE|SEQ|LEN|PAYLOAD */
    public static String encode(ChatMessage msg) {
        return msg.type + "|" + msg.seq + "|" + msg.length + "|" + msg.payload;
    }

    /** Decode from wire format and validate headers. */
    public static ChatMessage decode(String wire) throws ProtocolException {
        if (wire == null) {
            throw new ProtocolException("Null wire message");
        }

        // We use limit 4 to keep the rest as payload even if it contains '|'
        String[] parts = wire.split("\\|", 4);
        if (parts.length < 4) {
            throw new ProtocolException("Invalid header (expected 4 fields): " + wire);
        }

        Type type;
        try {
            type = Type.valueOf(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new ProtocolException("Unknown message type: " + parts[0], e);
        }

        int seq;
        int len;
        try {
            seq = Integer.parseInt(parts[1]);
            len = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            throw new ProtocolException("Invalid sequence or length in: " + wire, e);
        }

        String payload = parts[3];
        if (payload.length() != len) {
            throw new ProtocolException("Length mismatch: header=" + len +
                                        ", actual=" + payload.length());
        }

        return new ChatMessage(type, seq, payload);
    }
}

/**
 * Custom exception used for protocol errors (3.9.3 Advanced Error Handling).
 */
class ProtocolException extends Exception {
    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}