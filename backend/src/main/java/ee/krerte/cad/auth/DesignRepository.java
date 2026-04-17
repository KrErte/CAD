package ee.krerte.cad.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DesignRepository extends JpaRepository<Design, Long> {
    Page<Design> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    long countByUserId(Long userId);

    @Query("SELECT COUNT(d) FROM Design d WHERE CAST(d.createdAt AS string) LIKE ?1%")
    long countByCreatedAtMonth(String yearMonth);
}
