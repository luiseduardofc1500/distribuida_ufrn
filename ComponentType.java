public enum ComponentType {
    COMPRAR_INGRESSO("COMPRAR_INGRESSO"),
    INGRESSOS_DISPONIVEIS("INGRESSOS_DISPONIVEIS"),
    DATABASE("DATABASE"),
    GATEWAY("GATEWAY"),
    UNKNOWN("UNKNOWN");

    private final String value;

    ComponentType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ComponentType fromString(String text) {
        for (ComponentType type : ComponentType.values()) {
            if (type.value.equalsIgnoreCase(text)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}