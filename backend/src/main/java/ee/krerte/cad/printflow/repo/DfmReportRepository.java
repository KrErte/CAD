package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.DfmReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DfmReportRepository extends JpaRepository<DfmReport, Long> {
    Optional<DfmReport> findByIdAndOrganizationId(Long id, Long organizationId);
}
