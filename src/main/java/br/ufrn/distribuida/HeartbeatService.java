package br.ufrn.distribuida;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class HeartbeatService implements Runnable {

    private final String serviceType;
    private final String instanceHost;
    private final int    instancePort;
    private final String gatewayHost;
    private final int    gatewayHeartbeatPort;
    private final int    intervalMs;

    public HeartbeatService(AppConfiguration config) {
        this.serviceType          = config.getServiceType();
        this.instanceHost         = "localhost";
        this.instancePort         = config.getPort();
        this.gatewayHost          = config.getGatewayHost();
        this.gatewayHeartbeatPort = config.getGatewayHeartbeatPort();
        this.intervalMs           = config.getHeartbeatIntervalMs();
    }

    @Override
    public void run() {
        System.out.printf("[HEARTBEAT] Iniciando registro de '%s' (porta %d) no gateway %s:%d%n",
                serviceType, instancePort, gatewayHost, gatewayHeartbeatPort);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                sendHeartbeat();
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("[HEARTBEAT] Falha ao contatar gateway: " + e.getMessage());
            }
        }
    }

    private void sendHeartbeat() throws Exception {
        String body = serviceType + ":" + instanceHost + ":" + instancePort;
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

        String request =
                "POST /heartbeat HTTP/1.1\r\n" +
                "Host: " + gatewayHost + "\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: " + bodyBytes.length + "\r\n" +
                "\r\n" +
                body;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(gatewayHost, gatewayHeartbeatPort), 2000);
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }
}
