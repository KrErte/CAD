package ee.krerte.cad.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) { this.jwt = jwt; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws java.io.IOException, jakarta.servlet.ServletException {
        String h = req.getHeader("Authorization");
        String token = null;
        if (h != null && h.startsWith("Bearer ")) token = h.substring(7);
        // Fallback: ?token=... for <a download> style links (can't set headers).
        if (token == null) token = req.getParameter("token");
        if (token != null) {
            try {
                Claims c = jwt.parse(token);
                Long uid = Long.valueOf(c.getSubject());
                String plan = c.get("plan", String.class);
                var auth = new UsernamePasswordAuthenticationToken(
                        uid, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + (plan == null ? "FREE" : plan))));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {}
        }
        chain.doFilter(req, res);
    }
}
