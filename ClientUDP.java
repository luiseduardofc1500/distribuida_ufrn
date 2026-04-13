import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class ClientUDP {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Uso:");
            System.out.println("  java ClientUDP ingressos_disponiveis");
            System.out.println("  java ClientUDP comprar_ingresso <numero_ingresso>");
            return;
        }

        MessageType type;
        ComponentType componentType;
        String payload;

        String operation = args[0].trim().toUpperCase();
        if ("INGRESSOS_DISPONIVEIS".equals(operation)) {
            type = MessageType.GET;
            componentType = ComponentType.INGRESSOS_DISPONIVEIS;
            payload = "";
        } else if ("COMPRAR_INGRESSO".equals(operation)) {
            if (args.length < 2) {
                System.out.println("Informe o numero do ingresso para compra.");
                return;
            }
            type = MessageType.POST;
            componentType = ComponentType.COMPRAR_INGRESSO;
            payload = args[1];
        } else {
            System.out.println("Operacao invalida: " + args[0]);
            System.out.println("Operacoes aceitas: ingressos_disponiveis, comprar_ingresso");
            return;
        }

        try (DatagramSocket socket = new DatagramSocket()) {
            String requestId = System.currentTimeMillis() + "-1";
            
            Message msg = new Message(
                    type,
                    componentType,
                    "CLIENT",
                    "localhost",
                    socket.getLocalPort(),
                    requestId,
                    payload,
                    String.valueOf(System.currentTimeMillis())
            );
            
            byte[] sendData = msg.toHttpFormat().getBytes(StandardCharsets.UTF_8);
            
            InetAddress address = InetAddress.getByName("localhost");
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, 9000);
            socket.send(sendPacket);
            
            byte[] receiveBuffer = new byte[4096];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);
            
            String responseJson = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8);
            Message response = Message.fromHttpFormat(responseJson);
            
            System.out.println("Status: " + response.type());
            System.out.println(response.payload());
        }
    }
}