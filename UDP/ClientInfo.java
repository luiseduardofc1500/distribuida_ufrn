package UDP;
public class ClientInfo {
   private final String host;
   private final int port;

    public ClientInfo(String host, int port){
        this.host = host;
        this.port = port;
    }

    public int getPort(){
        return port;
    }

    public String getHost(){
        return host;
    }

}
