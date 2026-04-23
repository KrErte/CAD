package ee.krerte.cad.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base klass kõigile integration-testidele, mis vajavad päris Postgres'i.
 *
 * <p>Me kasutame ühte {@code static} container'it kogu test-jooksu jooksul (reuse=true). See on
 * ~20x kiirem kui container per test, ja Testcontainers Flyway'ga teeb automaatselt {@code schema
 * cleanup + migrate} iga testi algul, nii et state ei lekki ühest testist teise.
 *
 * <p>Kasutamise näide:
 *
 * <pre>
 * class MinuRepoIT extends AbstractPostgresIntegrationTest {
 *     &#64;Autowired DesignRepository repo;
 *
 *     &#64;Test
 *     void saveAndLoad() { ... }
 * }
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
public abstract class AbstractPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("cad_test")
                    .withUsername("test")
                    .withPassword("test")
                    // Reuse hoiab container'i live'is JVM'i vahel (kui Docker daemon
                    // toetab reuse mode'i — ~/.testcontainers.properties)
                    .withReuse(true);

    @DynamicPropertySource
    static void postgresProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.flyway.clean-disabled", () -> "false");
    }
}
