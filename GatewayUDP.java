import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GatewayUDP {
    private static int GATEWAY_PORT = 9000;
    private static Map<String, List<InstanceInfo>> registry = new HashMap<>();
    private static Map<String, Integer> roundRobinIndex = new HashMap<>();
    
    private static Map<String, ClientInfo> pendingRequests = new HashMap<>();

    public static void main(String args[]) {
        System.out.println("Gateway UDP rodando na porta " + GATEWAY_PORT + "...");

        try (DatagramSocket socket = new DatagramSocket(GATEWAY_PORT)) {
            while (true) {
                byte[] buf = new byte[2048]; 
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String json = new String(packet.getData(), 0, packet.getLength());
                Message msg = Message.fromJson(json);

                long now = System.currentTimeMillis();
                String type = msg.type();
                String component = msg.componentType();
                String id = msg.instanceId();

                if ("REGISTER".equals(type)) {
                    List<InstanceInfo> instances = registry.computeIfAbsent(component, k -> new ArrayList<>());
                    boolean exists = false;
                    for (InstanceInfo info : instances) {
                        if (info.getInstanceId().equals(id)) {
                            info.setLastSeen(now);
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        instances.add(new InstanceInfo(id, msg.host(), msg.port(), now));
                    }
                } 
                else if ("HEARTBEAT".equals(type)) {
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

                else if ("REQUEST".equals(type)) {
                    List<InstanceInfo> instances = registry.get(component);
                    
                    if (instances != null && !instances.isEmpty()) {
                        int index = roundRobinIndex.getOrDefault(component, 0);
                        InstanceInfo target = instances.get(index % instances.size());
                        roundRobinIndex.put(component, index + 1);

                        pendingRequests.put(msg.requestId(), new ClientInfo(
                            packet.getAddress().getHostAddress(), 
                            packet.getPort()
                        ));

                        byte[] outData = msg.toJson().getBytes();
                        DatagramPacket packetOut = new DatagramPacket(
                            outData, outData.length, 
                            InetAddress.getByName(target.getHost()), 
                            target.getPort()
                        );
                        socket.send(packetOut);
                    } else {
                        Message errorMsg = new Message(
                            "RESPONSE", 
                            component,
                            "GATEWAY",
                            "localhost",    
                            GATEWAY_PORT,         
                            msg.requestId(),      
                            "NO_INSTANCE",
                            String.valueOf(now));
                        byte[] errData = errorMsg.toJson().getBytes();
                        socket.send(new DatagramPacket(
                            errData, errData.length, 
                            packet.getAddress(), packet.getPort()
                        ));
                    }
                }

                else if ("RESPONSE".equals(type)) {
                    ClientInfo client = pendingRequests.remove(msg.requestId());
                    
                    if (client != null) {
                        byte[] outData = msg.toJson().getBytes();
                        DatagramPacket packetOut = new DatagramPacket(
                            outData, outData.length, 
                            InetAddress.getByName(client.getHost()), 
                            client.getPort());
                        socket.send(packetOut);
                    }
                    else{
                        System.out.println("RESPONSE SEM O REQUEST ID CONHECIDO: ");
                    }
                }

                for (List<InstanceInfo> list : registry.values()) {
                    list.removeIf(info -> (now - info.getLastSeen() > 15000));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}