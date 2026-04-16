import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import message.HTTPResponse;
import message.HTTPUtils;
import message.HttpRequest;

public class GatewayUDP {

    private static final int HEARTBEAT_PORT = 9000;
    private static final int GATEWAY_PORT = 9001;
    private static final int CLEANUP_INTERVAL_SECONDS = 5;

    private static final ConcurrentHashMap<String, InstanceInfo> isEmailServices = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, InstanceInfo> isPasswordServices = new ConcurrentHashMap<>();

    private static final AtomicInteger isEmailIndex = new AtomicInteger(0);
    private static final AtomicInteger isPasswordIndex = new AtomicInteger(0);

    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private DatagramSocket socketHeartbeat;
    private DatagramSocket socketGateway;

    public void start() {
        try {
            socketHeartbeat = new DatagramSocket(HEARTBEAT_PORT);
            socketGateway = new DatagramSocket(GATEWAY_PORT);
        } catch (SocketException e) {
            e.printStackTrace();
            return;
        }

        startCleanup();
        executor.submit(this::listenHeartBeat);
        server();
    }

    private void listenHeartBeat() {
        while (true) {
            try {
                byte[] serverMessage = new byte[2048];
                DatagramPacket serverPacket = new DatagramPacket(serverMessage, serverMessage.length);
                socketHeartbeat.receive(serverPacket);

                String message = new String(serverPacket.getData(), 0, serverPacket.getLength(), StandardCharsets.UTF_8);
                BufferedReader messageReader = new BufferedReader(new StringReader(message));
                HttpRequest request = getHTTPRequest(messageReader);

                if (request != null) {
                    updateService(request.getBody());
                }
            } catch (IOException e) {
                System.err.println("Erro ao processar heartbeat: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Erro inesperado no heartbeat: " + e.getMessage());
            }
        }
    }

