package ee.krerte.cad.pricing;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * REST API 3D-print-partnerite hinnavõrdluseks.
 *
 * <p>Tüüpiline kasutus frontend'is:
 * <pre>{@code
 *   POST /api/pricing/compare
 *   {
 *     "volumeCm3": 96.5,
 *     "weightG": 119.7,
 *     "bbox": { "x": 100, "y": 100, "z": 120 },
 *     "material": "PLA",
 *     "infillPercent": 20,
 *     "copies": 1,
 *     "countryCode": "EE"
 *   }
 * }</pre>
 *
 * <p>Vastus sisaldab iga partneri kohta ühte {@link PricingQuote} objekti,
 * pluss tuletatud {@code cheapestProviderId} ja {@code fastestProviderId}.
 */
@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private final PricingService service;
    private final List<PricingProvider> providers;

    public PricingController(PricingService service, List<PricingProvider> providers) {
        this.service = service;
        this.providers = providers;
    }

    @PostMapping("/compare")
    public Mono<ResponseEntity<PricingCompareResponse>> compare(
            @Valid @RequestBody PricingCompareRequest request) {
        return service.compareAll(request).map(ResponseEntity::ok);
    }

    /**
     * Kasutajaliides võib debug'iks vaadata, millised providerid on
     * praegu konfigureeritud (ilma API-võtmeid tagastamata).
     */
    @GetMapping("/providers")
    public List<Map<String, Object>> listProviders() {
        return providers.stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.providerId(),
                        "name", p.displayName(),
                        "enabled", p.enabled()
                ))
                .toList();
    }
}
