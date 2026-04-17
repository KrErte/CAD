package ee.krerte.cad.auth;

import ee.krerte.cad.mail.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

@Service
public class QuotaService {

    private final UserRepository users;
    private final UsageRepository usages;
    private final EmailService emailService;
    private final int freeMonthly;

    public QuotaService(UserRepository users, UsageRepository usages, EmailService emailService,
                        @Value("${app.quota.free-monthly:3}") int freeMonthly) {
        this.users = users;
        this.usages = usages;
        this.emailService = emailService;
        this.freeMonthly = freeMonthly;
    }

    public record Status(User.Plan plan, int used, int limit, boolean allowed) {}

    public Status status(Long userId) {
        User u = users.findById(userId).orElseThrow();
        String ym = YearMonth.now().toString();
        int used = usages.findByUserIdAndYearMonth(userId, ym).map(Usage::getStlCount).orElse(0);
        int limit = u.getPlan() == User.Plan.PRO ? Integer.MAX_VALUE : freeMonthly;
        return new Status(u.getPlan(), used, limit, used < limit);
    }

    @Transactional
    public void recordStl(Long userId) {
        String ym = YearMonth.now().toString();
        Usage usage = usages.findByUserIdAndYearMonth(userId, ym).orElseGet(() -> {
            Usage n = new Usage();
            n.setUserId(userId);
            n.setYearMonth(ym);
            return n;
        });
        usage.increment();
        usages.save(usage);

        // Send quota warning emails for FREE users
        User u = users.findById(userId).orElse(null);
        if (u != null && u.getPlan() == User.Plan.FREE) {
            int used = usage.getStlCount();
            if (used == freeMonthly - 1) {
                emailService.sendQuotaWarning(u.getEmail(), u.getName(), used, freeMonthly);
            } else if (used == freeMonthly) {
                emailService.sendQuotaExhausted(u.getEmail(), u.getName());
            }
        }
    }
}
