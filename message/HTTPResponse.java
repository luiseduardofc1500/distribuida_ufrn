package message;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class HTTPResponse {
    private String status;
    private int code;
    private String contentType;
    private int contentLength;
    private String statusLine;

    private Map<String, String> headers;
    private String body;

    public HTTPResponse() {
        this.contentLength = 0;
        this.headers = new HashMap<>();
    }

    public HTTPResponse(String statusLine) {
        this();
        this.statusLine = statusLine;

        StringTokenizer tokenizer = new StringTokenizer(statusLine);
        tokenizer.nextToken();
        this.code = Integer.parseInt(tokenizer.nextToken());
        this.status = tokenizer.nextToken();
    }

    public HTTPResponse(String protocol, int code, String status) {
        this();
        this.code = code;
        this.status = status;
        this.statusLine = protocol + " " + code + " " + status;
    }

    public String getStatusLine() {
        return this.statusLine;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCode() {
        return this.code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getContentType() {
        return this.contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setContentLength(String line) {
        this.contentLength = Integer.parseInt(line.split(":")[1].trim());
    }

    public void setContentLength(int length) {
        this.contentLength = length;
    }

    public int getContentLength() {
        return this.contentLength;
    }

    public void setHeaders(String headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }

        String[] lines = headers.split("\r\n");
        for (String line : lines) {
            this.setHeader(line);
        }
    }

    public void setHeader(String header) {
        int index = header.indexOf(":");
        if (index > 0) {
            String headerKey = header.substring(0, index).trim();
            String value = header.substring(index + 1).trim();
            this.headers.put(headerKey, value);
        }
    }

    public String getHeaders() {
        StringBuilder builder = new StringBuilder();

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.append(entry.getKey())
                   .append(": ")
                   .append(entry.getValue())
                   .append("\r\n");
        }

        return builder.toString();
    }

    public String getHeader(String key) {
        return headers.get(key);
    }

    public void setBody(char[] body) {
        this.body = new String(body);
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getBody() {
        return this.body;
    }

    public String toString() {
        StringBuilder messageBuilder = new StringBuilder();

        messageBuilder.append(this.statusLine).append("\r\n");
        messageBuilder.append(this.getHeaders()).append("\r\n");

        if (this.contentLength > 0) {
            messageBuilder.append(this.body);
        }

        return messageBuilder.toString();
    }
}
