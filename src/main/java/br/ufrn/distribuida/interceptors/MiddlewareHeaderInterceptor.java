package br.ufrn.distribuida.interceptors;

import br.ufrn.imd.middleware.extension.invocationInterceptor.InvocationInterceptor;
import br.ufrn.imd.middleware.extension.invocationInterceptor.annotations.Interceptor;
import br.ufrn.imd.middleware.extension.invocationInterceptor.entity.AbstractInterceptedData;
import br.ufrn.imd.middleware.extension.invocationInterceptor.enums.InvocationType;

import java.util.HashMap;
import java.util.Map;

@Interceptor(value = InvocationType.AFTER_INVOCATION, priority = 1)
public class MiddlewareHeaderInterceptor implements InvocationInterceptor {

    @Override
    public void intercept(AbstractInterceptedData data) {
        Map<String, String> headers = new HashMap<>();

        if (data.getHeaders() != null) {
            headers.putAll(data.getHeaders());
        }

        headers.put("X-Middleware", "distribuida-nelio");
        data.setHeaders(headers);
    }
}
