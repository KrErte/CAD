package ee.krerte.cad.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlMs;

    public JwtService(@Value("${app.jwt.secret:change-me-please-change-me-please-32bytes!!}") String secret,
                      @Value("${app.jwt.ttl-hours:24}") long ttlHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMs = ttlHours * 3600 * 1000;
    }

    public String issue(User u) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(u.getId()))
                .claim("email", u.getEmail())
                .claim("plan", u.getPlan().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
