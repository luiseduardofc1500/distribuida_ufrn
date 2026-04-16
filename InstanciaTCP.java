import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import message.HTTPResponse;
import message.HTTPUtils;
import message.HttpRequest;

public class InstanciaTCP {
    private static String serviceType;
    private static String instanceId;
    private static final String GATEWAY_HOST = "localhost";
    private static final int HEARTBEAT_PORT = 9000;
    private static final int HEARTBEAT_INTERVAL_MS = 3000;
    private static final int SERVER_BACKLOG = 1500;
    private static final int HEARTBEAT_WAKE_TIMEOUT_MS = 2000;
    private static int localPort;

    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Uso: java InstanciaTCP <isEmail|isPassword> <instanceId> <port>");
            return;
        }

        serviceType = args[0].trim();
        if (!isValidServiceType(serviceType)) {
            System.err.println("Servico invalido: " + serviceType);
            return;
        }

        instanceId = args[1].trim();
        localPort = Integer.parseInt(args[2]);
        new InstanciaTCP().start();
    }

    public void start() {
        System.out.println("[START] Instancia " + instanceId + " do tipo " + serviceType + " na porta " + localPort);

        new Thread(() -> sendHeartbeatLoop()).start();

        try (ServerSocket serverSocket = new ServerSocket(localPort, SERVER_BACKLOG)) {
            System.out.println("[INFO] Instancia TCP pronta e aguardando requisicoes...");

            while (true) {
                Socket socket = serverSocket.accept();
                executor.execute(() -> handleConnection(socket));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isValidServiceType(String type) {
        return "isemail".equalsIgnoreCase(type) || "ispassword".equalsIgnoreCase(type);
    }

    private void sendHeartbeatLoop() {
        while (true) {
            try {
                sendHeartbeat();
                Thread.sleep(HEARTBEAT_INTERVAL_MS);
            } catch (Exception e) {
                System.err.println("Erro ao enviar heartbeat: " + e.getMessage());
            }
        }
    }

    private void sendHeartbeat() throws Exception {
        HttpRequest request = new HttpRequest("POST /heartbeat HTTP/1.1");
        request.setHeader("Host: " + GATEWAY_HOST);
        request.setHeader("Content-Type: text/plain");

        String body = serviceType + ":" + GATEWAY_HOST + ":" + localPort;
        int length = body.getBytes(StandardCharsets.UTF_8).length;

        request.setHeader("Content-Length: " + length);
        request.setContentLength(length);
        request.setBody(body);

        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(GATEWAY_HOST, HEARTBEAT_PORT),
                    HEARTBEAT_WAKE_TIMEOUT_MS);
            OutputStream output = socket.getOutputStream();
            byte[] data = request.toString().getBytes(StandardCharsets.UTF_8);
            output.write(data);
            output.flush();
        }
    }

    private void handleConnection(Socket socket) {
        try (socket) {
            socket.setSoTimeout(15000); // timeout generoso tbm pro keepalive do worker
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            
            while (true) {
                HttpRequest request = readHttpRequest(reader);
                if (request == null) break;

                HTTPResponse response = handleRequest(request);
                writeResponse(socket, response);

                String connHeader = request.getHeader("Connection");
                if ("close".equalsIgnoreCase(connHeader)) {
                    break;
                }
            }
        } catch (Exception e) {
             // cliente fechou a conexão
        }
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

    private void writeResponse(Socket socket, HTTPResponse response) throws IOException {
        OutputStream output = socket.getOutputStream();
        byte[] data = response.toString().getBytes(StandardCharsets.UTF_8);
        output.write(data);
        output.flush();
    }

    private HttpRequest readHttpRequest(BufferedReader reader) {
        StringBuilder headersBuilder = new StringBuilder();
        try {
            String firstHeader = reader.readLine();
            if (firstHeader == null) return null;
            HttpRequest request = new HttpRequest(firstHeader);
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                Integer parsedLength = extractContentLength(line);
                if (parsedLength != null) request.setContentLength(parsedLength);
                headersBuilder.append(line).append("\r\n");
            }
            request.setHeaders(headersBuilder.toString());
            if (request.getContentLength() > 0) {
                int totalRead = 0;
                char[] body = new char[request.getContentLength()];
                while (totalRead < request.getContentLength()) {
                    int read = reader.read(body, totalRead, request.getContentLength() - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }
                if (totalRead < request.getContentLength()) return null;
                request.setBody(body);
            }
            return request;
        } catch (IOException e) { return null; }
    }

    private Integer extractContentLength(String line) {
        if (line == null) return null;
        int sep = line.indexOf(':');
        if (sep <= 0) return null;
        String name = line.substring(0, sep).trim();
        if (!"Content-Length".equalsIgnoreCase(name)) return null;
        String value = line.substring(sep + 1).trim();
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return null; }
    }
}
