package ee.krerte.cad.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;

/**
 * Kogub kõigi providerite hinnapakkumised PARALLEELSELT ühte päringu sisse.
 *
 * <p>Iga provider kutsutakse eraldi scheduler'il (boundedElastic), nii et
 * aeglane provider EI blokeeri teisi. Lõpus {@code Flux.merge} + 10s globaalne
 * timeout + collectList + sortedByPrice.
 *
 * <p>Ebaõnnestunud providerid tagastavad ise fallback'i; globaalne timeout
 * on viimane kaitsemüür, et kontroller ei jääks lõpmatult oodata.
 */
@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);
    private static final Duration GLOBAL_TIMEOUT = Duration.ofSeconds(10);

    private final List<PricingProvider> providers;

    public PricingService(List<PricingProvider> providers) {
        this.providers = providers;
        log.info("PricingService initialized with {} providers: {}",
                providers.size(), providers.stream().map(PricingProvider::providerId).toList());
    }

    public Mono<PricingCompareResponse> compareAll(PricingCompareRequest request) {
        long t0 = System.currentTimeMillis();

        List<Mono<PricingQuote>> calls = providers.stream()
                .map(p -> p.quote(request)
                        .subscribeOn(Schedulers.boundedElastic())
                        // Kaitse selle vastu, et üksik provider viib service'i kinni.
                        // Provider ISE peab juba oma error'id ohutult käitlema,
                        // aga sanity-net.
                        .onErrorResume(ex -> {
                            log.warn("Provider {} leaked an exception: {}", p.providerId(), ex.getMessage());
                            return Mono.just(PricingQuote.error(
                                    p.providerId(), p.displayName(),
                                    "unexpected error: " + ex.getClass().getSimpleName(),
                                    System.currentTimeMillis() - t0));
                        }))
                .toList();

        return Flux.merge(calls)
                .collectList()
                .timeout(GLOBAL_TIMEOUT, Mono.fromSupplier(() -> {
                    log.warn("Global pricing timeout reached after {}ms", GLOBAL_TIMEOUT.toMillis());
                    return providers.stream()
                            .map(p -> PricingQuote.error(p.providerId(), p.displayName(),
                                    "global timeout", GLOBAL_TIMEOUT.toMillis()))
                            .toList();
                }))
                .map(quotes -> {
                    int totalMs = (int) (System.currentTimeMillis() - t0);
                    log.info("compareAll: {} quotes in {}ms", quotes.size(), totalMs);
                    return PricingCompareResponse.of(quotes, totalMs);
                });
    }
}
