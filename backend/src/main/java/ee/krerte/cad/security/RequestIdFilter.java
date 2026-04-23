package ee.krerte.cad.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Request correlation ID — kui klient saadab X-Request-Id, kasutame seda; muidu genereerime uue
 * UUID. Lisame MDC-sse (traceId kõrvale) ja response header'isse, et saaks kasutaja veateate põhjal
 * otsejoones backendist logi leida.
 *
 * <p>Oluline: jookseb enne tracing filtrit, et logs+traces+request_id ühenduks Grafana'is
 * üks-ühele.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String incoming = req.getHeader(HEADER);
        String requestId =
                (incoming != null && !incoming.isBlank() && incoming.length() <= 64)
                        ? incoming
                        : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        resp.setHeader(HEADER, requestId);
        try {
            chain.doFilter(req, resp);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
