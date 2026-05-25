package br.ufrn.distribuida;

import br.ufrn.imd.middleware.configuration.MiddlewareConfiguration;

/**
 * Estende MiddlewareConfiguration com os campos necessários para
 * se registrar no gateway via heartbeat.
 */
public class AppConfiguration extends MiddlewareConfiguration {

    private String serviceType;
    private String instanceId;
    private String gatewayHost   = "localhost";
    private int    gatewayHeartbeatPort = 9000;
    private int    heartbeatIntervalMs  = 3000;

    public String getServiceType()           { return serviceType; }
    public void   setServiceType(String v)   { this.serviceType = v; }

    public String getInstanceId()            { return instanceId; }
    public void   setInstanceId(String v)    { this.instanceId = v; }

    public String getGatewayHost()           { return gatewayHost; }
    public void   setGatewayHost(String v)   { this.gatewayHost = v; }

    public int  getGatewayHeartbeatPort()          { return gatewayHeartbeatPort; }
    public void setGatewayHeartbeatPort(int v)     { this.gatewayHeartbeatPort = v; }

    public int  getHeartbeatIntervalMs()           { return heartbeatIntervalMs; }
    public void setHeartbeatIntervalMs(int v)      { this.heartbeatIntervalMs = v; }
}
