package message;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class HttpRequest {
	private String method;
	private String path;
	private String queryString;
	private String requestLine;
	private Map<String, String> queryParams;

	private int contentLength;
	private String body;
	private Map<String, String> headers;

	public HttpRequest() {
		this.contentLength = 0;
		this.queryParams = new HashMap<>();
		this.headers = new HashMap<>();
	}

	public HttpRequest(String requestLine) {
		this();
		this.requestLine = requestLine;

		if (requestLine == null) {
			return;
		}

		StringTokenizer tokenizer = new StringTokenizer(requestLine);
		if (!tokenizer.hasMoreTokens()) {
			return;
		}

		this.method = tokenizer.nextToken();

		if (!tokenizer.hasMoreTokens()) {
			return;
		}

		String pathQuery = tokenizer.nextToken();
		if (pathQuery.contains("?")) {
			String[] parts = pathQuery.split("\\?", 2);
			this.path = parts[0];
			this.queryString = parts[1];
			parseQueryParams(this.queryString);
		} else {
			this.path = pathQuery;
		}
	}

	private void parseQueryParams(String query) {
		if (query == null || query.isEmpty()) {
			return;
		}

		String[] params = query.split("&");
		for (String param : params) {
			String[] keyValue = param.split("=", 2);
			String key = keyValue[0];
			String value = keyValue.length > 1 ? keyValue[1] : "";
			this.queryParams.put(key, value);
		}
	}

	public Map<String, String> getQueryParams() {
		return this.queryParams;
	}

	public String getRequestLine() {
		return this.requestLine;
	}

	public String getMethod() {
		return this.method;
	}

	public String getPath() {
		return this.path;
	}

	public String getQueryString() {
		return this.queryString;
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

		messageBuilder.append(this.requestLine).append("\r\n");
		messageBuilder.append(this.getHeaders()).append("\r\n");

		if (this.contentLength > 0) {
			messageBuilder.append(this.body);
		}

		return messageBuilder.toString();
	}
}
