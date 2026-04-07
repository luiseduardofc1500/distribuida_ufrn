
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class InstanciaUDP {
    private static String componentType;
    private static String instanceID;
    private static String gatewayHost = "localhost";
    private static int gatewayPort = 9000;
    private static int localPort;

    
    public static void main(String[] args) {
        InstanciaUDP.componentType = args[0];
        InstanciaUDP.instanceID = args[1];
        InstanciaUDP.localPort = Integer.parseInt(args[2]);
        new InstanciaUDP().start();
    }

    public void start(){
        System.out.println("Iniciando uma instancia");
        
        try(DatagramSocket socket = new DatagramSocket(localPort)){
            Message message = new Message("REGISTER", componentType, instanceID, gatewayHost,localPort, "", "", String.valueOf(System.currentTimeMillis()));
            sendMessage(socket, message);
            new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(5000);

                        sendMessage(socket, new Message(
                                "HEARTBEAT",
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

            while (true) { 
                byte[] buf = new byte[2048]; 
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet); 
                String json = new String(packet.getData(), 0, packet.getLength());
                Message msg = Message.fromJson(json);

                String type = msg.type();

                if ("REQUEST".equals(type)) {

                    Message response = new Message(
                            "RESPONSE",
                            msg.componentType(),                 
                            instanceID,                
                            "localhost",         
                            localPort,                 
                            msg.requestId(),           
                            "OK from " + instanceID,   
                            String.valueOf(System.currentTimeMillis()) 
                    );
                    sendMessage(socket, response);
                }
            }}
        catch (Exception e) {
            e.printStackTrace();
        }}

    
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
