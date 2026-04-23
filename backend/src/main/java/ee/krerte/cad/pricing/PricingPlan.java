package ee.krerte.cad.pricing;

public enum PricingPlan {
    // ── Makers (hobbyists, engineers, students) ──
    DEMO,
    MAKER,
    CREATOR,

    // ── Print Bureaus ──
    BUREAU_STARTER,
    BUREAU_STUDIO,
    BUREAU_FACTORY,
    BUREAU_ENTERPRISE,

    // ── Developers (API access) ──
    DEV_TRIAL,
    DEV_GROWTH,
    DEV_BUSINESS;

    /** Map legacy User.Plan values to new pricing plans. */
    public static PricingPlan fromLegacy(String legacyPlan) {
        if (legacyPlan == null) return DEMO;
        return switch (legacyPlan.toUpperCase()) {
            case "FREE" -> MAKER;
            case "PRO" -> CREATOR;
            case "BUSINESS" -> DEV_GROWTH;
            default -> DEMO;
        };
    }
}
