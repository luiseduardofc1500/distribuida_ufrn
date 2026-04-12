import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class GatewayUDP {

    private static final int GATEWAY_PORT = 9000;
    private static final long TIMEOUT = 15000;

    private static final Map<ComponentType, List<InstanceInfo>> registry = new ConcurrentHashMap<>();
    private static final Map<ComponentType, AtomicInteger> roundRobinIndex = new ConcurrentHashMap<>();
    private static final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    private static final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static DatagramSocket socket;

    static class PendingRequest {
        ClientInfo client;
        long timestamp;

        PendingRequest(ClientInfo client, long timestamp) {
            this.client = client;
            this.timestamp = timestamp;
        }
    }

    public static void main(String[] args) throws Exception {
        socket = new DatagramSocket(GATEWAY_PORT);
        System.out.println("Gateway rodando na porta " + GATEWAY_PORT);

        startCleanup();

        while (true) {
            byte[] buf = new byte[4096];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);

            pool.submit(() -> handle(packet));
        }
    }

    private static void handle(DatagramPacket packet) {
        try {
            String json = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            Message msg = Message.fromHttpFormat(json);

            if (msg.type() == MessageType.UNKNOWN) {
                sendBadRequest(packet, msg.requestId() != null ? msg.requestId() : "unknown", System.currentTimeMillis());
                return;
            }

            switch (msg.type()) {
                case REGISTER, HEARTBEAT -> handleManagement(msg, packet);
                case GET, POST-> handleRequest(msg, packet);
                case RESPONSE, ERROR -> handleResponse(msg);
            }

        } catch (Exception e) {
            System.err.println("Erro processando pacote: " + e.getMessage());
            try {
                sendBadRequest(packet, "unknown", System.currentTimeMillis());
            } catch (Exception sendEx) {
                System.err.println("Erro ao tentar enviar BAD_REQUEST: " + sendEx.getMessage());
            }
        }
    }

    
    private static void handleManagement(Message msg, DatagramPacket packet) {
        long now = System.currentTimeMillis();
        ComponentType component = msg.componentType();
        String id = msg.instanceId();
        String host = msg.host();
        int port = msg.port();
        String identity = id + "|" + host + "|" + port;

        List<InstanceInfo> list = registry.computeIfAbsent(component, key -> new CopyOnWriteArrayList<>());

        InstanceInfo existing = list.stream().filter(instance ->  instance.getIdentity()
                                                            .equals(identity))
                                                            .findFirst()
                                                            .orElse(null);

        if (existing == null) {
            System.out.println("[NEW_INSTANCE] Adicionando: " + identity + " (" + component + ")");
            list.add(new InstanceInfo(id, host, port, now));
            roundRobinIndex.putIfAbsent(component, new AtomicInteger(0));
        } else {
            existing.setLastSeen(now);
            if (msg.type() == MessageType.HEARTBEAT) {
                System.out.println("[HEARTBEAT] Recebido pulso de vida de " + identity + " (" + component + ")");
            } else {
                System.out.println("[REGISTER] Atualizando registro de: " + identity + " (" + component + ")");
            }
        }

        if (msg.type() == MessageType.REGISTER) {
            try {
                Message successResponse = new Message(MessageType.RESPONSE, component, id, "localhost", GATEWAY_PORT, "REGISTER", "OK", String.valueOf(now));
                byte[] data = successResponse.toHttpFormat().getBytes(StandardCharsets.UTF_8);
                socket.send(new DatagramPacket(data, data.length, packet.getAddress(), packet.getPort()));
            } catch (Exception e) {
                System.err.println("Erro ao enviar resposta de sucesso: " + e.getMessage());
            }
        }
    }

    private static void handleRequest(Message msg, DatagramPacket packet) throws Exception {
        long now = System.currentTimeMillis();
        ComponentType component = msg.componentType();

        List<InstanceInfo> list = registry.get(component);

        if (list == null || list.isEmpty()) {
            sendError(packet, msg.requestId(), now);
            return;
        }

        List<InstanceInfo> alive = list.stream()
                .filter(instance -> instance.isAlive(now))
                .toList();

        if (alive.isEmpty()) {
            sendError(packet, msg.requestId(), now);
            return;
        }

        AtomicInteger index = roundRobinIndex.get(component);
        int currentIndex = index.getAndIncrement();

        InstanceInfo target = alive.get(Math.abs(currentIndex) % alive.size());

        System.out.println("[FORWARD] " + msg.requestId() + " -> " + target.getIdentity());

        pendingRequests.put(msg.requestId(),
                new PendingRequest(
                        new ClientInfo(packet.getAddress().getHostAddress(), packet.getPort()),
                        now
                ));

        byte[] data = msg.toHttpFormat().getBytes(StandardCharsets.UTF_8);

        socket.send(new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName(target.getHost()),
                target.getPort()
        ));
    }

 
    private static void handleResponse(Message msg) throws Exception {
        PendingRequest pendingReq = pendingRequests.remove(msg.requestId());

        if (pendingReq == null) return;

        byte[] data = msg.toHttpFormat().getBytes(StandardCharsets.UTF_8);

        socket.send(new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName(pendingReq.client.getHost()),
                pendingReq.client.getPort()
        ));
    }

    private static void startCleanup() {
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();

            registry.forEach((component, list) -> {
                list.removeIf(instance -> {
                    boolean expired = (now - instance.getLastSeen() > TIMEOUT);
                    if (expired) {
                        System.out.println("[TIMEOUT] Instancia removida por inatividade: " + instance.getIdentity() + " (" + component + ")");
                    }
                    return expired;
                });
            });

            pendingRequests.values().removeIf(pendingReq -> now - pendingReq.timestamp > 10000);

        }, 5, 5, TimeUnit.SECONDS);
    }

    private static void sendError(DatagramPacket packet, String requestId, long now) throws Exception {
        Message error = new Message(
                MessageType.ERROR,
                ComponentType.GATEWAY,
                "GATEWAY",
                "localhost",
                GATEWAY_PORT,
                requestId,
                "NO_INSTANCE",
                String.valueOf(now)
        );

        byte[] data = error.toHttpFormat().getBytes(StandardCharsets.UTF_8);

        socket.send(new DatagramPacket(
                data,
                data.length,
                packet.getAddress(),
                packet.getPort()
        ));
    }

    private static void sendBadRequest(DatagramPacket packet, String requestId, long now) throws Exception {
        Message error = new Message(
                MessageType.ERROR,
                ComponentType.GATEWAY,
                "GATEWAY",
                "localhost",
                GATEWAY_PORT,
                requestId,
                "BAD_REQUEST",
                String.valueOf(now)
        );

        byte[] data = error.toHttpFormat().getBytes(StandardCharsets.UTF_8);

        socket.send(new DatagramPacket(
                data,
                data.length,
                packet.getAddress(),
                packet.getPort()
        ));
    }
}