package ee.krerte.cad;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Talks to the Python slicer sidecar — turns an STL into estimated print time, filament mass and
 * EUR cost by shelling out to PrusaSlicer CLI.
 *
 * <p>The sidecar is OPTIONAL: when SLICER_URL is blank the backend falls back to the worker's
 * volume-based heuristic (see {@link DesignController#preview}).
 */
@Component
public class SlicerClient {

    private static final Logger log = LoggerFactory.getLogger(SlicerClient.class);

    private final WebClient webClient;

    @Value("${app.slicer.url:}")
    private String slicerUrl;

    @Value("${app.slicer.timeout-seconds:150}")
    private int timeoutSeconds;

    public SlicerClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public boolean enabled() {
        return slicerUrl != null && !slicerUrl.isBlank();
    }

    /**
     * Slices the given STL with the given preset and optional overrides. Returns the JSON payload
     * emitted by the sidecar (see slicer/app.py).
     */
    public JsonNode slice(byte[] stl, String preset, String fillDensity, String layerHeight) {
        if (!enabled()) {
            throw new IllegalStateException("SLICER_URL not configured");
        }

        MultipartBodyBuilder mb = new MultipartBodyBuilder();
        mb.part(
                        "stl",
                        new ByteArrayResource(stl) {
                            @Override
                            public String getFilename() {
                                return "model.stl";
                            }
                        })
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        if (preset != null && !preset.isBlank()) mb.part("preset", preset);
        if (fillDensity != null && !fillDensity.isBlank()) mb.part("fill_density", fillDensity);
        if (layerHeight != null && !layerHeight.isBlank()) mb.part("layer_height", layerHeight);

        try {
            return webClient
                    .post()
                    .uri(slicerUrl + "/slice")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(mb.build()))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (WebClientResponseException e) {
            log.warn("Slicer returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.warn("Slicer call failed: {}", e.getMessage());
            throw new RuntimeException("Slicer unavailable: " + e.getMessage(), e);
        }
    }
}
