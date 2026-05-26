import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class InstanceInfo{
    private static final long HEARTBEAT_TIMEOUT_MS = 6000;
    String instanceId;
    String host;
    int port;
    long lastSeen;
    Set<EndpointInfo> endpoints;

    public InstanceInfo(String instanceId, String host, int port, long lastSeen) {
        this.instanceId = instanceId;
        this.host = host;
        this.port = port;
        this.lastSeen = lastSeen;
        this.endpoints = new HashSet<>();
    }

    public void setLastSeen(long lastSeen){
        this.lastSeen = lastSeen;
    }

    public long getLastSeen(){
        return lastSeen;
    }

    public String getInstanceId(){
        return instanceId;
    }

    public String getHost(){
        return host;
    }

    public int getPort(){
        return port;
    }

    public String getIdentity() {
        return instanceId + "|" + host + "|" + port;
    }

    public boolean isAlive(long now) {
        return now - lastSeen <= HEARTBEAT_TIMEOUT_MS;
    }

    public void setEndpoints(Set<EndpointInfo> endpoints) {
        this.endpoints = endpoints == null ? new HashSet<>() : new HashSet<>(endpoints);
    }

    public Set<EndpointInfo> getEndpoints() {
        return Collections.unmodifiableSet(endpoints);
    }

    public boolean supports(String method, String path) {
        for (EndpointInfo endpoint : endpoints) {
            if (endpoint.matches(method, path)) {
                return true;
            }
        }
        return false;
    }

    public static class EndpointInfo {
        private final String method;
        private final String path;

        public EndpointInfo(String method, String path) {
            this.method = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
            this.path = normalizePath(path);
        }

        public String getMethod() {
            return method;
        }

        public String getPath() {
            return path;
        }

        public boolean matches(String requestMethod, String requestPath) {
            if (!method.equalsIgnoreCase(requestMethod == null ? "" : requestMethod.trim())) {
                return false;
            }

            String normalizedRequestPath = normalizePath(requestPath);
            String[] patternParts = path.split("/");
            String[] requestParts = normalizedRequestPath.split("/");

            if (patternParts.length != requestParts.length) {
                return false;
            }

            for (int i = 0; i < patternParts.length; i++) {
                if (patternParts[i].equals(requestParts[i])) {
                    continue;
                }
                if (patternParts[i].startsWith("{") && patternParts[i].endsWith("}")) {
                    continue;
                }
                return false;
            }
            return true;
        }

        private static String normalizePath(String path) {
            if (path == null || path.isBlank()) {
                return "/";
            }

            int queryStart = path.indexOf("?");
            String normalized = queryStart >= 0 ? path.substring(0, queryStart) : path;
            normalized = normalized.trim();

            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            while (normalized.endsWith("/") && normalized.length() > 1) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }

            return normalized.isEmpty() || "/".equals(normalized) ? "/" : "/" + normalized;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EndpointInfo that)) return false;
            return method.equals(that.method) && path.equals(that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, path);
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }
}