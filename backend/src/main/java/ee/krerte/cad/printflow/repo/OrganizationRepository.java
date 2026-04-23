package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.Organization;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findBySlug(String slug);

    List<Organization> findByOwnerUserId(Long ownerUserId);
}
