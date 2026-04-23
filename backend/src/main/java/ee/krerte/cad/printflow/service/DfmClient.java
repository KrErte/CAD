package ee.krerte.cad.printflow.service;

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

/**
 * Talks to the Python worker's /dfm endpoint. Sends the STL bytes and gets back a structured DFM
 * report (wall thickness, overhang%, watertightness, issues list).
 */
@Component
public class DfmClient {

    private static final Logger log = LoggerFactory.getLogger(DfmClient.class);

    private final WebClient webClient;

    @Value("${app.worker.url}")
    private String workerUrl;

    public DfmClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Saada STL DFM analüüsiks. Kui materjali-piirid on antud, kasutab worker neid issue-rules'is
     * (nt. min_wall_mm).
     */
    public JsonNode analyze(byte[] stl, String fileName, Double minWallMm, Integer maxOverhangDeg) {
        MultipartBodyBuilder mb = new MultipartBodyBuilder();
        mb.part(
                        "stl",
                        new ByteArrayResource(stl) {
                            @Override
                            public String getFilename() {
                                return fileName != null ? fileName : "part.stl";
                            }
                        })
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        if (minWallMm != null) mb.part("min_wall_mm", String.valueOf(minWallMm));
        if (maxOverhangDeg != null) mb.part("max_overhang_deg", String.valueOf(maxOverhangDeg));

        try {
            return webClient
                    .post()
                    .uri(workerUrl + "/dfm")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(mb.build()))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();
        } catch (Exception e) {
            log.warn("DFM call failed: {}", e.getMessage());
            throw new RuntimeException("DFM analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Nesting (2D packing) — saadab build-plaadi mõõdud + osade bbox'id, saab tagasi paigutuse (x,
     * y, rotation).
     */
    public JsonNode nest(JsonNode body) {
        return webClient
                .post()
                .uri(workerUrl + "/nest")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }
}
