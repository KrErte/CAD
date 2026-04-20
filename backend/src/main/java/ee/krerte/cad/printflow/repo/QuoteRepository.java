package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.Quote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuoteRepository extends JpaRepository<Quote, Long> {
    List<Quote> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
    Optional<Quote> findByIdAndOrganizationId(Long id, Long organizationId);
    Optional<Quote> findByPublicToken(String publicToken);
    List<Quote> findByOrganizationIdAndStatus(Long organizationId, String status);
    long countByOrganizationIdAndStatus(Long organizationId, String status);
    long countByOrganizationId(Long organizationId);
}
