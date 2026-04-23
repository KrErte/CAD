package ee.krerte.cad.auth;

import ee.krerte.cad.mail.EmailService;
import java.time.YearMonth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuotaService {

    private final UserRepository users;
    private final UsageRepository usages;
    private final EmailService emailService;
    private final int freeMonthly;
    private final int proMonthly;
    private final int businessMonthly;

    public QuotaService(
            UserRepository users,
            UsageRepository usages,
            EmailService emailService,
            @Value("${app.quota.free-monthly:3}") int freeMonthly,
            @Value("${app.quota.pro-monthly:50}") int proMonthly,
            @Value("${app.quota.business-monthly:200}") int businessMonthly) {
        this.users = users;
        this.usages = usages;
        this.emailService = emailService;
        this.freeMonthly = freeMonthly;
        this.proMonthly = proMonthly;
        this.businessMonthly = businessMonthly;
    }

    public record Status(User.Plan plan, int used, int limit, boolean allowed) {}

    public int limitForPlan(User.Plan plan) {
        return switch (plan) {
            case BUSINESS -> businessMonthly;
            case PRO -> proMonthly;
            default -> freeMonthly;
        };
    }

    public Status status(Long userId) {
        User u = users.findById(userId).orElseThrow();
        String ym = YearMonth.now().toString();
        int used = usages.findByUserIdAndYearMonth(userId, ym).map(Usage::getStlCount).orElse(0);
        int limit = limitForPlan(u.getPlan());
        return new Status(u.getPlan(), used, limit, used < limit);
    }

    @Transactional
    public void recordStl(Long userId) {
        String ym = YearMonth.now().toString();
        Usage usage =
                usages.findByUserIdAndYearMonth(userId, ym)
                        .orElseGet(
                                () -> {
                                    Usage n = new Usage();
                                    n.setUserId(userId);
                                    n.setYearMonth(ym);
                                    return n;
                                });
        usage.increment();
        usages.save(usage);

        // Send quota warning emails when approaching limit
        User u = users.findById(userId).orElse(null);
        if (u != null) {
            int used = usage.getStlCount();
            int limit = limitForPlan(u.getPlan());
            if (used == limit - 1) {
                emailService.sendQuotaWarning(u.getEmail(), u.getName(), used, limit);
            } else if (used == limit) {
                emailService.sendQuotaExhausted(u.getEmail(), u.getName());
            }
        }
    }
}
