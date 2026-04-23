package ee.krerte.cad.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Keskne teenus audit-kirjete tegemiseks.
 *
 * <p>Kasutamine:
 * <pre>
 *   auditService.record("DESIGN_CREATE", "design", designId, "SUCCESS",
 *       Map.of("template", spec.get("template").asText()));
 * </pre>
 *
 * <p><b>Async</b>: audit-write on {@code @Async} — ei blokeeri request'i.
 * Kui audit-kirjutamine fail'ib (DB down), siis me logime ERROR ja liigume
 * edasi — äri-tegevus ei tohi peatuda logging-failure'i tõttu.
 *
 * <p><b>Context</b>: tõmbame user'i SecurityContext'ist ja request-id
 * MDC'st automaatselt — kutsuja ei pea neid edasi andma.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repo;
    private final ObjectMapper mapper;
    private final HttpServletRequest request;

    public AuditService(AuditLogRepository repo, ObjectMapper mapper, HttpServletRequest request) {
        this.repo = repo;
        this.mapper = mapper;
        this.request = request;
    }

    @Async
    public void record(String action, String targetType, Long targetId, String outcome,
                       Map<String, ?> details) {
        try {
            var entry = new AuditLog();
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setOutcome(outcome);
            entry.setRequestId(MDC.get("requestId"));

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                // Me hoiame userId tavaliselt Principal.getName() kujul string'ina —
                // controller'id võivad konverteerida kui tarvis
                try { entry.setActorUserId(Long.parseLong(auth.getName())); } catch (Exception ignored) {}
            }

            if (request != null) {
                entry.setActorIp(clientIp(request));
                entry.setActorUa(truncate(request.getHeader("User-Agent"), 500));
            }

            if (details != null && !details.isEmpty()) {
                entry.setDetailsJson(mapper.writeValueAsString(details));
            }

            repo.save(entry);
        } catch (Exception e) {
            log.error("Audit log write failed: action={} target={}:{} outcome={} err={}",
                action, targetType, targetId, outcome, e.getMessage());
        }
    }

    /** Convenience overload ilma details'ita. */
    public void record(String action, String targetType, Long targetId, String outcome) {
        record(action, targetType, targetId, outcome, Map.of());
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // Esimene IP = päris klient (proxy ahela algus)
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).strip();
        }
        return req.getRemoteAddr();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
