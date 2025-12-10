import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class ChatMessage {

    private MessageType type;
    private String version;
    private Instant timestamp;
    private String sender;
    private String recipient;
    private String room;
    private String content;

    public ChatMessage(MessageType type,
                       String version,
                       Instant timestamp,
                       String sender,
                       String recipient,
                       String room,
                       String content) {
        this.type = type;
        this.version = version;
        this.timestamp = timestamp;
        this.sender = sender;
        this.recipient = recipient;
        this.room = room;
        this.content = content;
    }

    public MessageType getType() { return type; }
    public String getVersion() { return version; }
    public Instant getTimestamp() { return timestamp; }
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public String getRoom() { return room; }
    public String getContent() { return content; }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "type=" + type +
                ", version='" + version + '\'' +
                ", timestamp=" + timestamp +
                ", sender='" + sender + '\'' +
                ", recipient='" + recipient + '\'' +
                ", room='" + room + '\'' +
                ", content='" + content + '\'' +
                '}';
    }

    // ========= 3.2.3 – SERIALIZATION / DESERIALIZATION =========

    /** Convert this ChatMessage into [4 bytes length][JSON UTF-8] */
    public byte[] toBytes() throws IOException {
        String json = toJsonString();
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        int len = jsonBytes.length;

        ByteArrayOutputStream baos = new ByteArrayOutputStream(4 + len);
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(len);         // length prefix
        dos.write(jsonBytes);      // JSON body
        dos.flush();
        return baos.toByteArray();
    }

    /** Reconstruct ChatMessage from [4 bytes length][JSON UTF-8] */
    public static ChatMessage fromBytes(byte[] data) throws IOException {
        if (data.length < 4) {
            throw new IOException("Invalid message: not enough bytes for length header");
        }

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        int len = dis.readInt();

        // 3.2.3 – message validation: length verification
        if (len < 0 || len > data.length - 4) {
            throw new IOException("Invalid message length: " + len);
        }

        byte[] jsonBytes = new byte[len];
        int read = dis.read(jsonBytes);
        if (read != len) {
            throw new IOException("Could not read full JSON body");
        }

        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        return fromJsonString(json);
    }

    // ========= JSON helper methods (très simples, suffisent pour le TP) =========

    private String toJsonString() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendJsonField(sb, "type", type.name(), true);
        appendJsonField(sb, "version", version, true);
        appendJsonField(sb, "sender", sender, true);
        appendJsonField(sb, "recipient", recipient, true);
        appendJsonField(sb, "room", room, true);
        appendJsonField(sb, "content", content, true);
        sb.append("\"timestamp\":")
          .append(timestamp != null ? timestamp.getEpochSecond() : "null");
        sb.append('}');
        return sb.toString();
    }

    private static void appendJsonField(StringBuilder sb,
                                        String name,
                                        String value,
                                        boolean withComma) {
        sb.append('"').append(name).append('"').append(':');
        if (value == null) {
            sb.append("null");
        } else {
            sb.append('"').append(escapeJson(value)).append('"');
        }
        if (withComma) {
            sb.append(',');
        }
    }

    private static String escapeJson(String s) {
        // Échappement minimal pour ce TP
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static ChatMessage fromJsonString(String json) {
        String typeStr   = extractStringField(json, "type");
        String version   = extractStringField(json, "version");
        String sender    = extractStringField(json, "sender");
        String recipient = extractStringField(json, "recipient");
        String room      = extractStringField(json, "room");
        String content   = extractStringField(json, "content");
        Long ts          = extractLongField(json, "timestamp");

        MessageType type = MessageType.valueOf(typeStr);
        Instant timestamp = (ts != null ? Instant.ofEpochSecond(ts) : null);

        return new ChatMessage(type, version, timestamp, sender, recipient, room, content);
    }

    private static String extractStringField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return null;

        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length()) return null;

        char c = json.charAt(i);
        if (c == 'n') {
            // assume "null"
            return null;
        } else if (c == '"') {
            int start = i + 1;
            StringBuilder sb = new StringBuilder();
            boolean escape = false;
            for (int j = start; j < json.length(); j++) {
                char ch = json.charAt(j);
                if (escape) {
                    sb.append(ch);
                    escape = false;
                } else if (ch == '\\') {
                    escape = true;
                } else if (ch == '"') {
                    return sb.toString();
                } else {
                    sb.append(ch);
                }
            }
        }
        return null;
    }

    private static Long extractLongField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return null;

        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        int start = i;
        while (i < json.length() &&
               (Character.isDigit(json.charAt(i)) || json.charAt(i) == '-')) {
            i++;
        }
        if (start == i) return null;

        String numStr = json.substring(start, i);
        try {
            return Long.parseLong(numStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Petit main de test pour 4.2.1 (round-trip)
    public static void main(String[] args) throws Exception {
        ChatMessage original = new ChatMessage(
                MessageType.TEXT_MESSAGE,
                "1.0",
                Instant.now(),
                "alice",
                null,
                "general",
                "Hello JSON over TLS"
        );

        byte[] data = original.toBytes();
        ChatMessage decoded = ChatMessage.fromBytes(data);

        System.out.println("Original: " + original);
        System.out.println("Decoded : " + decoded);

// content of message for testing unicode and special char
        // Unicode: Hello ! I'm مهدي 😊 ; Special char: ]}^-_*$£€¨ù%)è/ê`œ|æ?∞¿Û¡>à=é
// For testing the size

        // // Test with a large message (maximum size handling)
        // StringBuilder sb = new StringBuilder();
        // for (int i = 0; i < 8000; i++) {   // ou proche de ta limite si tu as un MAX_LENGTH
        //     sb.append('A');
        // }
        // String bigContent = sb.toString();

        // ChatMessage bigMsg = new ChatMessage(
        //         MessageType.TEXT_MESSAGE,
        //         "1.0",
        //         java.time.Instant.now(),
        //         "alice",
        //         null,
        //         "general",
        //         bigContent
        // );

        // byte[] bigData = bigMsg.toBytes();
        // ChatMessage bigDecoded = ChatMessage.fromBytes(bigData);

        // System.out.println("Big message serialized length = " + bigData.length + " bytes");
        // System.out.println("Big message decoded OK, content length = " + bigDecoded.getContent().length());
    }
}
