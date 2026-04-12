
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class InstanciaUDP {
    private static ComponentType componentType;
    private static String instanceID;
    private final static String gatewayHost = "localhost";
    private final static int gatewayPort = 9000;
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
            System.out.println("[REGISTER] Solicitando registro no gateway " + gatewayHost + ":" + gatewayPort + " ...");
            Message message = new Message(MessageType.REGISTER, componentType, instanceID, gatewayHost,localPort, "", "", String.valueOf(System.currentTimeMillis()));
            sendMessage(socket, message);
            
            boolean registered = false;
            socket.setSoTimeout(5000); // Espera maximo 5s pela resposata
            
            while(!registered) {
                try {
                    byte[] buf = new byte[4096];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String json = new String(packet.getData(), 0, packet.getLength());
                    Message msg = Message.fromJson(json);

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
            
            socket.setSoTimeout(0); // Reinicia o parametro para que os REQUESTs possam esperar indefinidamente

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
                    Message msg = Message.fromJson(json);

                    MessageType type = msg.type();

                    if (type == MessageType.REQUEST) {
                        System.out.println("[REQUEST] Recebido Request (ID: " + msg.requestId() + "). Payload: '" + msg.payload() + "'. Preparando resposta...");
                        
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
        byte[] data = msg.toJson().getBytes();

        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName(gatewayHost),
                gatewayPort
        );

        socket.send(packet);
    }

}
