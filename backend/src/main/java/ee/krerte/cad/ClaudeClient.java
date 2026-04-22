package ee.krerte.cad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Thin wrapper around Anthropic's Messages API.
 *
 * Prompts Claude to translate an Estonian free-text description into a strict JSON
 * payload { "template": "...", "params": {...} } matching one of the worker templates.
 */
@Component
public class ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.claude.api-key}") private String apiKey;
    @Value("${app.claude.model}")   private String model;
    @Value("${app.claude.base-url}") private String baseUrl;

    public ClaudeClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public JsonNode specFromPrompt(String userPrompt, JsonNode templateCatalog) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY not configured");
        }
        // BUG-FIX: sanitize sisend enne Claude API-sse saatmist — eemalda
        // kontrollsümboolid (v.a. reavahetused) ja piira pikkust.
        userPrompt = sanitize(userPrompt);

        String system = """
            You are a CAD parameter extractor for an Estonian 3D-printing service.
            The user describes a part in Estonian (or English). Your job: pick ONE
            template from the provided catalog and fill its numeric parameters.

            Respond ONLY with a JSON object of shape:
              { "template": "<template_name>", "params": { ... }, "summary_et": "..." }

            Rules:
            - All param values MUST respect the min/max in the catalog. Clamp if needed.
            - If the user omits a value, use the default.
            - ALWAYS pick the closest matching template, even if it's not a perfect fit.
              Adapt the parameters creatively to approximate the user's request.
              Never refuse — there is no "no_match". Every request can be approximated.
            - summary_et: one short Estonian sentence describing what will be printed.
            """;

        String userMsg = "Catalog:\n" + templateCatalog.toPrettyString()
                + "\n\nUser request (Estonian):\n" + userPrompt;

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 1024);
        body.put("system", system);
        var messages = body.putArray("messages");
        var m = messages.addObject();
        m.put("role", "user");
        m.put("content", userMsg);

        JsonNode resp = webClient.post()
                .uri(baseUrl + "/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (resp == null || !resp.has("content")) {
            throw new RuntimeException("Empty response from Claude: " + resp);
        }

        // BUG-FIX: null-check iga nested field'i juures — Claude API võib tagastada
        // ootamatu struktuuri (nt rate-limit, overloaded, muutunud API versioon).
        // Varem: resp.get("content").get(0).get("text") → NPE kui ükskõik milline
        // neist on null.
        JsonNode contentArr = resp.get("content");
        if (contentArr == null || !contentArr.isArray() || contentArr.isEmpty()) {
            throw new RuntimeException("Claude response missing 'content' array: " + resp);
        }
        JsonNode firstBlock = contentArr.get(0);
        if (firstBlock == null || !firstBlock.has("text")) {
            throw new RuntimeException("Claude response first content block missing 'text': " + firstBlock);
        }
        String text = firstBlock.get("text").asText("").trim();
        // strip accidental ```json fences
        if (text.startsWith("```")) {
            text = text.replaceAll("(?s)```(?:json)?\\s*", "").replaceAll("```\\s*$", "");
        }
        log.debug("Claude raw spec: {}", text);
        return mapper.readTree(text);
    }

    /**
     * AI Design Review — the unprecedented bit.
     *
     * Feeds Claude three things:
     *   1. the user's original Estonian prompt (so Claude knows the INTENT),
     *   2. the resolved parametric spec,
     *   3. a base64 PNG screenshot of the three.js preview (so Claude
     *      actually SEES the design, not just numbers).
     *
     * Claude replies via a forced tool call `submit_design_review` whose
     * schema guarantees a structured JSON response — no fragile
     * "parse the assistant's markdown" step.
     *
     * The returned JsonNode is the tool's `input` object:
     *   { score, verdict_et, strengths[], weaknesses[], suggestions[] }
     * where each suggestion MAY carry { param, new_value } so the
     * frontend can offer one-click apply.
     *
     * @param userPromptEt   the original Estonian description
     * @param spec           the spec the user is about to print
     * @param imageBase64Png three.js canvas PNG (no data-URL prefix) — may be null
     */
    public JsonNode reviewDesign(String userPromptEt, JsonNode spec, String imageBase64Png) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY not configured");
        }

        String system = """
            Sa oled eesti 3D-printimise ja parameetrilise CAD-i ekspert, kellel on
            20 aastat töökogemust FDM-printimisega (PLA, PETG) ja tootedisainiga.
            Kasutaja on just genereerinud endale detaili oma eestikeelse kirjelduse
            põhjal. Sa näed:
              1. tema ORIGINAALSET soovi — mida ta tegelikult tahtis,
              2. saadud parameetrilist spetsifikatsiooni,
              3. 3D-eelvaate pilti.

            Anna aus, konstruktiivne ülevaade:
              - kas disain vastab kasutaja soovile?
              - kas see on hästi FDM-prinditav (overhang'id, sillad, ankurdus)?
              - kas mõõtmed / seinapaksus / täite tihedus on mõistlikud?
              - mida konkreetselt peaks muutma?

            NÕUDED:
              - Kõik tekstid ON ALATI eesti keeles.
              - Ole täpne, konkreetne ja sõbralik — mitte üldine.
              - Numbrilised soovitused pane "param" + "new_value" väljadele täpselt.
                Näiteks kui soovitad paksendada seina 3mm pealt 5mm peale, siis
                { "param": "wall_thickness", "new_value": 5 }.
              - Kui disain on juba korralik, ütle seda ausalt ja anna 1–2 nüanssi,
                mitte 10 jõupingutatud soovitust.
              - Vasta AINULT tööriistaga submit_design_review.
            """;

        // ---- Tool schema: forces structured JSON, no regex parsing. ----
        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", "submit_design_review");
        tool.put("description", "Esita struktureeritud ülevaade 3D-prinditavast detailist.");
        ObjectNode toolSchema = tool.putObject("input_schema");
        toolSchema.put("type", "object");
        ArrayNode required = toolSchema.putArray("required");
        required.add("score"); required.add("verdict_et");
        required.add("strengths"); required.add("weaknesses"); required.add("suggestions");
        ObjectNode props = toolSchema.putObject("properties");
        props.putObject("score")
                .put("type", "integer").put("minimum", 1).put("maximum", 10)
                .put("description", "Üldine hinne 1–10. 10 = valmis printimiseks, 1 = loobu.");
        props.putObject("verdict_et")
                .put("type", "string")
                .put("description", "Üks lause eesti keeles — kokkuvõttev verdikt.");
        ObjectNode strengths = props.putObject("strengths");
        strengths.put("type", "array").put("description", "2–4 tugevust eesti keeles.");
        strengths.putObject("items").put("type", "string");
        ObjectNode weaknesses = props.putObject("weaknesses");
        weaknesses.put("type", "array").put("description", "0–4 muret eesti keeles. Tühi kui disain on puhas.");
        weaknesses.putObject("items").put("type", "string");
        ObjectNode suggestions = props.putObject("suggestions");
        suggestions.put("type", "array").put("description", "0–4 konkreetset soovitust. Eelista numbrilisi param-muudatusi.");
        ObjectNode sugItem = suggestions.putObject("items");
        sugItem.put("type", "object");
        ArrayNode sugReq = sugItem.putArray("required");
        sugReq.add("label_et"); sugReq.add("rationale_et");
        ObjectNode sugProps = sugItem.putObject("properties");
        sugProps.putObject("label_et").put("type", "string")
                .put("description", "Lühike tegevuskuju eesti keeles — nt 'Suurenda seinapaksust 5mm peale'.");
        sugProps.putObject("rationale_et").put("type", "string")
                .put("description", "Üks lause miks — nt 'praegune 3mm võib 5kg all väänduma hakata'.");
        sugProps.putObject("param").put("type", "string")
                .put("description", "Spec.params võti, mida muuta. Jäta välja kui soovitus pole numbriline.");
        sugProps.putObject("new_value").put("type", "number")
                .put("description", "Uus väärtus param-ile. Peab jääma template skeemi min/max piiresse.");

        // ---- Request body ----
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 1500);
        body.put("system", system);

        ArrayNode tools = body.putArray("tools");
        tools.add(tool);
        // Force Claude to use the tool (not return free text).
        ObjectNode choice = body.putObject("tool_choice");
        choice.put("type", "tool");
        choice.put("name", "submit_design_review");

        // Build multimodal user message.
        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        ArrayNode contents = msg.putArray("content");

        ObjectNode textBlock = contents.addObject();
        textBlock.put("type", "text");
        textBlock.put("text",
                "Kasutaja algne soov (eesti keeles):\n" +
                (userPromptEt == null || userPromptEt.isBlank()
                        ? "(ei ole salvestatud — vaata ainult spec'i ja pilti)"
                        : userPromptEt) +
                "\n\nResolveeritud spec:\n" + spec.toPrettyString());

        if (imageBase64Png != null && !imageBase64Png.isBlank()) {
            ObjectNode imageBlock = contents.addObject();
            imageBlock.put("type", "image");
            ObjectNode src = imageBlock.putObject("source");
            src.put("type", "base64");
            src.put("media_type", "image/png");
            src.put("data", imageBase64Png);
        }

        JsonNode resp = webClient.post()
                .uri(baseUrl + "/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (resp == null || !resp.has("content")) {
            throw new RuntimeException("Empty response from Claude: " + resp);
        }

        // Find the tool_use block; text blocks before it are Claude's thinking.
        for (JsonNode block : resp.get("content")) {
            if ("tool_use".equals(block.path("type").asText())
                    && "submit_design_review".equals(block.path("name").asText())) {
                return block.get("input");
            }
        }
        throw new RuntimeException("Claude did not call submit_design_review: " + resp);
    }

    /**
     * Darwin CAD — batch-ranking mitmele variandile korraga.
     *
     * <p>Saadab Claude'ile:
     * <ul>
     *   <li>kasutaja originaalse eestikeelse soovi (intent),</li>
     *   <li>iga variandi spec-i (template + params + metrics),</li>
     *   <li>iga variandi SVG-eelvaate konverteerituna base64-PNG-le
     *       (tegelikult siin me pareda lihtsuse kasuks saadame SVG-d tekstina
     *       — Claude Vision suudab SVG-d lugeda tekstina, ning see on
     *       odavam kui raster-render).</li>
     * </ul>
     *
     * <p>Claude peab vastama forceitud tool-use'iga {@code rank_variants},
     * mis tagastab array {@code [{variant_id, score, reasoning_et, rank}]}.
     * See on BATCH ranking — üks LLM-kutse reastab kõik 6 varianti
     * ühekorraga, mis on ~6× odavam kui kuus eraldi kutset.
     *
     * <p>Miks see on tähtis: kasutaja ei näe pelgalt 6 pilti, vaid näeb
     * neid REASTATUNA + iga variandi juures 1-lause põhjendus eesti keeles.
     * See on "AI on sinu disainijuhendaja" kogemus, mitte "AI on geomeetria-
     * automaat".
     *
     * @param userPromptEt algne eestikeelne soov
     * @param template     mille templaadi piires evolutsioon toimub
     * @param population   workeri vastus /evolve/seed või /evolve/cross
     * @return tagastatud array Variant objekte koos score/reasoning/rank
     */
    public JsonNode rankVariants(String userPromptEt, String template, JsonNode population) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY not configured");
        }
        JsonNode variants = population.path("variants");
        if (!variants.isArray() || variants.size() == 0) {
            throw new RuntimeException("No variants to rank");
        }

        String system = """
            Sa oled eesti 3D-printimise ja parameetrilise CAD-i ekspert.
            Kasutaja kirjeldas detaili eesti keeles. Nüüd on automaatselt
            genereeritud MITU VARIANTI (populatsioon). Sinu ülesanne on
            reastada need kasutaja soovi vastu ja iga variandi juures
            seletada ühes lauses, miks see oma koha saab.

            NÕUDED:
              - Kõik tekstid ON ALATI eesti keeles.
              - `rank` = 0 tähendab parim variant, `rank` = N-1 halvim.
              - `score` = 1–10 — sama skaalal iga variant.
              - `reasoning_et` = üks lause, konkreetne, mitte üldine.
                Näiteks: "Seinapaksus 6mm suudab 8kg koormust hoida, aga
                prindiaeg on teistest 25% pikem." MITTE: "See on hea."
              - Arvesta: (a) kas vastab kasutaja soovile, (b) kas on FDM-
                prinditav (overhang'id, sillad), (c) kas massi/aeg/hind on
                mõistlikud.
              - Vasta AINULT tööriistaga submit_ranking.
            """;

        // Tool schema — sunnib struktureeritud vastuse.
        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", "submit_ranking");
        tool.put("description", "Esita variantide järjekord ja põhjendused.");
        ObjectNode toolSchema = tool.putObject("input_schema");
        toolSchema.put("type", "object");
        ArrayNode required = toolSchema.putArray("required");
        required.add("ranked");
        ObjectNode props = toolSchema.putObject("properties");
        ObjectNode ranked = props.putObject("ranked");
        ranked.put("type", "array");
        ranked.put("description", "Iga variandi kohta üks kirje. Sorteeri rank järgi 0..N-1.");
        ObjectNode items = ranked.putObject("items");
        items.put("type", "object");
        ArrayNode itReq = items.putArray("required");
        itReq.add("variant_id"); itReq.add("score"); itReq.add("reasoning_et"); itReq.add("rank");
        ObjectNode itProps = items.putObject("properties");
        itProps.putObject("variant_id").put("type", "string")
                .put("description", "Variandi ID, mida reastatakse (kopeeri täpselt nagu sisendis).");
        itProps.putObject("score").put("type", "integer").put("minimum", 1).put("maximum", 10);
        itProps.putObject("rank").put("type", "integer").put("minimum", 0)
                .put("description", "0 = parim, suureneb halvima poole.");
        itProps.putObject("reasoning_et").put("type", "string")
                .put("description", "Üks lause eesti keeles.");

        // Koosta user-message: tekst + iga variandi kohta pilt (SVG konverteeri
        // base64-na image-blokiks? Claude toetab image.source.type=base64 aga
        // ainult PNG/JPG/GIF/WEBP — SVG-d tuleb konverteerida. Efektiivsuse
        // huvides saadame SVG-d TEKSTINA: Claude tajub kontuurid koodist piisavalt
        // vaatlus-tasemel, kuna need on lihtsad polüüpid).
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 2000);
        body.put("system", system);

        ArrayNode tools = body.putArray("tools");
        tools.add(tool);
        ObjectNode choice = body.putObject("tool_choice");
        choice.put("type", "tool");
        choice.put("name", "submit_ranking");

        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        ArrayNode contents = msg.putArray("content");

        // Intro-tekst
        ObjectNode intro = contents.addObject();
        intro.put("type", "text");
        intro.put("text",
                "Kasutaja algne soov (eesti keeles):\n" +
                (userPromptEt == null || userPromptEt.isBlank() ? "(pole salvestatud)" : userPromptEt) +
                "\n\nTemplate: " + template +
                "\n\nAllpool on " + variants.size() + " varianti. Iga juures on " +
                "(1) variandi ID, (2) parameetrid, (3) arvutatud mõõdud " +
                "(maht, prindiaeg, kaal, overhang-risk), (4) SVG-eelvaade koodina. " +
                "Reasta nad kasutaja soovi vastu.");

        // Iga variandi kohta üks tekstblokk — kompaktselt, et ei ületada
        // context-aknat. SVG-d me piirame ~800 tähemärgile (path-commands).
        int idx = 0;
        for (JsonNode v : variants) {
            ObjectNode block = contents.addObject();
            block.put("type", "text");
            String svg = v.path("svg_thumb").asText("");
            if (svg.length() > 800) svg = svg.substring(0, 800) + "...[kärbe]";
            StringBuilder sb = new StringBuilder();
            sb.append("\n--- Variant #").append(idx++).append(" ---\n");
            sb.append("variant_id: ").append(v.path("variant_id").asText()).append("\n");
            sb.append("params: ").append(v.path("params").toString()).append("\n");
            sb.append("metrics: ").append(v.path("metrics").toString()).append("\n");
            sb.append("svg_preview: ").append(svg).append("\n");
            block.put("text", sb.toString());
        }

        JsonNode resp = webClient.post()
                .uri(baseUrl + "/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        if (resp == null || !resp.has("content")) {
            throw new RuntimeException("Empty response from Claude: " + resp);
        }

        // Ekstraktime tool_use → input → ranked[] ja liitume originaalsete
        // variantidega (et tagastada täisvariant koos SVG-ga frontendi jaoks).
        for (JsonNode block : resp.get("content")) {
            if ("tool_use".equals(block.path("type").asText())
                    && "submit_ranking".equals(block.path("name").asText())) {
                JsonNode rankedArr = block.path("input").path("ranked");
                return mergeRanking(variants, rankedArr);
            }
        }
        throw new RuntimeException("Claude did not call submit_ranking: " + resp);
    }

    /**
     * BUG-FIX: sanitize kasutaja sisendit enne API-sse saatmist.
     * Eemaldab kontrollsümbolid (v.a. newline/tab) ja piirab pikkust 500 märgile.
     */
    private static String sanitize(String input) {
        if (input == null) return "";
        // Eemalda kontrollsümboolid v.a. \n ja \t
        String cleaned = input.replaceAll("[\\p{Cc}&&[^\n\t]]", "");
        // Piira pikkust (defense-in-depth, lisaks kontrolleri @Size validatsioonile)
        if (cleaned.length() > 500) {
            cleaned = cleaned.substring(0, 500);
        }
        return cleaned.trim();
    }

    /**
     * Liida ranking-vastus tagasi originaalvariantidega. Iga variant saab
     * score, reasoning_et, rank — ning lõpptulemus sorteeritakse rank-i järgi.
     */
    private JsonNode mergeRanking(JsonNode variants, JsonNode rankedArr) {
        // Index originaalid variant_id järgi
        java.util.Map<String, ObjectNode> byId = new java.util.HashMap<>();
        for (JsonNode v : variants) {
            byId.put(v.path("variant_id").asText(), (ObjectNode) v.deepCopy());
        }
        // Lisa iga ranking-rea väljad vastava variandi juurde
        java.util.List<ObjectNode> sorted = new java.util.ArrayList<>();
        for (JsonNode r : rankedArr) {
            String id = r.path("variant_id").asText();
            ObjectNode variant = byId.get(id);
            if (variant == null) continue; // Claude hallutsineeris ID — ignoreeri
            variant.put("score", r.path("score").asInt(5));
            variant.put("reasoning_et", r.path("reasoning_et").asText(""));
            variant.put("rank", r.path("rank").asInt(0));
            sorted.add(variant);
        }
        // Lisa variandid, mida Claude ei katnud (ei peaks juhtuma aga fail-safe)
        java.util.Set<String> covered = new java.util.HashSet<>();
        for (JsonNode r : rankedArr) covered.add(r.path("variant_id").asText());
        int nextRank = sorted.size();
        for (var entry : byId.entrySet()) {
            if (!covered.contains(entry.getKey())) {
                ObjectNode v = entry.getValue();
                v.put("score", 5);
                v.put("reasoning_et", "AI ei katnud seda varianti.");
                v.put("rank", nextRank++);
                sorted.add(v);
            }
        }
        // Sorteeri rank-i järgi
        sorted.sort((a, b) -> Integer.compare(a.path("rank").asInt(), b.path("rank").asInt()));
        ArrayNode out = mapper.createArrayNode();
        for (ObjectNode v : sorted) out.add(v);
        return out;
    }
}
