package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.QuoteLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteLineRepository extends JpaRepository<QuoteLine, Long> {
    List<QuoteLine> findByQuoteIdOrderByLineNoAsc(Long quoteId);
}
