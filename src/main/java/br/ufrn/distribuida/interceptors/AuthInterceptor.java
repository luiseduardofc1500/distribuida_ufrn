package br.ufrn.distribuida.interceptors;

import br.ufrn.imd.middleware.broker.entities.ResponseWrapper;
import br.ufrn.imd.middleware.broker.enums.HTTPStatus;
import br.ufrn.imd.middleware.exceptions.InterceptionException;
import br.ufrn.imd.middleware.extension.invocationInterceptor.InvocationInterceptor;
import br.ufrn.imd.middleware.extension.invocationInterceptor.annotations.Interceptor;
import br.ufrn.imd.middleware.extension.invocationInterceptor.entity.AbstractInterceptedData;
import br.ufrn.imd.middleware.extension.invocationInterceptor.enums.InvocationType;

import java.util.Map;

@Interceptor(value = InvocationType.BEFORE_INVOCATION, priority = 1)
public class AuthInterceptor implements InvocationInterceptor {

    private static final String EXPECTED_AUTHORIZATION = "Bearer segredo123";

    @Override
    public void intercept(AbstractInterceptedData data) {
        String authorization = getHeaderIgnoreCase(data.getHeaders(), "Authorization");

        if (!EXPECTED_AUTHORIZATION.equals(authorization)) {
            throw new InterceptionException(
                    ResponseWrapper.status(HTTPStatus.UNAUTHORIZED, "Token invalido ou ausente")
            );
        }
    }

    private String getHeaderIgnoreCase(Map<String, String> headers, String name) {
        if (headers == null) return null;

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }

        return null;
    }
}
