import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class ServerUDP {

        public static void main(String args[]) {
            System.out.println("Server is running...");
            try {
                DatagramSocket socket = new DatagramSocket(9003);
                while (true) {
                    byte[] buf = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    byte[] data = packet.getData();
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(data, 0, packet.getLength());
                    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                    try {
                        Message message = (Message) objectInputStream.readObject();
                        System.out.println("Received message: " + message.message() + " from socket: " + message.socket());
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }

                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    
}
