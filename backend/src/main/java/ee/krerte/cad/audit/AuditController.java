package ee.krerte.cad.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Audit-log read-only REST endpoint'id. Admin-only.
 *
 * <p>UI show'itakse admin-paneelis: filter user/action/date, paginate.
 * Tundmatuid kasutajaid ei lase audit-sise nägemata — {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditLogRepository repo;

    public AuditController(AuditLogRepository repo) {
        this.repo = repo;
    }

    /** Viimased N audit-kirjet (default 50, max 200). */
    @GetMapping
    public Page<AuditLog> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String action
    ) {
        var pg = PageRequest.of(
            Math.max(0, page),
            Math.min(200, Math.max(1, size)),
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
        if (userId != null)  return repo.findByActorUserIdOrderByCreatedAtDesc(userId, pg);
        if (action != null)  return repo.findByActionOrderByCreatedAtDesc(action, pg);
        return repo.findAll(pg);
    }

    /** Viimased 24h kõik tegevused. */
    @GetMapping("/recent")
    public Page<AuditLog> recent() {
        var from = Instant.now().minus(24, ChronoUnit.HOURS);
        return repo.findByDateRange(from, Instant.now(),
            PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt")));
    }
}
