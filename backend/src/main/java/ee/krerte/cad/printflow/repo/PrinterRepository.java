package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.Printer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrinterRepository extends JpaRepository<Printer, Long> {
    List<Printer> findByOrganizationIdOrderByNameAsc(Long organizationId);

    Optional<Printer> findByIdAndOrganizationId(Long id, Long organizationId);

    List<Printer> findByOrganizationIdAndStatus(Long organizationId, String status);

    long countByOrganizationIdAndStatus(Long organizationId, String status);
}
