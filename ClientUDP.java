
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

class ClientUDP {

    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName("localhost");
            Message message = new Message("Hello, Server!", socket.getLocalPort());

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
            objectOutputStream.writeObject(message);
            byte[] data = outputStream.toByteArray();

            DatagramPacket packet = new DatagramPacket(data, data.length, address, 9003);
            socket.send(packet);
            System.out.println("Message sent to server.");
        } catch (UnknownHostException | SocketException e) {
            System.err.println("Network setup error: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error serializing or sending UDP message: " + e.getMessage());
        }
    }

}


