package ee.krerte.cad.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Pattern EMAIL_RE =
        Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(UserRepository userRepo,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          RefreshTokenService refreshTokenService) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public record RegisterRequest(String email, String name, String password) {}
    public record LoginRequest(String email, String password) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest body,
                                      HttpServletRequest req,
                                      HttpServletResponse resp) {
        if (body.email() == null || !EMAIL_RE.matcher(body.email().trim()).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid email"));
        }
        if (body.password() == null || body.password().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 8 characters"));
        }
        String email = body.email().trim().toLowerCase();
        if (userRepo.findByEmail(email).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "Email already registered"));
        }

        var user = new User();
        user.setEmail(email);
        user.setName(body.name() != null ? body.name().trim() : email.split("@")[0]);
        user.setPasswordHash(passwordEncoder.encode(body.password()));
        user = userRepo.save(user);

        return issueTokens(user, req, resp);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest body,
                                   HttpServletRequest req,
                                   HttpServletResponse resp) {
        if (body.email() == null || body.password() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        var optUser = userRepo.findByEmail(body.email().trim().toLowerCase());
        if (optUser.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        var user = optUser.get();
        if (user.getPasswordHash() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "This account uses Google login"));
        }
        if (!passwordEncoder.matches(body.password(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }

        return issueTokens(user, req, resp);
    }

    private ResponseEntity<?> issueTokens(User user, HttpServletRequest req, HttpServletResponse resp) {
        String jwt = jwtService.issue(user);
        String refresh = refreshTokenService.issueNew(user.getId(), req);

        // Set refresh cookie same way as RefreshTokenController
        String cookie = String.format(
            "%s=%s; Path=/api/auth; Max-Age=%d; HttpOnly; Secure; SameSite=Strict",
            RefreshTokenController.COOKIE_NAME, refresh, 30 * 24 * 3600);
        resp.addHeader("Set-Cookie", cookie);

        return ResponseEntity.ok(Map.of("access_token", jwt, "expires_in", 15 * 60));
    }
}
