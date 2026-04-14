package UDP;


import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class InstanceUDP {
    private static ComponentType componentType;
    private static String instanceID;
    private final static String GATEWAY_HOST = "localhost";
    private final static int GATEWAY_PORT = 9000;
    private static int localPort;
    private static final TicketStore ticketStore = new TicketStore("ingressos.txt");

    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Uso: java InstanceUDP <componentType> <instanceID> <porta>");
            System.exit(1);
        }

        InstanceUDP.componentType = ComponentType.fromString(args[0]);
        if (InstanceUDP.componentType == ComponentType.UNKNOWN) {
            System.err.println("Componente desconhecido: " + args[0]);
            System.exit(1);
        }
        InstanceUDP.instanceID = args[1];
        InstanceUDP.localPort = Integer.parseInt(args[2]);
        new InstanceUDP().start();
    }

    public void start(){
        System.out.println("[START] Iniciando instancia " + instanceID + " do tipo " + componentType + " na porta " + localPort);
        
        try(DatagramSocket socket = new DatagramSocket(localPort)){
            System.out.println("[REGISTER] Solicitando registro no gateway " + GATEWAY_HOST + ":" + GATEWAY_PORT + " ...");
            Message message = new Message(MessageType.REGISTER, componentType, instanceID, GATEWAY_HOST,localPort, "", "", String.valueOf(System.currentTimeMillis()));
            sendMessage(socket, message);
            
            boolean registered = false;
            socket.setSoTimeout(5000); 
            
            while(!registered) {
                try {
                    byte[] buf = new byte[4096];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String json = new String(packet.getData(), 0, packet.getLength());
                    Message msg = Message.fromHttpFormat(json);

                    if (msg.type() == MessageType.RESPONSE && "REGISTER".equals(msg.requestId())) {
                        if ("OK".equals(msg.payload())) {
                            System.out.println("[REGISTER] Registro confirmado pelo Gateway!");
                            registered = true;
                        }
                    }
                } catch (java.net.SocketTimeoutException e) {
                    System.out.println("[REGISTER] Timeout de 5s expirou. Tentando registrar novamente...");
                    sendMessage(socket, message);
                }
            }
            
            socket.setSoTimeout(0); 

            new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(5000);
                        System.out.println("[HEARTBEAT] Enviando pulso de vida...");
                        sendMessage(socket, new Message(
                                MessageType.HEARTBEAT,
                                componentType,
                                instanceID,
                                "localhost",
                                localPort,
                                "",
                                "",
                                String.valueOf(System.currentTimeMillis())
                        ));

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();

            System.out.println("[INFO] Instância pronta e aguardando requisições...");



            
            while (true) { 
                try {
                    byte[] buf = new byte[4096]; 
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet); 
                    String json = new String(packet.getData(), 0, packet.getLength());
                    Message msg = Message.fromHttpFormat(json);

                    MessageType type = msg.type();

                    if (type == MessageType.GET || type == MessageType.POST) {
                        System.out.println("[" + type + "] Recebido (ID: " + msg.requestId() + "). Payload: '" + msg.payload() + "'.");
                        Message response = handleBusinessRequest(msg);
                        if (response != null) {
                            sendMessage(socket, response);
                            System.out.println("[RESPONSE] Resposta enviada com sucesso ao Gateway.");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Erro ao processar pacote na Instancia: " + e.getMessage());
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void sendMessage(DatagramSocket socket, Message msg) throws Exception {
        String wire = msg.toHttpFormat();
        System.out.println("[SEND INSTANCE] -> " + GATEWAY_HOST + ":" + GATEWAY_PORT + "\n" + wire.replace("\r\n", "\n"));
        byte[] data = wire.getBytes(StandardCharsets.UTF_8);

        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName(GATEWAY_HOST),
                GATEWAY_PORT
        );

        socket.send(packet);
    }

    private Message handleBusinessRequest(Message msg) {
        long now = System.currentTimeMillis();

        try {
            if (msg.componentType() == ComponentType.INGRESSOS_DISPONIVEIS) {
                if (msg.type() != MessageType.GET) {
                    return null;
                }

                String available = ticketStore.listAvailableTickets();
                return buildResponse(msg, available, now);
            }

            if (msg.componentType() == ComponentType.COMPRAR_INGRESSO) {
                if (msg.type() != MessageType.POST) {
                    return null;
                }

                String payload = msg.payload() == null ? "" : msg.payload().trim();
                if (payload.isEmpty() || !payload.matches("\\d+")) {
                    return null;
                }

                int ticketNumber = Integer.parseInt(payload);
                String result = ticketStore.buyTicket(ticketNumber);
                if (result.contains("SUCESSO")) {
                    return buildResponse(msg, result, now);
                }
                return null;
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Message buildResponse(Message request, String payload, long now) {
        return new Message(
                MessageType.RESPONSE,
                request.componentType(),
                instanceID,
                "localhost",
                localPort,
                request.requestId(),
                payload,
                String.valueOf(now)
        );
    }

}
