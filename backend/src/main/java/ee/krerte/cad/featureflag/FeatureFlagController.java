package ee.krerte.cad.featureflag;

import ee.krerte.cad.audit.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Feature flag REST API.
 *
 * <ul>
 *   <li><b>GET  /api/flags</b> — publish flag state'i frontend'ile ("kas näita X?")</li>
 *   <li><b>GET  /api/admin/flags</b> — admin list koos rollout percent jne</li>
 *   <li><b>PUT  /api/admin/flags/{name}</b> — admin toggle / rollout</li>
 * </ul>
 */
@RestController
public class FeatureFlagController {

    private final JdbcTemplate jdbc;
    private final FeatureFlagService service;
    private final AuditService audit;

    public FeatureFlagController(JdbcTemplate jdbc, FeatureFlagService service, AuditService audit) {
        this.jdbc = jdbc;
        this.service = service;
        this.audit = audit;
    }

    /** Frontend: küsib kõikide flag'ide state'i current-user'i perspektiivist. */
    @GetMapping("/api/flags")
    public Map<String, Boolean> userFlags() {
        Long userId = currentUserId();
        var names = jdbc.queryForList("SELECT name FROM feature_flags", String.class);
        var out = new java.util.LinkedHashMap<String, Boolean>();
        for (String n : names) {
            out.put(n, service.isEnabled(n, userId));
        }
        return out;
    }

    /** Admin: kõik meta (rollout %, overrides). */
    @GetMapping("/api/admin/flags")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> adminList() {
        return jdbc.queryForList(
            "SELECT name, description, enabled, rollout_percent, " +
            "user_overrides::text AS user_overrides, created_at, updated_at " +
            "FROM feature_flags ORDER BY name");
    }

    /** Admin: toggle / rollout update. */
    @PutMapping("/api/admin/flags/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> update(
        @PathVariable String name,
        @RequestBody Map<String, Object> body
    ) {
        Boolean enabled = (Boolean) body.get("enabled");
        Integer rolloutPercent = body.get("rollout_percent") == null
            ? null : ((Number) body.get("rollout_percent")).intValue();

        var set = new StringBuilder();
        var args = new java.util.ArrayList<Object>();
        if (enabled != null)        { set.append("enabled = ?, "); args.add(enabled); }
        if (rolloutPercent != null) { set.append("rollout_percent = ?, "); args.add(rolloutPercent); }
        if (set.isEmpty()) throw new IllegalArgumentException("no fields to update");

        set.append("updated_at = NOW()");
        args.add(name);
        jdbc.update("UPDATE feature_flags SET " + set + " WHERE name = ?", args.toArray());

        var details = new java.util.LinkedHashMap<String, Object>();
        details.put("name", name);
        if (enabled != null) details.put("enabled", enabled);
        if (rolloutPercent != null) details.put("rollout_percent", rolloutPercent);
        audit.record("FLAG_UPDATE", "feature_flag", null, "SUCCESS", details);

        return Map.of("ok", true, "name", name);
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName()))
            return null;
        try { return Long.parseLong(auth.getName()); } catch (Exception e) { return null; }
    }
}
