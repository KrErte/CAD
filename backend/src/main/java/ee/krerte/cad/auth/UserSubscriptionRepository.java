package ee.krerte.cad.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {
    Optional<UserSubscription> findByUserId(Long userId);

    Optional<UserSubscription> findByStripeCustomerId(String customerId);

    Optional<UserSubscription> findByStripeSubscriptionId(String subscriptionId);
}
