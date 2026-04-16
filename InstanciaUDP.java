import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import message.HTTPResponse;
import message.HTTPUtils;
import message.HttpRequest;

public class InstanciaUDP {
    private static String serviceType;
    private static String instanceId;
    private static final String GATEWAY_HOST = "localhost";
    private static final int HEARTBEAT_PORT = 9000;
    private static final int HEARTBEAT_INTERVAL_MS = 3000;
    private static int localPort;

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Uso: java InstanciaUDP <isEmail|isPassword> <instanceId> <port>");
            return;
        }

        serviceType = args[0].trim();
        if (!isValidServiceType(serviceType)) {
            System.err.println("Servico invalido: " + serviceType);
            return;
        }

        instanceId = args[1].trim();
        localPort = Integer.parseInt(args[2]);
        new InstanciaUDP().start();
    }

    public void start() {
        System.out.println("[START] Instancia " + instanceId + " do tipo " + serviceType + " na porta " + localPort);

        try (DatagramSocket socket = new DatagramSocket(localPort)) {
            new Thread(() -> sendHeartbeatLoop(socket)).start();
            System.out.println("[INFO] Instancia pronta e aguardando requisicoes...");

            while (true) {
                byte[] buf = new byte[4096];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                BufferedReader messageReader = new BufferedReader(new StringReader(message));
                HttpRequest request = getHTTPRequest(messageReader);

                if (request == null) {
                    sendResponse(socket, packet, buildResponse(400, "Bad Request", null));
                    continue;
                }

                HTTPResponse response = handleRequest(request);
                sendResponse(socket, packet, response);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isValidServiceType(String type) {
        return "isemail".equalsIgnoreCase(type) || "ispassword".equalsIgnoreCase(type);
    }

    private void sendHeartbeatLoop(DatagramSocket socket) {
        while (true) {
            try {
                sendHeartbeat(socket);
                Thread.sleep(HEARTBEAT_INTERVAL_MS);
            } catch (Exception e) {
                System.err.println("Erro ao enviar heartbeat: " + e.getMessage());
            }
        }
    }

    private void sendHeartbeat(DatagramSocket socket) throws Exception {
        HttpRequest request = new HttpRequest("POST /heartbeat HTTP/1.1");
        request.setHeader("Host: " + GATEWAY_HOST);
        request.setHeader("Content-Type: text/plain");

        String body = serviceType + ":" + GATEWAY_HOST + ":" + localPort;
        int length = body.getBytes(StandardCharsets.UTF_8).length;

        request.setHeader("Content-Length: " + length);
        request.setContentLength(length);
        request.setBody(body);

        byte[] data = request.toString().getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName(GATEWAY_HOST),
                HEARTBEAT_PORT
        );

        socket.send(packet);
    }

    private HTTPResponse handleRequest(HttpRequest request) {
        String method = request.getMethod();
        String path = request.getPath();
        String body = request.getBody() == null ? "" : request.getBody();

        if (!"POST".equalsIgnoreCase(method)) {
            return buildResponse(405, "Method Not Allowed", request);
        }

        if (path == null) {
            return buildResponse(404, "Not Found", request);
        }

        if ("isemail".equalsIgnoreCase(serviceType)) {
            if (!"/isemail".equalsIgnoreCase(path)) {
                return buildResponse(404, "Not Found", request);
            }

            boolean result = body.contains("@");
            return buildResponse(200, result ? "sim" : "nao", request);
        }

        if ("ispassword".equalsIgnoreCase(serviceType)) {
            if (!"/ispassword".equalsIgnoreCase(path)) {
                return buildResponse(404, "Not Found", request);
            }

            boolean hasLetter = body.matches(".*[A-Za-z].*");
            boolean hasDigit = body.matches(".*[0-9].*");
            boolean result = hasLetter && hasDigit;
            return buildResponse(200, result ? "sim" : "nao", request);
        }

        return buildResponse(404, "Not Found", request);
    }

    private HTTPResponse buildResponse(int statusCode, String body, HttpRequest request) {
        String protocol = "HTTP/1.1";
        String status = HTTPUtils.mapStatus(statusCode);
        HTTPResponse response = new HTTPResponse(protocol, statusCode, status);

        String safeBody = body == null ? "" : body;
        int length = safeBody.getBytes(StandardCharsets.UTF_8).length;

        response.setHeader("Content-Type: text/plain; charset=utf-8");
        response.setHeader("Content-Length: " + length);
        response.setContentLength(length);
        response.setBody(safeBody);

        if (request != null) {
            String clientIp = request.getHeader("X-Client-IP");
            String clientPort = request.getHeader("X-Client-Port");
            if (clientIp != null) {
                response.setHeader("X-Client-IP: " + clientIp);
            }
            if (clientPort != null) {
                response.setHeader("X-Client-Port: " + clientPort);
            }
        }

        return response;
    }

    private void sendResponse(DatagramSocket socket, DatagramPacket requestPacket, HTTPResponse response) {
        try {
            byte[] data = response.toString().getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    requestPacket.getAddress(),
                    requestPacket.getPort()
            );
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("Erro ao enviar resposta: " + e.getMessage());
        }
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
}
