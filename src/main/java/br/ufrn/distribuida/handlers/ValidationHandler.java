package br.ufrn.distribuida.handlers;

import br.ufrn.imd.middleware.broker.annotations.BodyParam;
import br.ufrn.imd.middleware.broker.annotations.Endpoint;
import br.ufrn.imd.middleware.broker.annotations.Handler;
import br.ufrn.imd.middleware.broker.entities.ResponseWrapper;
import br.ufrn.imd.middleware.broker.enums.HTTPMethods;
import br.ufrn.imd.middleware.broker.enums.HTTPStatus;

@Handler(basePath = "/")
public class ValidationHandler {

    @Endpoint(method = HTTPMethods.POST, path = "isemail")
    public ResponseWrapper<String> isEmail(@BodyParam String body) {
        boolean valid = body != null && body.contains("@");
        return ResponseWrapper.ok(String.valueOf(valid));
    }

    @Endpoint(method = HTTPMethods.POST, path = "ispassword")
    public ResponseWrapper<String> isPassword(@BodyParam String body) {
        boolean valid = body != null
                && body.matches(".*[a-zA-Z].*")
                && body.matches(".*\\d.*");
        return ResponseWrapper.ok(String.valueOf(valid));
    }
}
