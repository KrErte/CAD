package ee.krerte.cad.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh token rotation koos theft-detection'iga.
 *
 * <h3>Miks?</h3>
 * Praegu tagastame 24h eluga JWT. Kui see JWT lekib, ründaja saab kuni
 * 24 tundi käia. Kui me kasutame 15min JWT + pika refresh-token'i, siis
 * ründaja kaotab access'i 15 min pärast lekket.
 *
 * <h3>Rotation + family</h3>
 * Iga refresh tagastab UUS refresh token, vana invalidate'itakse
 * (replaced_by_id). Kui ründaja ja ohver kasutavad sama VANA tokenit,
 * siis me näeme reuse → revoke kogu family → ohver peab uuesti login'ima,
 * ründaja ei pääse enam.
 *
 * <h3>Storage</h3>
 * DB'sse salvestame token'i SHA-256 hash, mitte raw. DB leak ei anna
 * ründajale kasutatavaid tokeneid.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RNG = new SecureRandom();
    private static final int TOKEN_BYTES = 32;   // 256-bit entropy
    private static final long TTL_DAYS = 30;

    private final JdbcTemplate jdbc;

    public RefreshTokenService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Anna välja uus token user'ile (login / OAuth2 success). */
    @Transactional
    public String issueNew(long userId, HttpServletRequest req) {
        String raw = randomToken();
        UUID family = UUID.randomUUID();
        Instant expires = Instant.now().plus(TTL_DAYS, ChronoUnit.DAYS);

        jdbc.update(
            "INSERT INTO refresh_tokens " +
            "(user_id, token_hash, family_id, expires_at, client_ip, user_agent) " +
            "VALUES (?, ?, ?::uuid, ?, ?::inet, ?)",
            userId, sha256(raw), family.toString(), java.sql.Timestamp.from(expires),
            clientIp(req), req == null ? null : req.getHeader("User-Agent")
        );
        return raw;
    }

    /**
     * Rotate — kontrollib et token on kehtiv, märgib vana kasutatuks,
     * annab välja uue. Kui keegi proovib sama tokenit uuesti kasutada,
     * siis revoke kogu family.
     *
     * @return new raw token, või empty kui invaliidne
     */
    @Transactional
    public Optional<RotationResult> rotate(String rawToken, HttpServletRequest req) {
        String hash = sha256(rawToken);

        var rows = jdbc.queryForList(
            "SELECT id, user_id, family_id::text AS family_id, revoked_at, expires_at " +
            "FROM refresh_tokens WHERE token_hash = ?", hash);

        if (rows.isEmpty()) return Optional.empty();
        var row = rows.get(0);

        Instant revokedAt = ((java.sql.Timestamp) row.getOrDefault("revoked_at", null)) == null
            ? null : ((java.sql.Timestamp) row.get("revoked_at")).toInstant();
        Instant expiresAt = ((java.sql.Timestamp) row.get("expires_at")).toInstant();
        Long tokenId = ((Number) row.get("id")).longValue();
        Long userId  = ((Number) row.get("user_id")).longValue();
        String familyId = (String) row.get("family_id");

        if (Instant.now().isAfter(expiresAt)) {
            log.debug("Refresh token expired user={} family={}", userId, familyId);
            return Optional.empty();
        }

        // Theft detection: kui token on juba kasutatud (revoked_at != null),
        // aga keegi proovib seda uuesti — reuse → revoke family
        if (revokedAt != null) {
            log.warn("Refresh token REUSE detected — revoking family user={} family={}", userId, familyId);
            jdbc.update(
                "UPDATE refresh_tokens SET revoked_at = COALESCE(revoked_at, NOW()) " +
                "WHERE family_id = ?::uuid AND revoked_at IS NULL",
                familyId);
            return Optional.empty();
        }

        // Happy path: issue new, mark old as revoked + replaced_by
        String newRaw = randomToken();
        Instant newExpires = Instant.now().plus(TTL_DAYS, ChronoUnit.DAYS);
        Long newId = jdbc.queryForObject(
            "INSERT INTO refresh_tokens " +
            "(user_id, token_hash, family_id, expires_at, client_ip, user_agent) " +
            "VALUES (?, ?, ?::uuid, ?, ?::inet, ?) RETURNING id",
            Long.class,
            userId, sha256(newRaw), familyId, java.sql.Timestamp.from(newExpires),
            clientIp(req), req == null ? null : req.getHeader("User-Agent")
        );
        jdbc.update(
            "UPDATE refresh_tokens SET revoked_at = NOW(), replaced_by_id = ? WHERE id = ?",
            newId, tokenId);

        return Optional.of(new RotationResult(newRaw, userId));
    }

    /** Revoke — logout, vm. */
    public void revoke(String rawToken) {
        jdbc.update("UPDATE refresh_tokens SET revoked_at = NOW() " +
                    "WHERE token_hash = ? AND revoked_at IS NULL", sha256(rawToken));
    }

    public void revokeAllForUser(long userId) {
        jdbc.update("UPDATE refresh_tokens SET revoked_at = NOW() " +
                    "WHERE user_id = ? AND revoked_at IS NULL", userId);
    }

    private static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RNG.nextBytes(bytes);
        // URL-safe base64, et saaks panna cookie'sse ilma encoding'uta
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(s.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String clientIp(HttpServletRequest req) {
        if (req == null) return null;
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).strip();
        }
        return req.getRemoteAddr();
    }

    public record RotationResult(String newRefreshToken, long userId) {}
}
