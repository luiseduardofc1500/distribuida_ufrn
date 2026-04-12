
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class InstanciaUDP {
    private static ComponentType componentType;
    private static String instanceID;
    private final static String GATEWAY_HOST = "localhost";
    private final static int GATEWAY_PORT = 9000;
    private static int localPort;

    
    public static void main(String[] args) {
        InstanciaUDP.componentType = ComponentType.fromString(args[0]);
        if (InstanciaUDP.componentType == ComponentType.UNKNOWN) {
            System.err.println("Componente desconhecido: " + args[0]);
            System.exit(1);
        }
        InstanciaUDP.instanceID = args[1];
        InstanciaUDP.localPort = Integer.parseInt(args[2]);
        new InstanciaUDP().start();
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
                        System.out.println("[" + type + "] Recebido (ID: " + msg.requestId() + "). Payload: '" + msg.payload() + "'. Preparando resposta...");
                        
                        Message response = new Message(
                                MessageType.RESPONSE,
                                msg.componentType(),                 
                                instanceID,                
                                "localhost",         
                                localPort,                 
                                msg.requestId(),           
                                "OK from " + instanceID,   
                                String.valueOf(System.currentTimeMillis()) 
                        );
                        sendMessage(socket, response);
                        System.out.println("[RESPONSE] Resposta enviada com sucesso ao Gateway.");
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
        byte[] data = wire.getBytes();

        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName(GATEWAY_HOST),
                GATEWAY_PORT
        );

        socket.send(packet);
    }

}
