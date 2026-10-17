package com.pasalapeli.ticket.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.List;

/**
 * Reenvia al microservicio de destino las cabeceras de identidad del request entrante:
 * - Authorization (JWT de Azure AD): permite que Movie Service valide el mismo token
 *   que ya valido el BFF (defensa en profundidad).
 * - X-Dev-User-Role / X-Dev-User-Email: preserva la identidad del modo demo.
 */
public class RequestHeaderRelayInterceptor implements ClientHttpRequestInterceptor {

    private static final List<String> RELAY_HEADERS = List.of(
            "Authorization", "X-Dev-User-Role", "X-Dev-User-Email");

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest servletRequest = servletAttrs.getRequest();
            for (String header : RELAY_HEADERS) {
                String value = servletRequest.getHeader(header);
                if (value != null && !value.isBlank()) {
                    request.getHeaders().set(header, value);
                }
            }
        }
        return execution.execute(request, body);
    }
}
