package ee.krerte.cad.audit;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByActorUserIdOrderByCreatedAtDesc(Long actorUserId, Pageable pg);

    Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pg);

    @Query(
            "SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :from AND :to "
                    + "ORDER BY a.createdAt DESC")
    Page<AuditLog> findByDateRange(Instant from, Instant to, Pageable pg);

    /** Retention: vanad read kustutatakse öösel, pärast archive'imist S3'sse. */
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoff")
    void deleteOlderThan(Instant cutoff);
}