    private void server() {
        try {
            while (true) {
                byte[] clientMessage = new byte[4096];
                DatagramPacket clientPacket = new DatagramPacket(clientMessage, clientMessage.length);
                socketGateway.receive(clientPacket);

                DatagramPacket packetCopy = new DatagramPacket(
                        clientPacket.getData().clone(),
                        clientPacket.getLength(),
                        clientPacket.getAddress(),
                        clientPacket.getPort()
                );

                executor.execute(() -> handleGatewayPacket(packetCopy));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleGatewayPacket(DatagramPacket packet) {
        try {
            String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            BufferedReader messageReader = new BufferedReader(new StringReader(message));

            if (message.startsWith("HTTP/")) {
                HTTPResponse response = getHTTPResponse(messageReader);
                if (response != null) {
                    sendResponseToClient(response);
                }
            } else {
                HttpRequest request = getHTTPRequest(messageReader);
                if (request == null) {
                    sendErrorToClient(packet, 400, "Bad Request");
                    return;
                }
                handleClientRequest(request, packet);
            }
        } catch (Exception e) {
            System.err.println("Erro processando pacote do gateway: " + e.getMessage());
        }
    }

    private void handleClientRequest(HttpRequest request, DatagramPacket packet) throws IOException {
        request.setHeader("X-Client-IP: " + packet.getAddress().getHostAddress());
        request.setHeader("X-Client-Port: " + packet.getPort());

        String path = request.getPath();
        InstanceInfo target;

        if ("/isemail".equalsIgnoreCase(path)) {
            target = roundRobin(isEmailServices, isEmailIndex);
        } else if ("/ispassword".equalsIgnoreCase(path)) {
            target = roundRobin(isPasswordServices, isPasswordIndex);
        } else {
            sendErrorToClient(packet, 404, "Not Found");
            return;
        }

        if (target == null) {
            sendErrorToClient(packet, 503, "Servico indisponivel");
            return;
        }

        byte[] data = request.toString().getBytes(StandardCharsets.UTF_8);
        socketGateway.send(new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName(target.getHost()),
                target.getPort()
        ));
    }

    private void sendResponseToClient(HTTPResponse response) throws IOException {
        String ip = response.getHeader("X-Client-IP");
        String portValue = response.getHeader("X-Client-Port");

        if (ip == null || portValue == null) {
            System.err.println("Resposta sem cabecalhos X-Client.");
            return;
        }

        if (ip.startsWith("/")) {
            ip = ip.substring(1);
        }

        int port;
        try {
            port = Integer.parseInt(portValue);
        } catch (NumberFormatException e) {
            System.err.println("Porta invalida na resposta: " + portValue);
            return;
        }

        byte[] data = response.toString().getBytes(StandardCharsets.UTF_8);
        socketGateway.send(new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName(ip),
                port
        ));
    }

    private void sendErrorToClient(DatagramPacket packet, int statusCode, String body) {
        try {
            HTTPResponse response = buildSimpleResponse(statusCode, body);
            byte[] data = response.toString().getBytes(StandardCharsets.UTF_8);
            socketGateway.send(new DatagramPacket(
                    data,
                    data.length,
                    packet.getAddress(),
                    packet.getPort()
            ));
        } catch (IOException e) {
            System.err.println("Erro ao enviar erro para cliente: " + e.getMessage());
        }
    }

    private HTTPResponse buildSimpleResponse(int statusCode, String body) {
        String protocol = "HTTP/1.1";
        String status = HTTPUtils.mapStatus(statusCode);
        HTTPResponse response = new HTTPResponse(protocol, statusCode, status);

        String safeBody = body == null ? "" : body;
        int length = safeBody.getBytes(StandardCharsets.UTF_8).length;

        response.setHeader("Content-Type: text/plain; charset=utf-8");
        response.setHeader("Content-Length: " + length);
        response.setContentLength(length);
        response.setBody(safeBody);
        return response;
    }

    private void updateService(String body) {
        if (body == null || body.isEmpty()) {
            return;
        }

        String[] tokens = body.trim().split(":");
        if (tokens.length < 3) {
            System.err.println(" XIIIIIIIII Heartbeat invalido: " + body);
            return;
        }

        String serviceType = tokens[0].trim().toLowerCase();
        String host = tokens[1].trim();
        String portValue = tokens[2].trim();

        int port;
        try {
            port = Integer.parseInt(portValue);
        } catch (NumberFormatException e) {
            System.err.println("Porta invalida no heartbeat: " + portValue);
            return;
        }

        ConcurrentHashMap<String, InstanceInfo> map;
        if ("isemail".equals(serviceType)) {
            map = isEmailServices;
        } else if ("ispassword".equals(serviceType)) {
            map = isPasswordServices;
        } else {
            System.err.println("Servico desconhecido no heartbeat: " + serviceType);
            return;
        }

        String identity = host + ":" + port;
        long now = System.currentTimeMillis();
        InstanceInfo existing = map.get(identity);

        if (existing == null) {
            map.put(identity, new InstanceInfo(identity, host, port, now));
            System.out.println("[NEW_INSTANCE] " + serviceType + " -> " + identity);
        } else {
            existing.setLastSeen(now);
        }
    }

    private InstanceInfo roundRobin(ConcurrentHashMap<String, InstanceInfo> serviceHashMap, AtomicInteger index) {
        long now = System.currentTimeMillis();
        List<InstanceInfo> servicesOnline = new ArrayList<>();

        for (InstanceInfo instancia : serviceHashMap.values()) {
            if (instancia.isAlive(now)) {
                servicesOnline.add(instancia);
            }
        }

        if (servicesOnline.isEmpty()) {
            return null;
        }

        int i = index.getAndUpdate(v -> (v + 1) % servicesOnline.size());
        if (i < 0) {
            i = (i % servicesOnline.size()) + servicesOnline.size();
        }

        return servicesOnline.get(i % servicesOnline.size());
    }

    private void startCleanup() {
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            cleanupMap(isEmailServices, "isEmail", now);
            cleanupMap(isPasswordServices, "isPassword", now);
        }, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void cleanupMap(ConcurrentHashMap<String, InstanceInfo> map, String label, long now) {
        map.values().removeIf(instance -> {
            boolean expired = !instance.isAlive(now);
            if (expired) {
                System.out.println("[TIMEOUT] Removendo instancia " + label + ": " + instance.getIdentity());
            }
            return expired;
        });
    }

    private HttpRequest getHTTPRequest(BufferedReader clientRequest) {
        StringBuilder headersBuilder = new StringBuilder();
        String firstHeader;

        try {
            firstHeader = clientRequest.readLine();
            if (firstHeader == null) {
                return null;
            }

            HttpRequest request = new HttpRequest(firstHeader);

            String line;
            while ((line = clientRequest.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    request.setContentLength(line);
                }

                headersBuilder.append(line).append("\r\n");
            }

            request.setHeaders(headersBuilder.toString());

            if (request.getContentLength() > 0) {
                int totalRead = 0;
                char[] body = new char[request.getContentLength()];

                while (totalRead < request.getContentLength()) {
                    int read = clientRequest.read(body, totalRead, request.getContentLength() - totalRead);
                    if (read == -1) {
                        break;
                    }
                    totalRead += read;
                }

                request.setBody(body);
            }

            return request;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private HTTPResponse getHTTPResponse(BufferedReader serverResponse) {
        StringBuilder headersBuilder = new StringBuilder();
        String firstHeader;

        try {
            firstHeader = serverResponse.readLine();
            if (firstHeader == null) {
                return null;
            }

            HTTPResponse response = new HTTPResponse(firstHeader);

            String line;
            while ((line = serverResponse.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    response.setContentLength(line);
                }

                headersBuilder.append(line).append("\r\n");
            }

            response.setHeaders(headersBuilder.toString());
            if (response.getContentLength() > 0) {
                char[] body = new char[response.getContentLength()];
                serverResponse.read(body, 0, response.getContentLength());
                response.setBody(body);
            }

            return response;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        try {
            GatewayUDP gateway = new GatewayUDP();
            gateway.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
