package ee.krerte.cad.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class MeController {

    private final UserRepository users;
    private final QuotaService quotas;

    public MeController(UserRepository users, QuotaService quotas) {
        this.users = users;
        this.quotas = quotas;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Long uid = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User u = users.findById(uid).orElseThrow();
        QuotaService.Status s = quotas.status(uid);
        return ResponseEntity.ok(Map.of(
                "id", u.getId(),
                "email", u.getEmail(),
                "name", u.getName() == null ? "" : u.getName(),
                "plan", u.getPlan().name(),
                "used", s.used(),
                "limit", s.limit()
        ));
    }
}
