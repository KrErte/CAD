package ee.krerte.cad.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PromptHistoryRepository extends JpaRepository<PromptHistory, Long> {

    /**
     * Similarity-otsing kasutaja prompti suhtes. Kasutame Postgresi
     * {@code pg_trgm} extensioni {@code similarity()} funktsiooni — see
     * arvutab trigramm-overlapi kahe stringi vahel, skooriga 0.0..1.0.
     *
     * <p>Tagastame top-N edukaid pretsedente (downloaded=true saab bonus),
     * et siis neist top-template'id välja ekstrakteerida.
     *
     * <p>Miks native query? JPA JPQL ei toeta {@code similarity()} funktsiooni
     * ilma custom dialectita. Native on lihtsam ja auditeeritavam.
     */
    @Query(value = """
            SELECT *, similarity(prompt_et, :q) AS sim
            FROM prompt_history
            WHERE prompt_et % :q
            ORDER BY (CASE WHEN downloaded THEN 2 ELSE 1 END)
                     * similarity(prompt_et, :q)
                     DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<PromptHistory> searchSimilar(@Param("q") String query, @Param("limit") int limit);

    /**
     * Fallback — kui pg_trgm pole saadaval (vana Postgres install),
     * langeme tsvector-otsingu peale. Täpne, aga ei saa osalisi sõnu
     * (misspellings).
     */
    @Query(value = """
            SELECT *
            FROM prompt_history
            WHERE tsv @@ plainto_tsquery('simple', :q)
            ORDER BY (CASE WHEN downloaded THEN 2 ELSE 1 END) *
                     ts_rank(tsv, plainto_tsquery('simple', :q))
                     DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<PromptHistory> searchByTsvector(@Param("q") String query, @Param("limit") int limit);

    long countByTemplate(String template);
}
