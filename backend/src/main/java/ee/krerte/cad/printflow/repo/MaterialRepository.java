package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.Material;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialRepository extends JpaRepository<Material, Long> {
    List<Material> findByOrganizationIdAndActiveOrderByFamilyAscNameAsc(
            Long organizationId, Boolean active);

    List<Material> findByOrganizationIdOrderByFamilyAscNameAsc(Long organizationId);

    Optional<Material> findByIdAndOrganizationId(Long id, Long organizationId);

    List<Material> findByOrganizationIdAndFamily(Long organizationId, String family);
}
