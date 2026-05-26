package br.ufrn.distribuida;

import br.ufrn.imd.middleware.MiddlewareRunner;
import br.ufrn.imd.middleware.configuration.MiddlewareApplication;

/**
 * Ponto de entrada da instância middleware.
 *
 * O config.yml define os valores padrão. Qualquer campo pode ser
 * sobrescrito por system properties no momento da execução:
 *
 *   mvn exec:java -Dexec.jvmArgs="-Dapp.port=9103 -Dapp.serviceType=isemail -Dapp.instanceId=inst-email-2"
 *
 * Propriedades suportadas:
 *   app.port          – porta que o middleware vai abrir
 *   app.protocol      – protocolo usado pela instância (HTTP | UDP)
 *   app.serviceType   – tipo de serviço para registro no gateway (isemail | ispassword)
 *   app.instanceId    – identificador único da instância
 *   app.gatewayHost   – host do gateway (padrão: localhost)
 *   app.gatewayHeartbeatPort – porta de heartbeat do gateway (padrão: 9000)
 */
@MiddlewareApplication
public class App {

    public static void main(String[] args) {
        MiddlewareRunner runner = new MiddlewareRunner()
                .configureRunner(App.class)
                .configureArgs(AppConfiguration.class, args)
                .build();

        applySystemPropertyOverrides(runner);

        if (runner.getConfiguration() instanceof AppConfiguration config
                && config.getServiceType() != null) {

            Thread heartbeat = Thread.ofVirtual()
                    .name("heartbeat-" + config.getServiceType())
                    .start(new HeartbeatService(config));

            Runtime.getRuntime().addShutdownHook(
                    new Thread(heartbeat::interrupt, "heartbeat-shutdown"));
        }

        runner.run();
    }

    private static void applySystemPropertyOverrides(MiddlewareRunner runner) {
        if (!(runner.getConfiguration() instanceof AppConfiguration config)) return;

        String port = System.getProperty("app.port");
        if (port != null) config.setPort(Integer.parseInt(port));

        String protocol = System.getProperty("app.protocol");
        if (protocol != null) config.setProtocol(protocol);

        String serviceType = System.getProperty("app.serviceType");
        if (serviceType != null) config.setServiceType(serviceType);

        String instanceId = System.getProperty("app.instanceId");
        if (instanceId != null) config.setInstanceId(instanceId);

        String gatewayHost = System.getProperty("app.gatewayHost");
        if (gatewayHost != null) config.setGatewayHost(gatewayHost);

        String gatewayPort = System.getProperty("app.gatewayHeartbeatPort");
        if (gatewayPort != null) config.setGatewayHeartbeatPort(Integer.parseInt(gatewayPort));
    }
}
