import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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

public class GatewayTCP {

    private static final int HEARTBEAT_PORT = 9000;
    private static final int GATEWAY_PORT = 9001;
    private static final int CLEANUP_INTERVAL_SECONDS = 5;
    private static final int HEARTBEAT_BACKLOG = 200;
    private static final int GATEWAY_BACKLOG = 1500;
    private static final int HEARTBEAT_READ_TIMEOUT_MS = 2000;
    private static final int CLIENT_READ_TIMEOUT_MS = 10000;
    private static final int SERVICE_CONNECT_TIMEOUT_MS = 2000;
    private static final int SERVICE_READ_TIMEOUT_MS = 10000;

    private static final ConcurrentHashMap<String, InstanceInfo> isEmailServices = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, InstanceInfo> isPasswordServices = new ConcurrentHashMap<>();

    private static final AtomicInteger isEmailIndex    = new AtomicInteger(0);
    private static final AtomicInteger isPasswordIndex = new AtomicInteger(0);
    private static final AtomicInteger generalIndex    = new AtomicInteger(0);

    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private ServerSocket heartbeatServer;
    private ServerSocket gatewayServer;

    public void start() {
        try {
            heartbeatServer = new ServerSocket(HEARTBEAT_PORT, HEARTBEAT_BACKLOG);
            gatewayServer = new ServerSocket(GATEWAY_PORT, GATEWAY_BACKLOG);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        startCleanup();
        executor.submit(this::listenHeartBeat);
        executor.submit(this::listenGateway);
    }

    private void listenHeartBeat() {
        while (true) {
            try {
                Socket socket = heartbeatServer.accept();
                executor.execute(() -> handleHeartbeat(socket));
            } catch (IOException e) {
                System.err.println("Erro ao aceitar heartbeat: " + e.getMessage());
            }
        }
    }

    private void handleHeartbeat(Socket socket) {
        try (socket) {
            socket.setSoTimeout(HEARTBEAT_READ_TIMEOUT_MS);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            HttpRequest request = readHttpRequest(reader);

            if (request != null) {
                updateService(request.getBody());
            }
        } catch (IOException e) {
            System.err.println("Erro ao processar heartbeat: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Erro inesperado no heartbeat: " + e.getMessage());
        }
    }

    private void listenGateway() {
        while (true) {
            try {
                Socket socket = gatewayServer.accept();
                executor.execute(() -> handleClientConnection(socket));
            } catch (IOException e) {
                System.err.println("Erro ao aceitar conexao do cliente: " + e.getMessage());
            }
        }
    }

    private void handleClientConnection(Socket socket) {
        try (socket) {
            socket.setSoTimeout(CLIENT_READ_TIMEOUT_MS);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            HttpRequest request = readHttpRequest(reader);

            if (request == null) {
                writeResponse(socket, buildSimpleResponse(400, "Bad Request"));
                return;
            }

            request.setHeader("X-Client-IP: " + socket.getInetAddress().getHostAddress());
            request.setHeader("X-Client-Port: " + socket.getPort());

            String path = request.getPath();
            InstanceInfo target;

            if ("/isemail".equalsIgnoreCase(path)) {
                target = roundRobin(isEmailServices, isEmailIndex);
            } else if ("/ispassword".equalsIgnoreCase(path)) {
                target = roundRobin(isPasswordServices, isPasswordIndex);
            } else if ("/echo".equalsIgnoreCase(path) || path.toLowerCase().startsWith("/validate/")) {
                target = getAnyAvailableInstance();
            } else {
                writeResponse(socket, buildSimpleResponse(404, "Not Found"));
                return;
            }

            if (target == null) {
                writeResponse(socket, buildSimpleResponse(503, "Servico indisponivel"));
                return;
            }

            HTTPResponse response = forwardToService(request, target);
            if (response == null) {
                writeResponse(socket, buildSimpleResponse(503, "Servico indisponivel"));
                return;
            }

            response.setHeader("X-Gateway-Target: " + target.getHost() + ":" + target.getPort());
            writeResponse(socket, response);
        } catch (Exception e) {
            System.err.println("Erro processando conexao TCP: " + e.getMessage());
        }
    }

    private HTTPResponse forwardToService(HttpRequest request, InstanceInfo target) {
        try (Socket serviceSocket = new Socket()) {
            serviceSocket.connect(
                new InetSocketAddress(target.getHost(), target.getPort()),
                SERVICE_CONNECT_TIMEOUT_MS);
            serviceSocket.setSoTimeout(SERVICE_READ_TIMEOUT_MS);
            OutputStream output = serviceSocket.getOutputStream();
            byte[] data = request.toString().getBytes(StandardCharsets.UTF_8);
            output.write(data);
            output.flush();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(serviceSocket.getInputStream(), StandardCharsets.UTF_8));
            return readHttpResponse(reader);
        } catch (IOException e) {
            System.err.println("Erro ao encaminhar requisicao: " + e.getMessage());
            return null;
        }
    }

