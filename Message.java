import java.io.Serializable;

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

    public String toHttpFormat() {
        StringBuilder sb = new StringBuilder();
        
        switch (type) {
            case GET -> sb.append("GET /").append(componentType.getValue().toLowerCase()).append(" HTTP/1.1\r\n");
            case POST -> sb.append("POST /").append(componentType.getValue().toLowerCase()).append(" HTTP/1.1\r\n");
            case RESPONSE -> sb.append("HTTP/1.1 200 OK\r\n");
            case ERROR -> {
                if ("NO_INSTANCE".equals(payload)) {
                    sb.append("HTTP/1.1 503 Service Unavailable\r\n");
                } else if ("BAD_REQUEST".equals(payload)) {
                    sb.append("HTTP/1.1 400 Bad Request\r\n");
                } else {
                    sb.append("HTTP/1.1 500 Internal Server Error\r\n");
                }
            }
            case REGISTER -> sb.append("POST /register HTTP/1.1\r\n");
            case HEARTBEAT -> sb.append("POST /heartbeat HTTP/1.1\r\n");
            default -> sb.append("UNKNOWN / HTTP/1.1\r\n");
        }

        sb.append("Component-Type: ").append(componentType.getValue()).append("\r\n");
        sb.append("Instance-Id: ").append(instanceId).append("\r\n");
        sb.append("Host: ").append(host).append("\r\n");
        sb.append("Port: ").append(port).append("\r\n");
        sb.append("Request-Id: ").append(requestId).append("\r\n");
        sb.append("Timestamp: ").append(timestamp).append("\r\n");
        
        String safePayload = payload != null ? payload : "";
        sb.append("Content-Length: ").append(safePayload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).append("\r\n");
        sb.append("\r\n");
        sb.append(safePayload);

        return sb.toString();
    }

    public static Message fromHttpFormat(String http) {
        int separatorIndex = http.indexOf("\r\n\r\n");
        String headerPart = separatorIndex >= 0 ? http.substring(0, separatorIndex) : http;
        String bodyPart = separatorIndex >= 0 ? http.substring(separatorIndex + 4) : "";

        String[] headers = headerPart.split("\r\n");
        String firstLine = headers.length > 0 ? headers[0] : "";

        MessageType type = parseMessageType(firstLine);
        String componentTypeStr = extractHeader(headers, "Component-Type");
        String instanceId = extractHeader(headers, "Instance-Id");
        String host = extractHeader(headers, "Host");
        String portStr = extractHeader(headers, "Port");
        String requestId = extractHeader(headers, "Request-Id");
        String timestamp = extractHeader(headers, "Timestamp");
        String contentLengthStr = extractHeader(headers, "Content-Length");

        String payload = readPayload(bodyPart, contentLengthStr);
        ComponentType component = ComponentType.fromString(componentTypeStr);
        if (component == ComponentType.UNKNOWN) {
            type = MessageType.UNKNOWN;
        }

        return new Message(
            type,
            component,
            instanceId,
            host,
            parsePort(portStr),
            requestId,
            payload,
            timestamp
        );
    }

    private static String extractHeader(String[] headers, String key) {
        String prefix = key + ":";
        for (String header : headers) {
            if (header.toLowerCase().startsWith(prefix.toLowerCase())) {
                return header.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static int parsePort(String portStr) {
        try {
            return portStr.isEmpty() ? 0 : Integer.parseInt(portStr.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static MessageType parseMessageType(String startLine) {
        if (startLine == null || startLine.isEmpty()) {
            return MessageType.UNKNOWN;
        }
        if (startLine.startsWith("GET ")) {
            return MessageType.GET;
        }
        if (startLine.startsWith("POST ")) {
            String path = extractPath(startLine);
            if ("/register".equalsIgnoreCase(path)) {
                return MessageType.REGISTER;
            }
            if ("/heartbeat".equalsIgnoreCase(path)) {
                return MessageType.HEARTBEAT;
            }
            return MessageType.POST;
        }
        if (startLine.startsWith("HTTP/1.1 200")) {
            return MessageType.RESPONSE;
        }
        if (startLine.startsWith("HTTP/1.1")) {
            return MessageType.ERROR;
        }
        return MessageType.UNKNOWN;
    }

    private static String extractPath(String startLine) {
        String[] parts = startLine.split(" ");
        if (parts.length < 2) {
            return "";
        }
        String path = parts[1];
        int queryIndex = path.indexOf('?');
        return queryIndex >= 0 ? path.substring(0, queryIndex) : path;
    }

    private static String readPayload(String bodyPart, String contentLengthStr) {
        if (bodyPart == null) {
            return "";
        }
        int contentLength = parseContentLength(contentLengthStr);
        if (contentLength < 0) {
            return bodyPart;
        }
        byte[] bodyBytes = bodyPart.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int safeLength = Math.min(contentLength, bodyBytes.length);
        if (safeLength <= 0) {
            return "";
        }
        return new String(bodyBytes, 0, safeLength, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static int parseContentLength(String contentLengthStr) {
        if (contentLengthStr == null || contentLengthStr.isEmpty()) {
            return -1;
        }
        String digits = contentLengthStr.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}