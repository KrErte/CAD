package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
    Optional<Customer> findByIdAndOrganizationId(Long id, Long organizationId);
    Optional<Customer> findByOrganizationIdAndEmail(Long organizationId, String email);
    Optional<Customer> findByLinkedUserId(Long linkedUserId);
    long countByOrganizationId(Long organizationId);
}
