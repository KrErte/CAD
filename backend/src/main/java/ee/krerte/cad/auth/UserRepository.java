package ee.krerte.cad.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleSub(String googleSub);

    Optional<User> findByStripeCustomerId(String customerId);

    long countByPlan(User.Plan plan);
}
