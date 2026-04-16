package message;
public class HTTPUtils {
    public static String mapStatus(Integer code) {
        if (code == null) {
            return "UNKNOWN";
        }

        switch (code) {
            case 200:
                return "OK";
            case 400:
                return "Bad Request";
            case 405:
                return "Method Not Allowed";
            case 500:
                return "Internal Server Error";
            case 503:
                return "Service Unavailable";
            default:
                return "UNKNOWN";
        }
    }
}
