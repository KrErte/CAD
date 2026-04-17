package ee.krerte.cad.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleSub(String googleSub);
    Optional<User> findByStripeCustomerId(String customerId);
    long countByPlan(User.Plan plan);
}
