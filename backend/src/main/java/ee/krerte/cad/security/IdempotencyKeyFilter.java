package ee.krerte.cad.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotency-Key filter — RFC 7231 / Stripe-stiilis.
 *
 * Kui klient saadab {@code X-Idempotency-Key: <uuid>} header'iga POST
 * päringu, cachime response'i 24h. Samasuguse key'ga teine request tagastab
 * cached vastuse — ei tööta controller'i metoodi uuesti, ei kuluta Claude
 * raha, ei looma duplicate orders'e.
 *
 * Sobib:
 *   - /api/generate  (retry võrgu vigast)
 *   - /api/orders    (topelt-tellimuse kaitse)
 *   - /api/billing/* (topelt-payment kaitse)
 *
 * Märkus: in-memory cache — restart kustutab. Prod-is kasuta Redis't (järgmine
 * branch, db-infrastructure).
 */
@Component
public class IdempotencyKeyFilter extends OncePerRequestFilter {

    private static final long TTL_MS = 24 * 60 * 60 * 1000L;
    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, CachedResponse> cache = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        // Ainult POST/PUT/PATCH + ainult kui X-Idempotency-Key header olemas
        String method = req.getMethod();
        if (!("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) return true;
        return req.getHeader("X-Idempotency-Key") == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {

        String key = req.getHeader("X-Idempotency-Key");
        if (key == null || key.length() > 200) {
            chain.doFilter(req, resp);
            return;
        }

        // Key scoping: (userIdOrIp, method, uri, key) — ära jaga key'sid erinevate
        // kasutajate vahel
        String scoped = scopeKey(req, key);
        purgeExpired();

        CachedResponse cached = cache.get(scoped);
        if (cached != null && !cached.isExpired()) {
            resp.setStatus(cached.status);
            cached.headers.forEach(resp::setHeader);
            resp.setHeader("X-Idempotent-Replay", "true");
            resp.getOutputStream().write(cached.body);
            return;
        }

        // Wrap response, et saaksime body't salvestada
        var wrapped = new CapturingResponse(resp);
        chain.doFilter(req, wrapped);

        // Cache ainult 2xx/4xx (mitte 5xx - neid tahame uuesti proovida)
        int status = wrapped.getStatus();
        if (status >= 200 && status < 500 && cache.size() < MAX_ENTRIES) {
            cache.put(scoped, new CachedResponse(
                status,
                Map.of("Content-Type", wrapped.getContentType() == null ? "application/json" : wrapped.getContentType()),
                wrapped.getBody(),
                Instant.now().toEpochMilli() + TTL_MS
            ));
        }
        wrapped.flush();
    }

    private String scopeKey(HttpServletRequest req, String key) {
        String user = req.getUserPrincipal() != null
                ? req.getUserPrincipal().getName()
                : req.getRemoteAddr();
        return user + ":" + req.getMethod() + ":" + req.getRequestURI() + ":" + key;
    }

    private void purgeExpired() {
        long now = Instant.now().toEpochMilli();
        cache.entrySet().removeIf(e -> e.getValue().expiresAt < now);
    }

    private record CachedResponse(int status, Map<String, String> headers, byte[] body, long expiresAt) {
        boolean isExpired() { return Instant.now().toEpochMilli() > expiresAt; }
    }

    /** ServletResponse wrapper, mis kapitaliseerib kirjutatud body't. */
    static class CapturingResponse extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private PrintWriter writer;
        private ServletOutputStream stream;

        CapturingResponse(HttpServletResponse response) { super(response); }

        @Override
        public ServletOutputStream getOutputStream() {
            if (stream == null) {
                stream = new ServletOutputStream() {
                    @Override public boolean isReady() { return true; }
                    @Override public void setWriteListener(WriteListener w) {}
                    @Override public void write(int b) { buffer.write(b); }
                };
            }
            return stream;
        }

        @Override
        public PrintWriter getWriter() {
            if (writer == null) {
                writer = new PrintWriter(buffer);
            }
            return writer;
        }

        byte[] getBody() {
            if (writer != null) writer.flush();
            return buffer.toByteArray();
        }

        void flush() throws IOException {
            byte[] body = getBody();
            getResponse().getOutputStream().write(body);
            getResponse().getOutputStream().flush();
        }
    }
}
