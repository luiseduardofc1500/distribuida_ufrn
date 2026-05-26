package br.ufrn.distribuida.handlers;

import br.ufrn.imd.middleware.broker.annotations.BodyParam;
import br.ufrn.imd.middleware.broker.annotations.Endpoint;
import br.ufrn.imd.middleware.broker.annotations.Handler;
import br.ufrn.imd.middleware.broker.annotations.HeaderParam;
import br.ufrn.imd.middleware.broker.annotations.InstancePolicy;
import br.ufrn.imd.middleware.broker.annotations.PathParam;
import br.ufrn.imd.middleware.broker.annotations.QueryParam;
import br.ufrn.imd.middleware.broker.entities.ResponseWrapper;
import br.ufrn.imd.middleware.broker.enums.HTTPMethods;
import br.ufrn.imd.middleware.broker.enums.HTTPStatus;
import br.ufrn.imd.middleware.broker.enums.LifecyclePolicy;

import java.time.Instant;
import java.util.UUID;

/**
 * Endpoints extras para demonstrar parâmetros e ciclo de vida de handlers.
 */
@Handler(basePath = "/")
@InstancePolicy(LifecyclePolicy.PER_REQUEST)
public class UtilHandler {

    private final String instanceId;
    private final Instant createdAt;

    public UtilHandler() {
        this.instanceId = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }

    /**
     * GET /validate/{type}?value=xxx
     *
     * Valida um valor de acordo com o tipo informado na URL.
     * Tipos suportados: email, password, numeric.
     *
     * Demonstra: @PathParam + @QueryParam
     */
    @Endpoint(method = HTTPMethods.GET, path = "validate/{type}")
    public ResponseWrapper<String> validateByType(
            @PathParam("type") String type,
            @QueryParam("value") String value) {

        if (value == null || value.isBlank()) {
            return ResponseWrapper.status(HTTPStatus.BAD_REQUEST,
                    "Query param 'value' é obrigatório");
        }

        boolean result = switch (type.toLowerCase()) {
            case "email"    -> value.contains("@");
            case "password" -> value.matches(".*[a-zA-Z].*") && value.matches(".*\\d.*");
            case "numeric"  -> value.matches("\\d+");
            default         -> false;
        };

        return ResponseWrapper.ok(String.valueOf(result));
    }

    /**
     * GET /demo/instance
     *
     * Demonstra LifecyclePolicy.PER_REQUEST: cada requisição cria um UtilHandler novo.
     */
    @Endpoint(method = HTTPMethods.GET, path = "demo/instance")
    public ResponseWrapper<String> instanceInfo() {
        return ResponseWrapper.ok(
                "handler=UtilHandler"
                        + "; lifecycle=PER_REQUEST"
                        + "; instanceId=" + instanceId
                        + "; createdAt=" + createdAt);
    }

    /**
     * POST /echo
     *
     * Demonstra: @BodyParam + @HeaderParam("X-Transform")
     */
    @Endpoint(method = HTTPMethods.POST, path = "echo")
    public ResponseWrapper<String> echo(
            @BodyParam String body,
            @HeaderParam("X-Transform") String transform) {

        if (body == null || body.isBlank()) {
            return ResponseWrapper.status(HTTPStatus.BAD_REQUEST, "Body é obrigatório");
        }

        String result = body;
        if (transform != null) {
            result = switch (transform.toLowerCase()) {
                case "upper"   -> body.toUpperCase();
                case "lower"   -> body.toLowerCase();
                case "reverse" -> new StringBuilder(body).reverse().toString();
                default        -> body;
            };
        }

        return ResponseWrapper.ok(result);
    }
}
