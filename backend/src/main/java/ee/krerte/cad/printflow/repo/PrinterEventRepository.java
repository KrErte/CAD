package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.PrinterEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrinterEventRepository extends JpaRepository<PrinterEvent, Long> {
    Page<PrinterEvent> findByPrinterIdOrderByOccurredAtDesc(Long printerId, Pageable pageable);
}
