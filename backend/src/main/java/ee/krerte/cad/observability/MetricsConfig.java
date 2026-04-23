package ee.krerte.cad.observability;

import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer konfiguratsioon — @Timed ja @Counted annotatsioonide AOP tugi, default tag'id iga
 * meetriku juurde (env, app), ning cardinality-kaitse.
 *
 * <p>Kui lisad @Timed klassi või meetodi peale, Micrometer loob automaatselt histogram'i
 * (p50/p95/p99) + counter'i. Tehniliselt selles app'is kasutame seda peamiselt {@link
 * ClaudeCostMetrics} ja väljapoole minevatele klientidele ({@link
 * ee.krerte.cad.observability.ExternalApiMetrics}).
 */
@Configuration
public class MetricsConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public CountedAspect countedAspect(MeterRegistry registry) {
        return new CountedAspect(registry);
    }

    /**
     * Cardinality guard — URI tag'ide plahvatuse vältimine. Spring MVC auto-tagib {@code
     * http.server.requests} URI mustriga (nt /api/designs/{id}), mis peaks olema ok, aga kui mõni
     * controller võtab kasutaja-sisendiga path variable (nt /api/search/{query}), siis see võib
     * tekitada miljoneid unikaalseid tag väärtusi. Katkestame 100 unikaalse URI juures — ülejäänud
     * märgitud kui "other".
     */
    @Bean
    public MeterFilter uriCardinalityFilter() {
        return MeterFilter.maximumAllowableTags(
                "http.server.requests", "uri", 100, MeterFilter.deny());
    }
}
