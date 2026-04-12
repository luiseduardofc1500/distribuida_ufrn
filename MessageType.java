public enum MessageType {
    REGISTER("REGISTER"),
    HEARTBEAT("HEARTBEAT"),
    REQUEST("REQUEST"),
    RESPONSE("RESPONSE"),
    UNKNOWN("UNKNOWN");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MessageType fromString(String text) {
        for (MessageType type : MessageType.values()) {
            if (type.value.equalsIgnoreCase(text)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}