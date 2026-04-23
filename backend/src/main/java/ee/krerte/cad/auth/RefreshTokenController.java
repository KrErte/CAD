package ee.krerte.cad.auth;

import ee.krerte.cad.audit.AuditService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Refresh + logout endpoint'id.
 *
 * <p><b>Cookie</b>: refresh-token on HttpOnly, Secure, SameSite=Strict cookie'sse. Frontend JS ei
 * puutu seda, XSS ei saa varastada. Backend loeb cookie kaudu.
 *
 * <p><b>POST /api/auth/refresh</b>:
 *
 * <ul>
 *   <li>Loeb cookie'st vana refresh tokeni
 *   <li>Rotate ⇒ uus access JWT (15 min) + uus refresh cookie (30 päeva)
 *   <li>Kui theft detected, ÜKSKI token ei tööta — user peab uuesti login'ima
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class RefreshTokenController {

    public static final String COOKIE_NAME = "cad_refresh";

    private final RefreshTokenService refreshService;
    private final JwtService jwtService;
    private final AuditService audit;

    public RefreshTokenController(
            RefreshTokenService refreshService, JwtService jwtService, AuditService audit) {
        this.refreshService = refreshService;
        this.jwtService = jwtService;
        this.audit = audit;
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest req, HttpServletResponse resp) {
        String raw = readCookie(req);
        if (raw == null) {
            audit.record(
                    "TOKEN_REFRESH",
                    "refresh_token",
                    null,
                    "FAILURE",
                    Map.of("reason", "no_cookie"));
            return ResponseEntity.status(401).body(Map.of("error", "no_refresh_token"));
        }

        var result = refreshService.rotate(raw, req);
        if (result.isEmpty()) {
            // Theft OR expired OR unknown — ka audit-kirje, et SIEM nägeks
            audit.record(
                    "TOKEN_REFRESH",
                    "refresh_token",
                    null,
                    "DENIED",
                    Map.of("reason", "invalid_or_reused"));
            clearCookie(resp);
            return ResponseEntity.status(401).body(Map.of("error", "invalid_refresh_token"));
        }

        var r = result.get();
        String accessJwt = jwtService.generate(r.userId());
        setCookie(resp, r.newRefreshToken());

        audit.record("TOKEN_REFRESH", "refresh_token", r.userId(), "SUCCESS");
        return ResponseEntity.ok(Map.of("access_token", accessJwt, "expires_in", 15 * 60));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest req, HttpServletResponse resp) {
        String raw = readCookie(req);
        if (raw != null) refreshService.revoke(raw);
        clearCookie(resp);
        audit.record("LOGOUT", "user", null, "SUCCESS");
        return ResponseEntity.noContent().build();
    }

    private static String readCookie(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        for (Cookie c : req.getCookies()) {
            if (COOKIE_NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private static void setCookie(HttpServletResponse resp, String value) {
        // Manually setame et saaks SameSite=Strict — Cookie API ei toeta seda
        // enne Servlet 6 javax'iga
        String cookie =
                String.format(
                        "%s=%s; Path=/api/auth; Max-Age=%d; HttpOnly; Secure; SameSite=Strict",
                        COOKIE_NAME, value, 30 * 24 * 3600);
        resp.addHeader("Set-Cookie", cookie);
    }

    private static void clearCookie(HttpServletResponse resp) {
        resp.addHeader(
                "Set-Cookie",
                COOKIE_NAME + "=; Path=/api/auth; Max-Age=0; HttpOnly; Secure; SameSite=Strict");
    }
}
