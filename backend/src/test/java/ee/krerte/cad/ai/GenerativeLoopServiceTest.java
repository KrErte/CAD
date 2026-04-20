package ee.krerte.cad.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ee.krerte.cad.ClaudeClient;
import ee.krerte.cad.WorkerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Puhas unit-test generative loop'ile — ClaudeClient ja WorkerClient on
 * mock'itud, et saaksime kontrollida loop'i loogikat ilma päris API-de
 * peal raha põletamata.
 *
 * <p>Kontrollime:
 *   1. Loop peatub kui target score on saavutatud ("target_reached")
 *   2. Loop peatub MAX_ITER piiri juures
 *   3. Loop peatub kui score regresseerub (patch halvendas)
 *   4. Patch clamp'itakse template-skeemi min/max piirile
 *   5. Iga samm emiteerib "review" ja "patch" event'i õiges järjekorras
 */
class GenerativeLoopServiceTest {

    private ClaudeClient claude;
    private WorkerClient worker;
    private GenerativeLoopService loop;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() throws Exception {
        claude = mock(ClaudeClient.class);
        worker = mock(WorkerClient.class);
        loop = new GenerativeLoopService(claude, worker);

        // Worker tagastab fixed template catalog'i, et saaksime clamp'ida
        ObjectNode catalog = mapper.createObjectNode();
        ObjectNode shelf = catalog.putObject("shelf_bracket");
        ObjectNode params = shelf.putObject("params");
        ObjectNode wall = params.putObject("wall_thickness");
        wall.put("min", 3);
        wall.put("max", 10);
        wall.put("default", 5);
        when(worker.templates()).thenReturn(catalog);
    }

    @Test
    void stopsWhenTargetReached() throws Exception {
        // 1. kutse tagastab score 9.0 (target 8.5) → kohe stop
        when(claude.reviewDesign(any(), any(), any()))
                .thenReturn(review(9, "Puhas.", new SuggParam[]{}));

        List<ObjectNode> events = new ArrayList<>();
        ObjectNode spec = baseSpec(5);
        loop.iterate("test", spec, 8.5, events::add);

        assertEquals("start", events.get(0).path("type").asText());
        assertEquals("review", events.get(1).path("type").asText());
        assertEquals("stop", events.get(events.size() - 1).path("type").asText());
        assertEquals("target_reached", events.get(events.size() - 1).path("reason").asText());
        // Ei tohi olla ühtegi patch'i
        assertTrue(events.stream().noneMatch(e -> "patch".equals(e.path("type").asText())));
    }

    @Test
    void iteratesWhenBelowTarget() throws Exception {
        // Step 0: score 6, suggests wall→7
        // Step 1: score 9 → target
        when(claude.reviewDesign(any(), any(), any()))
                .thenReturn(review(6, "Vaja tugevamaks.", new SuggParam[]{
                        new SuggParam("wall_thickness", 7, "Suurenda seina")
                }))
                .thenReturn(review(9, "Nüüd OK.", new SuggParam[]{}));

        List<ObjectNode> events = new ArrayList<>();
        loop.iterate("test", baseSpec(5), 8.5, events::add);

        long patches = events.stream().filter(e -> "patch".equals(e.path("type").asText())).count();
        assertEquals(1, patches);
        // Kontrollime, et patch sees on wall_thickness=7
        ObjectNode patch = events.stream()
                .filter(e -> "patch".equals(e.path("type").asText()))
                .findFirst().orElseThrow();
        assertEquals("wall_thickness", patch.path("param").asText());
        assertEquals(7, patch.path("new").asInt());

        ObjectNode stop = events.get(events.size() - 1);
        assertEquals("target_reached", stop.path("reason").asText());
    }

