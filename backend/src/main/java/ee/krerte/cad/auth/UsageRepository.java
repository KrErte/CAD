package ee.krerte.cad.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UsageRepository extends JpaRepository<Usage, Long> {
    Optional<Usage> findByUserIdAndYearMonth(Long userId, String yearMonth);

    @Query("SELECT COUNT(DISTINCT u.userId) FROM Usage u WHERE u.yearMonth = ?1")
    long countDistinctUsersByYearMonth(String yearMonth);
}
