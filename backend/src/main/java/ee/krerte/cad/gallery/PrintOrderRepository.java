package ee.krerte.cad.gallery;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintOrderRepository extends JpaRepository<PrintOrder, Long> {
    List<PrintOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PrintOrder> findByStatusOrderByCreatedAtAsc(String status);
}
