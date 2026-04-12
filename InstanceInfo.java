public class InstanceInfo{
    String instanceId;
    String host;
    int port;
    long lastSeen;

    public InstanceInfo(String instanceId, String host, int port, long lastSeen) {
        this.instanceId = instanceId;
        this.host = host;
        this.port = port;
        this.lastSeen = lastSeen;

    }

    public void setLastSeen(long lastSeen){
        this.lastSeen = lastSeen;
    }

    public long getLastSeen(){
        return lastSeen;
    }

    public String getInstanceId(){
        return instanceId;
    }

    public String getHost(){
        return host;
    }

    public int getPort(){
        return port;
    }

    public String getIdentity() {
        return instanceId + "|" + host + "|" + port;
    }

    public boolean isAlive(long now) {
        return now - lastSeen <= 15000;
    }   

    
}