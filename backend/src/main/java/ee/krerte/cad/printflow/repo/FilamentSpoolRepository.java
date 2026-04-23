package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.FilamentSpool;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FilamentSpoolRepository extends JpaRepository<FilamentSpool, Long> {
    List<FilamentSpool> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    Optional<FilamentSpool> findByIdAndOrganizationId(Long id, Long organizationId);

    List<FilamentSpool> findByOrganizationIdAndMaterialIdAndStatusIn(
            Long orgId, Long materialId, List<String> statuses);

    Optional<FilamentSpool> findByAssignedPrinterId(Long printerId);

    /** Leia spool'id, mis on alla mass-limiidi (nt 10% alles) ja aktiivsed. */
    @Query(
            """
            select s from FilamentSpool s
            where s.organizationId = :orgId
              and s.status in ('FULL','PARTIAL')
              and s.massRemainingG <= :thresholdG
            order by s.massRemainingG asc
            """)
    List<FilamentSpool> findLowStock(
            @Param("orgId") Long orgId, @Param("thresholdG") int thresholdG);
}
