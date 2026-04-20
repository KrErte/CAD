package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.BuildPlate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BuildPlateRepository extends JpaRepository<BuildPlate, Long> {
    List<BuildPlate> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
    Optional<BuildPlate> findByIdAndOrganizationId(Long id, Long organizationId);
    List<BuildPlate> findByPrinterIdAndStatus(Long printerId, String status);
}