    @Test
    void stopsAtMaxIter() throws Exception {
        // Alati score 5 → loop läheb max_iter'ini
        when(claude.reviewDesign(any(), any(), any()))
                .thenReturn(review(5, "Keskmine.", new SuggParam[]{
                        new SuggParam("wall_thickness", 6, "Natuke paksemaks")
                }));

        List<ObjectNode> events = new ArrayList<>();
        loop.iterate("test", baseSpec(3), 8.5, events::add);

        ObjectNode stop = events.get(events.size() - 1);
        assertEquals("max_iter", stop.path("reason").asText());
        // Kontrollime, et teostati täpselt MAX_ITER review'd
        long reviews = events.stream().filter(e -> "review".equals(e.path("type").asText())).count();
        assertEquals(GenerativeLoopService.MAX_ITER, reviews);
    }

    @Test
    void stopsOnRegression() throws Exception {
        // Step 0: score 7
        // Step 1: score 4 (> 1 langes) → stop no_improvement
        when(claude.reviewDesign(any(), any(), any()))
                .thenReturn(review(7, "Kõrge.", new SuggParam[]{
                        new SuggParam("wall_thickness", 10, "Tee paksemaks")
                }))
                .thenReturn(review(4, "Halvem.", new SuggParam[]{
                        new SuggParam("wall_thickness", 3, "Ei tea")
                }));

        List<ObjectNode> events = new ArrayList<>();
        loop.iterate("test", baseSpec(5), 8.5, events::add);

        ObjectNode stop = events.get(events.size() - 1);
        assertEquals("no_improvement", stop.path("reason").asText());
    }

    @Test
    void clampsToSchemaMinMax() throws Exception {
        // LLM hallutsineeris wall_thickness=50 (max on 10). Peame clamp'ima 10-le.
        AtomicInteger calls = new AtomicInteger();
        when(claude.reviewDesign(any(), any(), any())).thenAnswer(inv -> {
            int c = calls.getAndIncrement();
            if (c == 0) {
                return review(5, "Lisa paksust.", new SuggParam[]{
                        new SuggParam("wall_thickness", 50, "Igaks juhuks")
                });
            }
            return review(9, "OK.", new SuggParam[]{});
        });

        List<ObjectNode> events = new ArrayList<>();
        loop.iterate("test", baseSpec(3), 8.5, events::add);

        ObjectNode patch = events.stream()
                .filter(e -> "patch".equals(e.path("type").asText()))
                .findFirst().orElseThrow();
        // Peab olema clamp'itud max-i (10) juurde, mitte 50
        assertEquals(10.0, patch.path("new").asDouble());
    }

    @Test
    void stopsWhenNoActionableSuggestion() throws Exception {
        // Score 6, aga soovitused ei sisalda param/new_value → stop
        when(claude.reviewDesign(any(), any(), any()))
                .thenReturn(review(6, "OK aga...", new SuggParam[]{
                        new SuggParam(null, null, "Mõtle üle")
                }));

        List<ObjectNode> events = new ArrayList<>();
        loop.iterate("test", baseSpec(5), 8.5, events::add);
        ObjectNode stop = events.get(events.size() - 1);
        assertEquals("no_patch_available", stop.path("reason").asText());
    }

    // --- Helpers -------------------------------------------------------------

    private ObjectNode baseSpec(int wall) {
        ObjectNode spec = mapper.createObjectNode();
        spec.put("template", "shelf_bracket");
        ObjectNode params = spec.putObject("params");
        params.put("wall_thickness", wall);
        return spec;
    }

    private record SuggParam(String param, Integer newValue, String label) {}

    private JsonNode review(int score, String verdict, SuggParam[] suggs) {
        ObjectNode r = mapper.createObjectNode();
        r.put("score", score);
        r.put("verdict_et", verdict);
        r.putArray("strengths");
        r.putArray("weaknesses");
        var arr = r.putArray("suggestions");
        for (SuggParam s : suggs) {
            ObjectNode o = arr.addObject();
            o.put("label_et", s.label());
            o.put("rationale_et", "sest");
            if (s.param() != null) o.put("param", s.param());
            if (s.newValue() != null) o.put("new_value", s.newValue());
        }
        return r;
    }
}
