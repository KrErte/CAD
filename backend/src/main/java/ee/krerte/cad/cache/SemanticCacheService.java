package ee.krerte.cad.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Semantic cache — RAG-lite Claude-kulu vähenduseks.
 *
 * <p>Idee: kui kasutaja kirjutab "20mm kuup", siis 10min hiljem keegi teine
 * kirjutab "kuup 20mm suurune", on vastus sisuliselt sama. Claude maksab
 * ~0.01€ per spec, ja kui meil on 10k kasutajat, säästame embedding-lookup'iga
 * ~90% korduvatest kõnedest.
 *
 * <p>Algoritm:
 * <ol>
 *   <li>Hash prompti (exact match kõige kiirem) — kui leiame, tagastame kohe</li>
 *   <li>Embeddi prompt 1536-dim vektoriks (Voyage / OpenAI)</li>
 *   <li>Otsi pgvector'ist lähim prompt, cosine distance &lt; 0.05 = samasugune</li>
 *   <li>Kui ei leia, kutsume Claude'i ja salvestame uue embedding'u</li>
 * </ol>
 *
 * <p>See klass ei tee ise embedding-API kõnet — {@link EmbeddingClient}
 * (eraldi) teeb. Siin on ainult lookup + store Postgres'is.
 */
@Service
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);

    /** Cosine distance alla selle piiri = "piisavalt sarnane" et cached spec'i kasutada. */
    private static final double SIMILARITY_THRESHOLD = 0.05;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SemanticCacheService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * Otsi sarnase prompti järgi cached spec'i.
     *
     * @param prompt       normaliseeritud kasutaja-prompt
     * @param embedding    1536-dim vector prompt'ist
     * @return             JSON spec kui leidsime sarnase (&lt; threshold), muidu empty
     */
    public Optional<JsonNode> lookup(String prompt, float[] embedding) {
        // 1. Exact-hash path — kiireim
        String hash = sha256(prompt);
        try {
            var row = jdbc.queryForMap(
                "UPDATE prompt_embeddings " +
                "SET hit_count = hit_count + 1, last_hit_at = NOW() " +
                "WHERE prompt_hash = ? " +
                "RETURNING spec_json::text AS spec",
                hash
            );
            log.debug("Semantic cache EXACT hit prompt_hash={}", hash);
            return Optional.of(mapper.readTree((String) row.get("spec")));
        } catch (Exception ignored) { /* fall through */ }

        // 2. Vector similarity path
        try {
            var vec = new PGvector(embedding);
            var row = jdbc.queryForMap(
                "SELECT id, spec_json::text AS spec, embedding <=> ? AS distance " +
                "FROM prompt_embeddings " +
                "ORDER BY embedding <=> ? " +
                "LIMIT 1",
                vec, vec
            );
            double distance = ((Number) row.get("distance")).doubleValue();
            if (distance < SIMILARITY_THRESHOLD) {
                jdbc.update("UPDATE prompt_embeddings SET hit_count = hit_count + 1, last_hit_at = NOW() WHERE id = ?",
                    row.get("id"));
                log.debug("Semantic cache VECTOR hit distance={}", distance);
                return Optional.of(mapper.readTree((String) row.get("spec")));
            }
        } catch (Exception e) {
            log.debug("Semantic cache lookup no match: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Salvesta uus (prompt, embedding, spec) — pärast Claude-kõnet, kui cache-miss.
     */
    public void store(String prompt, float[] embedding, String templateName, JsonNode spec) {
        try {
            jdbc.update(
                "INSERT INTO prompt_embeddings " +
                "(prompt_hash, prompt_text, embedding, template_name, spec_json) " +
                "VALUES (?, ?, ?, ?, ?::jsonb) " +
                "ON CONFLICT (prompt_hash) DO UPDATE SET hit_count = prompt_embeddings.hit_count + 1, last_hit_at = NOW()",
                sha256(prompt), prompt, new PGvector(embedding), templateName, mapper.writeValueAsString(spec)
            );
        } catch (Exception e) {
            log.warn("Semantic cache store failed (non-fatal): {}", e.getMessage());
        }
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes());
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
