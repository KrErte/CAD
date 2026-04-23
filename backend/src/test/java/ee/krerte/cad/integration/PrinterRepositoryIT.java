package ee.krerte.cad.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ee.krerte.cad.printflow.entity.Printer;
import ee.krerte.cad.printflow.repo.PrinterRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test päris Postgres'iga (Testcontainers).
 *
 * <p>Valideerime, et:
 *
 * <ol>
 *   <li>Flyway migratsioonid jooksevad puhtalt
 *   <li>JPA @Column mapping'ud (nt {@code bed_temp_c numeric(5,2)}) ei konflikti Hibernate
 *       ddl-auto=validate'iga
 *   <li>Custom query meetodid (findByOrganizationIdOrderByNameAsc) genereerivad päris Postgres'ile
 *       töötava SQL'i (mitte H2-dialect-only)
 * </ol>
 */
@DisplayName("PrinterRepository + Postgres")
class PrinterRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired private PrinterRepository repo;

    @BeforeEach
    void cleanup() {
        repo.deleteAll();
    }

    @Test
    @DisplayName("Salvestab ja loeb printer'i koos kõigi väljadega")
    void saveAndLoad() {
        Printer p = new Printer();
        p.setOrganizationId(42L);
        p.setName("Prusa MK4");
        p.setVendor("Prusa");
        p.setModel("MK4S");
        p.setAdapterType("PRUSA_CONNECT");
        p.setStatus("IDLE");

        Printer saved = repo.save(p);

        assertThat(saved.getId()).isNotNull();
        Printer loaded = repo.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Prusa MK4");
        assertThat(loaded.getBuildVolumeXmm()).isEqualTo(220); // default from @Column
        assertThat(loaded.getSupportedMaterialFamilies()).isEqualTo("PLA,PETG");
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("findByOrganizationIdOrderByNameAsc — isoleerib org'ide vahel")
    void tenantIsolation() {
        repo.save(newPrinter(1L, "Bambu X1", "IDLE"));
        repo.save(newPrinter(1L, "Prusa MK4", "PRINTING"));
        repo.save(newPrinter(2L, "Voron 2.4", "IDLE")); // teine tenant

        List<Printer> orgOne = repo.findByOrganizationIdOrderByNameAsc(1L);
        assertThat(orgOne).hasSize(2);
        assertThat(orgOne).extracting(Printer::getName).containsExactly("Bambu X1", "Prusa MK4");

        List<Printer> orgTwo = repo.findByOrganizationIdOrderByNameAsc(2L);
        assertThat(orgTwo).hasSize(1);
    }

    @Test
    @DisplayName("countByOrganizationIdAndStatus — agregeerib status'e järgi")
    void countByStatus() {
        repo.save(newPrinter(7L, "a", "PRINTING"));
        repo.save(newPrinter(7L, "b", "PRINTING"));
        repo.save(newPrinter(7L, "c", "IDLE"));

        assertThat(repo.countByOrganizationIdAndStatus(7L, "PRINTING")).isEqualTo(2);
        assertThat(repo.countByOrganizationIdAndStatus(7L, "IDLE")).isEqualTo(1);
        assertThat(repo.countByOrganizationIdAndStatus(7L, "ERROR")).isZero();
    }

    private static Printer newPrinter(Long orgId, String name, String status) {
        Printer p = new Printer();
        p.setOrganizationId(orgId);
        p.setName(name);
        p.setStatus(status);
        return p;
    }
}
