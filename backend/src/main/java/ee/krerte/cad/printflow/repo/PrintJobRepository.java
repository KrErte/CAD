package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.PrintJob;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrintJobRepository extends JpaRepository<PrintJob, Long> {
    List<PrintJob> findByOrganizationIdOrderByQueuedAtDesc(Long organizationId);

    Optional<PrintJob> findByIdAndOrganizationId(Long id, Long organizationId);

    List<PrintJob> findByOrganizationIdAndStatus(Long organizationId, String status);

    List<PrintJob> findByPrinterIdAndStatus(Long printerId, String status);

    /**
     * Scheduler algoritm: leia järgmine eelistusega queue-töö konkreetse organisatsiooni ja
     * materjali kohta. Sorteeritud prioriteedi (desc) ja queuing-aja (asc) järgi.
     */
    @Query(
            """
            select j from PrintJob j
            where j.organizationId = :orgId
              and j.status = 'QUEUED'
              and j.materialId = :materialId
            order by j.priority desc, j.queuedAt asc
            """)
    List<PrintJob> findNextQueued(@Param("orgId") Long orgId, @Param("materialId") Long materialId);

    /** Kõik QUEUED jobid org-is (kui materjali-filtrit pole). */
    List<PrintJob> findByOrganizationIdAndStatusOrderByPriorityDescQueuedAtAsc(
            Long orgId, String status);

    @Query(
            """
            select count(j) from PrintJob j
            where j.organizationId = :orgId
              and j.status in :statuses
              and j.queuedAt >= :since
            """)
    long countActiveSince(
            @Param("orgId") Long orgId,
            @Param("statuses") List<String> statuses,
            @Param("since") Instant since);
}
