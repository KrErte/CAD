package ee.krerte.cad.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kontrollime live-serveril, et security headers on kohal. Kui keegi
 * tulevikus SecurityConfig'u katki kaoab (või CSP'i kogemata eemaldab),
 * see test lööb alarmi enne, kui prod'is saaks XSS-i lasta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Security headers on HTTP response'il")
class SecurityHeadersIT extends AbstractPostgresIntegrationTest {

    @LocalServerPort int port;

    @Test
    @DisplayName("HSTS, CSP, X-Frame-Options, nosniff lisatud")
    void headersPresent() {
        var headers = WebClient.create("http://localhost:" + port)
                .get().uri("/actuator/health")
                .retrieve()
                .toBodilessEntity()
                .block()
                .getHeaders();

        assertThat(headers.getFirst("Strict-Transport-Security"))
            .as("HSTS")
            .contains("max-age").contains("includeSubDomains");

        assertThat(headers.getFirst("X-Frame-Options"))
            .as("Clickjacking-kaitse").isEqualTo("DENY");

        assertThat(headers.getFirst("X-Content-Type-Options"))
            .as("MIME-sniff kaitse").isEqualTo("nosniff");

        assertThat(headers.getFirst("Content-Security-Policy"))
            .as("CSP")
            .contains("default-src 'self'")
            .contains("frame-ancestors 'none'");

        assertThat(headers.getFirst("Referrer-Policy"))
            .isEqualTo("strict-origin-when-cross-origin");
    }
}
