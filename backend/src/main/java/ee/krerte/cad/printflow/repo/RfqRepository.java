package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.Rfq;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RfqRepository extends JpaRepository<Rfq, Long> {
    List<Rfq> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    Optional<Rfq> findByIdAndOrganizationId(Long id, Long organizationId);

    List<Rfq> findByOrganizationIdAndStatus(Long organizationId, String status);

    long countByOrganizationIdAndStatus(Long organizationId, String status);
}
