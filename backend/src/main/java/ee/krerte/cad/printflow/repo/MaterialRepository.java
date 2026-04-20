package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long> {
    List<Material> findByOrganizationIdAndActiveOrderByFamilyAscNameAsc(Long organizationId, Boolean active);
    List<Material> findByOrganizationIdOrderByFamilyAscNameAsc(Long organizationId);
    Optional<Material> findByIdAndOrganizationId(Long id, Long organizationId);
    List<Material> findByOrganizationIdAndFamily(Long organizationId, String family);
}
