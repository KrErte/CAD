package ee.krerte.cad.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {
    Optional<UserSubscription> findByUserId(Long userId);
    Optional<UserSubscription> findByStripeCustomerId(String customerId);
    Optional<UserSubscription> findByStripeSubscriptionId(String subscriptionId);
}
