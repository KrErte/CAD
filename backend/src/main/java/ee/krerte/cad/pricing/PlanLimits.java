package ee.krerte.cad.pricing;

/**
 * Immutable limits for a pricing plan.
 *
 * @param generationsPerMonth monthly generation cap (-1 = unlimited)
 * @param reviewsPerMonth     monthly AI review cap (-1 = unlimited)
 * @param meshyPerMonth       monthly Meshy (free-form) cap (-1 = unlimited)
 * @param ordersPerMonth      monthly print orders cap (-1 = unlimited, 0 = N/A)
 * @param maxPrinters         max managed printers (-1 = unlimited, 0 = N/A)
 * @param ratePerMinute       API rate limit per minute (-1 = unlimited)
 * @param trialDays           trial period in days (0 = no trial)
 * @param totalGenerations    lifetime generation cap (-1 = unlimited)
 * @param dailyLimit          daily cap for demo mode (-1 = unlimited)
 */
public record PlanLimits(
        int generationsPerMonth,
        int reviewsPerMonth,
        int meshyPerMonth,
        int ordersPerMonth,
        int maxPrinters,
        int ratePerMinute,
        int trialDays,
        int totalGenerations,
        int dailyLimit) {

    /** Convenience constructor for Maker-tier plans (no bureau/API fields). */
    public static PlanLimits maker(int gens, int reviews, int meshy) {
        return new PlanLimits(gens, reviews, meshy, 0, 0, -1, 0, -1, -1);
    }

    /** Convenience constructor for Bureau-tier plans. */
    public static PlanLimits bureau(int orders, int printers) {
        return new PlanLimits(-1, -1, -1, orders, printers, -1, 0, -1, -1);
    }

    /** Convenience constructor for Developer-tier plans. */
    public static PlanLimits dev(int gensPerMonth, int ratePerMin, int trialDays, int totalGens) {
        return new PlanLimits(gensPerMonth, -1, -1, 0, 0, ratePerMin, trialDays, totalGens, -1);
    }

    /** Demo: daily-limited, no review, no meshy. */
    public static PlanLimits demo(int dailyLimit) {
        return new PlanLimits(0, 0, 0, 0, 0, -1, 0, -1, dailyLimit);
    }
}
