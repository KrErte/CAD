package ee.krerte.cad.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * RAG-lite template-soovitaja.
 *
 * <p>Kui uus kasutaja kirjutab "vaja seinale konksu 5kg kotti", enne Claude-
 * kutsega template-kataloogi üle lugemist, küsime ajaloost:
 * "kes on varem seda fraasi kasutanud ja millise template'i valisid?"
 *
 * <p>Vastus jõuab Claude-prompti kui <em>hint</em>, mitte käsk. Kui ajalugu
 * näitab, et 8 inimest 10-st selle fraasi all valisid "hook", on Claude'il
 * palju lihtsam anda õige vastus kiiresti ja odavalt.
 *
 * <p>Kui {@code pg_trgm} pole saadaval (vana Postgres), langeme tagasi
 * tsvector-otsingule. Mõlema ebaõnnestumisel — tagastame tühja hint'i,
 * ja süsteem töötab samamoodi nagu enne RAG-i.
 */
@Service
public class TemplateRagService {

    private static final Logger log = LoggerFactory.getLogger(TemplateRagService.class);

    private final PromptHistoryRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public TemplateRagService(PromptHistoryRepository repo) {
        this.repo = repo;
    }

    /**
     * Anna tagasi top-3 template-soovitused antud prompti kohta.
     *
     * <p>Vastuse vorm:
     * <pre>
     *   {
     *     "hints": [
     *       { "template": "hook",   "confidence": 0.82, "matches": 4, "examples_et": ["seinakonks 5kg"] },
     *       { "template": "bracket","confidence": 0.18, "matches": 1, "examples_et": ["..."] }
     *     ],
     *     "total_corpus": 120,
     *     "method": "trigram"  // või "tsvector" või "empty"
     *   }
     * </pre>
     */
    public ObjectNode suggestTemplates(String userPromptEt) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode hints = mapper.createArrayNode();
        result.set("hints", hints);
        result.put("total_corpus", repo.count());

        if (userPromptEt == null || userPromptEt.isBlank()) {
            result.put("method", "empty");
            return result;
        }

        List<PromptHistory> matches = safeSearch(userPromptEt.trim(), 12);
        if (matches.isEmpty()) {
            result.put("method", "empty");
            return result;
        }

        // Grupeeri template järgi ja arvuta confidence kui <matches> / <total matches>
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, List<String>> examples = new HashMap<>();
        for (PromptHistory p : matches) {
            counts.merge(p.getTemplate(), 1, Integer::sum);
            examples.computeIfAbsent(p.getTemplate(), k -> new ArrayList<>())
                    .add(p.getPromptEt());
        }
        int total = matches.size();
        // Sorteeri match count alusel, top-3
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            Map.Entry<String, Integer> e = sorted.get(i);
            ObjectNode hint = hints.addObject();
            hint.put("template", e.getKey());
            hint.put("matches", e.getValue());
            hint.put("confidence", Math.round((double) e.getValue() / total * 100.0) / 100.0);
            ArrayNode ex = hint.putArray("examples_et");
            examples.get(e.getKey()).stream().limit(2).forEach(ex::add);
        }

        return result;
    }

    /**
     * Salvesta uus prompt → template seos RAG korpusesse.
     * Peaks kutsutama {@code DesignController.spec()} sisendist.
     */
    public void record(Long userId, String promptEt, String template, JsonNode params) {
        if (promptEt == null || promptEt.isBlank() || template == null) return;
        try {
            String paramsJson = params == null ? "{}" : mapper.writeValueAsString(params);
            repo.save(new PromptHistory(userId, promptEt.trim(), template, paramsJson));
        } catch (Exception e) {
            log.warn("Prompt history salvestamine ebaõnnestus: {}", e.getMessage());
        }
    }

    /**
     * Märgi olemasolev prompt "edukas" kui kasutaja STL-i päriselt alla laadis.
     * Eduka kirje kaal RAG-otsingus on 2× kõrgem.
     */
    public void markDownloaded(String promptEt, String template) {
        if (promptEt == null || template == null) return;
        try {
            // Leia viimane kirje ja märgi downloaded=true. Mitte-idempotentne,
            // aga kõrgema taseme kuberneti juures on see OK.
            List<PromptHistory> recent = repo.searchSimilar(promptEt.trim(), 1);
            if (!recent.isEmpty() && template.equals(recent.get(0).getTemplate())) {
                PromptHistory p = recent.get(0);
                p.setDownloaded(true);
                repo.save(p);
            }
        } catch (Exception e) {
            log.warn("Download'i märgistamine ebaõnnestus: {}", e.getMessage());
        }
    }

    private List<PromptHistory> safeSearch(String q, int limit) {
        try {
            return repo.searchSimilar(q, limit);
        } catch (Exception e) {
            log.info("pg_trgm similarity pole saadaval, kasutame tsvector: {}", e.getMessage());
            try {
                return repo.searchByTsvector(q, limit);
            } catch (Exception e2) {
                log.warn("Ka tsvector otsing ebaõnnestus: {}", e2.getMessage());
                return List.of();
            }
        }
    }
}
