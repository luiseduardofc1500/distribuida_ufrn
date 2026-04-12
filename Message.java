import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Message(
    MessageType type,
    ComponentType componentType,
    String instanceId,
    String host,
    int port,
    String requestId,
    String payload,
    String timestamp
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public String toJson() {
        return String.format(
            "{\"type\":\"%s\",\"componentType\":\"%s\",\"instanceId\":\"%s\",\"host\":\"%s\",\"port\":%d,\"requestId\":\"%s\",\"payload\":\"%s\",\"timestamp\":\"%s\"}",
            type.getValue(), componentType.getValue(), escape(instanceId), escape(host), 
            port, escape(requestId), escape(payload), escape(timestamp)
        );
    }

    public static Message fromJson(String json) {
        return new Message(
            MessageType.fromString(extract(json, "type")),
            ComponentType.fromString(extract(json, "componentType")),
            extract(json, "instanceId"),
            extract(json, "host"),
            parsePort(extract(json, "port")),
            extract(json, "requestId"),
            extract(json, "payload"),
            extract(json, "timestamp")
        );
    }

    private static String extract(String json, String key) {
        Pattern pattern = Pattern.compile("(?s)\"" + key + "\":\\s*\"?([^\"]*)\"?");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static int parsePort(String portStr) {
        try {
            return portStr.isEmpty() ? 0 : Integer.parseInt(portStr.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        
        if (value.contains("\"") || value.contains("\n")) {
            throw new IllegalArgumentException("Invalid characters in value: quotes and newlines are not allowed");
        }
        
        return value;
    }
}