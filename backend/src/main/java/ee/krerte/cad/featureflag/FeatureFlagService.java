package ee.krerte.cad.featureflag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Lihtne DB-põhine feature flag teenus. 3 rolluv-mehhanismi:
 *
 * <ol>
 *   <li><b>enabled=FALSE</b> → kõik saavad FALSE
 *   <li><b>user_overrides</b> — always-ON listi kasutajad (intern test)
 *   <li><b>rollout_percent</b> — deterministic hash(user_id) mod 100
 * </ol>
 *
 * <p>Kasutamine:
 *
 * <pre>
 *   if (flags.isEnabled("semantic_cache", userId)) { ... }
 * </pre>
 *
 * <p><b>Caching</b>: flag'i lookup on cache'itud 60s jaoks. Kui admin lülitab flag'i sisse, siis ≤
 * 1 min hiljem kõik instant'id näevad uut state'i.
 *
 * <p><b>Miks DB, mitte Unleash / LaunchDarkly?</b> Kerg'e ja piisav < 50 flag'i jaoks. Kui me
 * läheme üle 100, või vajame A/B-testimist targeting-ga (country, tier), migreerume Unleash'ile —
 * aga see pole täna ostmisvärskelt.
 */
@Service
public class FeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public FeatureFlagService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** Anonüümne / logoutitud user → false (kuni flag pole 100%). */
    public boolean isEnabled(String flagName) {
        return isEnabled(flagName, null);
    }

    @Cacheable(
            value = "feature_flags",
            key = "#flagName + ':' + (#userId == null ? 'anon' : #userId)")
    public boolean isEnabled(String flagName, Long userId) {
        try {
            var rows =
                    jdbc.queryForList(
                            "SELECT enabled, rollout_percent, user_overrides::text AS overrides "
                                    + "FROM feature_flags WHERE name = ?",
                            flagName);

            if (rows.isEmpty()) {
                log.debug("Feature flag '{}' not found — default OFF", flagName);
                return false;
            }

            var row = rows.get(0);
            boolean enabled = (Boolean) row.get("enabled");
            if (!enabled) return false;

            int rolloutPercent = (Integer) row.get("rollout_percent");

            // Override list — always on users
            if (userId != null) {
                String overridesJson = (String) row.get("overrides");
                if (overridesJson != null && !overridesJson.isBlank()) {
                    JsonNode arr = mapper.readTree(overridesJson);
                    for (JsonNode n : arr) {
                        if (n.asLong() == userId) return true;
                    }
                }
            }

            // Rollout percent — deterministic per-user
            if (rolloutPercent >= 100) return true;
            if (rolloutPercent <= 0) return false;
            if (userId == null) return false; // anon'idele rakendub alles 100%

            int bucket = (int) (Math.abs(userId.hashCode()) % 100);
            return bucket < rolloutPercent;
        } catch (Exception e) {
            log.warn("Feature flag lookup failed for '{}': {}", flagName, e.getMessage());
            return false; // Fail-closed — turvalisem
        }
    }
}
