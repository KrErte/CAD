package ee.krerte.cad.admin;

import ee.krerte.cad.auth.DesignRepository;
import ee.krerte.cad.auth.UsageRepository;
import ee.krerte.cad.auth.User;
import ee.krerte.cad.auth.UserRepository;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository users;
    private final UsageRepository usages;
    private final DesignRepository designs;
    private final List<String> adminEmails;

    public AdminController(
            UserRepository users,
            UsageRepository usages,
            DesignRepository designs,
            @Value("${app.admin.emails:}") String adminEmailsCsv) {
        this.users = users;
        this.usages = usages;
        this.designs = designs;
        this.adminEmails =
                adminEmailsCsv.isBlank()
                        ? List.of()
                        : List.of(adminEmailsCsv.split(",")).stream().map(String::trim).toList();
    }

    private void checkAdmin(Long userId) {
        User u = users.findById(userId).orElseThrow();
        if (adminEmails.isEmpty() || !adminEmails.contains(u.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@AuthenticationPrincipal Long userId) {
        checkAdmin(userId);
        String currentMonth = YearMonth.now().toString();

        long totalUsers = users.count();
        long proUsers = users.countByPlan(User.Plan.PRO);
        long totalDesigns = designs.count();
        long thisMonthDesigns = designs.countByCreatedAtMonth(currentMonth);
        long thisMonthActiveUsers = usages.countDistinctUsersByYearMonth(currentMonth);

        return Map.of(
                "totalUsers", totalUsers,
                "proUsers", proUsers,
                "freeUsers", totalUsers - proUsers,
                "totalDesigns", totalDesigns,
                "thisMonthDesigns", thisMonthDesigns,
                "thisMonthActiveUsers", thisMonthActiveUsers,
                "currentMonth", currentMonth);
    }

    @GetMapping("/users")
    public List<Map<String, Object>> userList(@AuthenticationPrincipal Long userId) {
        checkAdmin(userId);
        String currentMonth = YearMonth.now().toString();

        return users.findAll().stream()
                .map(
                        u -> {
                            int used =
                                    usages.findByUserIdAndYearMonth(u.getId(), currentMonth)
                                            .map(usage -> usage.getStlCount())
                                            .orElse(0);
                            long designCount = designs.countByUserId(u.getId());
                            return Map.<String, Object>of(
                                    "id", u.getId(),
                                    "email", u.getEmail(),
                                    "name", u.getName() != null ? u.getName() : "",
                                    "plan", u.getPlan().name(),
                                    "stlThisMonth", used,
                                    "totalDesigns", designCount,
                                    "createdAt", u.getCreatedAt().toString());
                        })
                .toList();
    }
}
