package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, Long> {
    List<WebhookSubscription> findByOrganizationIdAndActive(Long organizationId, Boolean active);
    List<WebhookSubscription> findByOrganizationId(Long organizationId);
    Optional<WebhookSubscription> findByIdAndOrganizationId(Long id, Long organizationId);
}