    private void writeResponse(Socket socket, HTTPResponse response) throws IOException {
        OutputStream output = socket.getOutputStream();
        byte[] data = response.toString().getBytes(StandardCharsets.UTF_8);
        output.write(data);
        output.flush();
    }

    private HTTPResponse buildSimpleResponse(int statusCode, String body) {
        String protocol = "HTTP/1.1";
        String status = HTTPUtils.mapStatus(statusCode);
        HTTPResponse response = new HTTPResponse(protocol, statusCode, status);

        String safeBody = body == null ? "" : body;
        int length = safeBody.getBytes(StandardCharsets.UTF_8).length;

        response.setHeader("Content-Type: text/plain; charset=utf-8");
        response.setHeader("Content-Length: " + length);
        response.setHeader("Connection: close");
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
            System.err.println("Heartbeat invalido: " + body);
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

    private InstanceInfo getAnyAvailableInstance() {
        long now = System.currentTimeMillis();
        List<InstanceInfo> all = new ArrayList<>();
        for (InstanceInfo i : isEmailServices.values()) {
            if (i.isAlive(now)) all.add(i);
        }
        for (InstanceInfo i : isPasswordServices.values()) {
            if (i.isAlive(now)) all.add(i);
        }
        if (all.isEmpty()) return null;
        int i = generalIndex.getAndUpdate(v -> (v + 1) % all.size());
        return all.get(i % all.size());
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

    private HttpRequest readHttpRequest(BufferedReader reader) {
        StringBuilder headersBuilder = new StringBuilder();
        String firstHeader;

        try {
            firstHeader = reader.readLine();
            if (firstHeader == null) {
                return null;
            }

            HttpRequest request = new HttpRequest(firstHeader);

            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                Integer parsedLength = extractContentLength(line);
                if (parsedLength != null) {
                    request.setContentLength(parsedLength);
                }

                headersBuilder.append(line).append("\r\n");
            }

            request.setHeaders(headersBuilder.toString());

            if (request.getContentLength() > 0) {
                int totalRead = 0;
                char[] body = new char[request.getContentLength()];

                while (totalRead < request.getContentLength()) {
                    int read = reader.read(body, totalRead, request.getContentLength() - totalRead);
                    if (read == -1) {
                        break;
                    }
                    totalRead += read;
                }

                if (totalRead < request.getContentLength()) {
                    return null;
                }

                request.setBody(body);
            }

            return request;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private HTTPResponse readHttpResponse(BufferedReader reader) {
        StringBuilder headersBuilder = new StringBuilder();
        String firstHeader;

        try {
            firstHeader = reader.readLine();
            if (firstHeader == null) {
                return null;
            }

            HTTPResponse response = new HTTPResponse(firstHeader);

            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                Integer parsedLength = extractContentLength(line);
                if (parsedLength != null) {
                    response.setContentLength(parsedLength);
                }

                headersBuilder.append(line).append("\r\n");
            }

            response.setHeaders(headersBuilder.toString());
            if (response.getContentLength() > 0) {
                int totalRead = 0;
                char[] body = new char[response.getContentLength()];

                while (totalRead < response.getContentLength()) {
                    int read = reader.read(body, totalRead, response.getContentLength() - totalRead);
                    if (read == -1) {
                        break;
                    }
                    totalRead += read;
                }

                if (totalRead < response.getContentLength()) {
                    return null;
                }

                response.setBody(body);
            }

            return response;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Integer extractContentLength(String line) {
        if (line == null) {
            return null;
        }

        int sep = line.indexOf(':');
        if (sep <= 0) {
            return null;
        }

        String name = line.substring(0, sep).trim();
        if (!"Content-Length".equalsIgnoreCase(name)) {
            return null;
        }

        String value = line.substring(sep + 1).trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static void main(String[] args) {
        try {
            GatewayTCP gateway = new GatewayTCP();
            gateway.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
