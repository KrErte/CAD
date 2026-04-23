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
    private final int freeFreeformMonthly;

    public QuotaService(UserRepository users, UsageRepository usages, EmailService emailService,
                        @Value("${app.quota.free-monthly:3}") int freeMonthly,
                        @Value("${app.quota.free-freeform-monthly:3}") int freeFreeformMonthly) {
        this.users = users;
        this.usages = usages;
        this.emailService = emailService;
        this.freeMonthly = freeMonthly;
        this.freeFreeformMonthly = freeFreeformMonthly;
    }

    public record Status(User.Plan plan, int used, int limit, boolean allowed) {}

    public Status status(Long userId) {
        User u = users.findById(userId).orElseThrow();
        String ym = YearMonth.now().toString();
        int used = usages.findByUserIdAndYearMonth(userId, ym).map(Usage::getStlCount).orElse(0);
        int limit = u.getPlan() == User.Plan.PRO ? Integer.MAX_VALUE : freeMonthly;
        return new Status(u.getPlan(), used, limit, used < limit);
    }

    /**
     * Freeform-kvoota FREE kasutajale. PRO+ on limiidita.
     * FREE saab {@code app.quota.free-freeform-monthly} katset kuus (default 3),
     * et saaks Pro-feature'it prooviks kogeda enne ostu.
     */
    public Status freeformStatus(Long userId) {
        User u = users.findById(userId).orElseThrow();
        String ym = YearMonth.now().toString();
        int used = usages.findByUserIdAndYearMonth(userId, ym).map(Usage::getFreeformCount).orElse(0);
        int limit = u.getPlan() == User.Plan.PRO ? Integer.MAX_VALUE : freeFreeformMonthly;
        return new Status(u.getPlan(), used, limit, used < limit);
    }

    @Transactional
    public void recordFreeform(Long userId) {
        String ym = YearMonth.now().toString();
        Usage usage = usages.findByUserIdAndYearMonth(userId, ym).orElseGet(() -> {
            Usage n = new Usage();
            n.setUserId(userId);
            n.setYearMonth(ym);
            return n;
        });
        usage.incrementFreeform();
        usages.save(usage);
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
