package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.QuoteLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuoteLineRepository extends JpaRepository<QuoteLine, Long> {
    List<QuoteLine> findByQuoteIdOrderByLineNoAsc(Long quoteId);
}
