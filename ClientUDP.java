import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class ClientUDP {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Uso: java ClientUDP <payload>");
            return;
        }

        try (DatagramSocket socket = new DatagramSocket()) {
            String requestId = System.currentTimeMillis() + "-1";
            
            Message msg = new Message(
                    MessageType.POST,
                    ComponentType.LOGIN,
                    "CLIENT",
                    "localhost",
                    socket.getLocalPort(),
                    requestId,
                    args[0],
                    String.valueOf(System.currentTimeMillis())
            );
            
                    // String wire = msg.toHttpFormat();
                    // System.out.println("[SEND CLIENT] -> localhost:9000\n" + wire.replace("\r\n", "\n"));
                    byte[] sendData = msg.toHttpFormat().getBytes(StandardCharsets.UTF_8);
            
            InetAddress address = InetAddress.getByName("localhost");
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, 9000);
            socket.send(sendPacket);
            
            byte[] receiveBuffer = new byte[4096];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);
            
            String responseJson = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8);
            Message response = Message.fromHttpFormat(responseJson);
            
            System.out.println(response.payload());
        }
    }
}