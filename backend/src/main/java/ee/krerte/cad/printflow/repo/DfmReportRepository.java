package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.DfmReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DfmReportRepository extends JpaRepository<DfmReport, Long> {
    Optional<DfmReport> findByIdAndOrganizationId(Long id, Long organizationId);
}
