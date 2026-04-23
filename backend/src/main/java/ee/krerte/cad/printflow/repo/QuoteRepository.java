package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.Quote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteRepository extends JpaRepository<Quote, Long> {
    List<Quote> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    Optional<Quote> findByIdAndOrganizationId(Long id, Long organizationId);

    Optional<Quote> findByPublicToken(String publicToken);

    List<Quote> findByOrganizationIdAndStatus(Long organizationId, String status);

    long countByOrganizationIdAndStatus(Long organizationId, String status);

    long countByOrganizationId(Long organizationId);
}
