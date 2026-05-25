package br.ufrn.distribuida.handlers;

import br.ufrn.imd.middleware.broker.annotations.BodyParam;
import br.ufrn.imd.middleware.broker.annotations.Endpoint;
import br.ufrn.imd.middleware.broker.annotations.Handler;
import br.ufrn.imd.middleware.broker.annotations.HeaderParam;
import br.ufrn.imd.middleware.broker.annotations.PathParam;
import br.ufrn.imd.middleware.broker.annotations.QueryParam;
import br.ufrn.imd.middleware.broker.entities.ResponseWrapper;
import br.ufrn.imd.middleware.broker.enums.HTTPMethods;
import br.ufrn.imd.middleware.broker.enums.HTTPStatus;

/**
 * Endpoints extras que demonstram o uso de @PathParam, @QueryParam e @HeaderParam.
 */
@Handler(basePath = "/")
public class UtilHandler {

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
     * POST /echo
     *
     * Retorna o corpo da requisição transformado conforme o header X-Transform.
     * Valores aceitos em X-Transform: upper, lower, reverse (padrão: sem transformação).
     *
     * Demonstra: @BodyParam + @HeaderParam
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
