package ee.krerte.cad.gallery;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PrintOrderRepository extends JpaRepository<PrintOrder, Long> {
    List<PrintOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<PrintOrder> findByStatusOrderByCreatedAtAsc(String status);
}
