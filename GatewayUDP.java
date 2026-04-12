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

    private static final Map<String, List<InstanceInfo>> registry = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> roundRobinIndex = new ConcurrentHashMap<>();
    private static final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    private static final ExecutorService managementPool = Executors.newVirtualThreadPerTaskExecutor();
    private static final ExecutorService trafficPool = Executors.newVirtualThreadPerTaskExecutor();
    private static final ScheduledExecutorService cleanupScheduler = Executors.newScheduledThreadPool(1);

    private static final List<String> dynamicOrder = new CopyOnWriteArrayList<>();
    private static final AtomicInteger componentIndex = new AtomicInteger(0);

    private static DatagramSocket socket;

    static class PendingRequest {
        ClientInfo clientInfo;
        long timestamp;
        PendingRequest(ClientInfo clientInfo, long timestamp) {
            this.clientInfo = clientInfo;
            this.timestamp = timestamp;
        }
    }

    public static void main(String[] args) {
        System.out.println("Gateway UDP rodando na porta " + GATEWAY_PORT + "...");

        try {
            socket = new DatagramSocket(GATEWAY_PORT);
            
            cleanupScheduler.scheduleAtFixedRate(() -> {
                long now = System.currentTimeMillis();
                
                for (Map.Entry<String, List<InstanceInfo>> entry : registry.entrySet()) {
                    List<InstanceInfo> instances = entry.getValue();
                    instances.removeIf(info -> {
                        if (now - info.getLastSeen() > 15000) {
                            System.out.println("[GATEWAY - CLEANUP] Instancia considerada morta (sem heartbeat) e removida: ID=" + info.getInstanceId() + " | Tipo=" + entry.getKey());
                            return true;
                        }
                        return false;
                    });

                    if (instances.isEmpty()) {
                        dynamicOrder.remove(entry.getKey());
                    }
                }
                
                pendingRequests.values().removeIf(req -> {
                    if (now - req.timestamp > 10000) {
                        System.out.println("[GATEWAY - CLEANUP] Request pendente expirado e removido (timeout passado).");
                        return true;
                    }
                    return false;
                });
                
            }, 5, 5, TimeUnit.SECONDS);

            while (true) {
                try {
                    byte[] buf = new byte[4096];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    
                    String json = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    Message msg = Message.fromJson(json);
                    
                    if ("REGISTER".equals(msg.type()) || "HEARTBEAT".equals(msg.type())) {
                        managementPool.submit(() -> processManagement(msg, packet));
                    } else {
                        trafficPool.submit(() -> processTraffic(msg, packet));
                    }
                } catch (Exception e) {
                    System.err.println("Erro ao receber/enviar pacote: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processManagement(Message msg, DatagramPacket packet) {
        try {
            long now = System.currentTimeMillis();
            String type = msg.type();
            String component = msg.componentType();
            String id = msg.instanceId();

            if ("REGISTER".equals(type)) {
                List<InstanceInfo> instances = registry.computeIfAbsent(component, k -> {
                    if (!dynamicOrder.contains(k)) {
                        dynamicOrder.add(k);
                    }
                    return new CopyOnWriteArrayList<>();
                });
                
                boolean exists = false;
                for (InstanceInfo info : instances) {
                    if (info.getInstanceId().equals(id)) {
                        info.setLastSeen(now);
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    System.out.println("[GATEWAY - REGISTER] Nova Instancia registrada: ID=" + id + " | Tipo=" + component + " | Endereco=" + msg.host() + ":" + msg.port());
                    instances.add(new InstanceInfo(id, msg.host(), msg.port(), now));
                    roundRobinIndex.putIfAbsent(component, new AtomicInteger(0));
                }
            } else if ("HEARTBEAT".equals(type)) {
                System.out.println("[GATEWAY - HEARTBEAT] Batimento recebido de ID=" + id);
                List<InstanceInfo> instances = registry.get(component);
                if (instances != null) {
                    for (InstanceInfo info : instances) {
                        if (info.getInstanceId().equals(id)) {
                            info.setLastSeen(now);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao processar controle: " + e.getMessage());
        }
    }

    private static void processTraffic(Message msg, DatagramPacket packet) {
        try {
            long now = System.currentTimeMillis();
            String type = msg.type();
            String component = msg.componentType();

            if ("REQUEST".equals(type)) {
                System.out.println("[GATEWAY - REQUEST] Recebido de " + packet.getAddress().getHostAddress() + ":" + packet.getPort() + ". Usando round");
                
                String selectedComponent = null;
                InstanceInfo target = null;

                int attempts = 0;
                int size = dynamicOrder.size();

                if (size > 0) {
                    while (attempts < size) {
                        int currentIdx = componentIndex.getAndIncrement();
                        String comp = dynamicOrder.get(Math.abs(currentIdx) % size);
                        List<InstanceInfo> instances = registry.get(comp);

                        if (instances != null && !instances.isEmpty()) {
                            List<InstanceInfo> aliveInstances = instances.stream()
                                .filter(i -> i.isAlive(now))
                                .toList();

                            if (!aliveInstances.isEmpty()) {
                                selectedComponent = comp;

                                AtomicInteger compIndex = roundRobinIndex.get(comp);
                                if (compIndex != null) {
                                    int index = compIndex.getAndIncrement();
                                    target = aliveInstances.get(Math.abs(index) % aliveInstances.size());
                                    break;
                                }
                            }
                        }
                        attempts++;
                    }
                }

                if (target == null) {
                    System.out.println("[GATEWAY - ERROR] Nenhuma instancia do tipo encontrada para resolver componente dinâmico. Devolvendo erro...");
                    Message errorMsg = new Message(
                            "RESPONSE",
                            "GATEWAY",
                            "GATEWAY",
                            "localhost",
                            GATEWAY_PORT,
                            msg.requestId(),
                            "NO_INSTANCE",
                            String.valueOf(now)
                    );

                    byte[] errData = errorMsg.toJson().getBytes(StandardCharsets.UTF_8);
                    socket.send(new DatagramPacket(
                            errData, errData.length,
                            packet.getAddress(), packet.getPort()
                    ));
                    return;
                }

                System.out.println("[GATEWAY - FORWARD] Repassando req " + msg.requestId() + " via Round-Robin para Instância ID=" + target.getInstanceId() + " (Tipo: " + selectedComponent + ")");
                
                pendingRequests.put(msg.requestId(), new PendingRequest(new ClientInfo(
                        packet.getAddress().getHostAddress(),
                        packet.getPort()
                ), now));

                Message forwardMsg = new Message(
                        msg.type(),
                        selectedComponent,
                        msg.instanceId(),
                        "localhost",
                        GATEWAY_PORT,
                        msg.requestId(),
                        msg.payload(),
                        msg.timestamp()
                );

                byte[] outData = forwardMsg.toJson().getBytes(StandardCharsets.UTF_8);

                DatagramPacket packetOut = new DatagramPacket(
                        outData,
                        outData.length,
                        InetAddress.getByName(target.getHost()),
                        target.getPort()
                );

                socket.send(packetOut);

            } else if ("RESPONSE".equals(type)) {
                PendingRequest pReq = pendingRequests.remove(msg.requestId());

                if (pReq != null) {
                    ClientInfo client = pReq.clientInfo;
                    byte[] outData = msg.toJson().getBytes(StandardCharsets.UTF_8);

                    DatagramPacket packetOut = new DatagramPacket(
                            outData,
                            outData.length,
                            InetAddress.getByName(client.getHost()),
                            client.getPort()
                    );

                    socket.send(packetOut);
                } else {
                    System.out.println("RESPONSE sem requestId conhecido ou expirado: " + msg.requestId());
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao processar trafego: " + e.getMessage());
        }
    }
}