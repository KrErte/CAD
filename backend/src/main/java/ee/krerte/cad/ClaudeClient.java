package ee.krerte.cad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ee.krerte.cad.ai.AgentPersona;
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

    /**
     * Fallback "leiuta midagi" — kui ükski template ei sobi, palume Claude'il
     * kirjutada CadQuery koodi otse. Kood peab järgima worker'i AST-whitelist'i
     * (cadquery + math + random; ei tohi olla os/sys/open/eval/exec jne) ja
     * defineerima funktsiooni {@code build()}, mis tagastab {@code cq.Workplane}.
     *
     * <p>Kasutame forced tool-use'i ({@code submit_freeform}), et saaks garanteeritud
     * struktureeritud JSON-vastuse ilma markdown-i parseimiseta.
     *
     * @return JsonNode kujul {@code { "code": "...python...", "summary_et": "...",
     *         "entrypoint": "build" }}
     */
    public JsonNode inventFreeform(String userPrompt) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY not configured");
        }

        String system = """
            Sa oled eesti 3D-printimise ekspert ja CadQuery-spetsialist. Kui meie
            23 parameetrilist malli EI SOBI kasutaja sooviga, on sinu töö leiutada
            detail otse — kirjutada CadQuery Python-koodi, mis meie turvalises
            sandbox'is jookseb.

            SANDBOX REEGLID (oluline, muidu kood ei käivitu):
              - Lubatud import'id ainult: cadquery, math, random.
              - KEELATUD: os, sys, subprocess, socket, threading, pathlib, shutil,
                ctypes, importlib, eval, exec, compile, __import__, open, input,
                ning kõik dunder-atribuudid (__class__, __globals__, __builtins__ jne).
              - Builtins on piiratud puhaste funktsioonidega (abs, min, max, range,
                len, int, float, str, list, dict jne) + tavalised erandid.
              - Timeout 15s, RAM 512MB.

            KOODI NÕUDED:
              - Defineeri funktsioon build() mis tagastab cq.Workplane objekti.
              - Kasuta `import cadquery as cq` (soovitav) või kutsu `cq` otse.
              - Kõik mõõdud millimeetrites.
              - Eelistagu FDM-prinditavust: minimaalne seinapaksus 2mm, overhang
                <45°, ei tohi olla hõljuvaid saarekesi ilma toeta.
              - Kui detail on SUUREM kui tüüpiline FDM-voodi (220×220×250mm),
                saa sellest aru — võid tagastada koodi, aga mainib summary_et-s,
                et kasutaja peab osadeks jagama või tellima suure-voodi printerilt.
              - Kood peab olema iseseisev — ära eelda mingeid worker-globaale.

            VASTAMISREEGLID:
              - Vasta AINULT tööriistaga submit_freeform.
              - summary_et: üks lühike lause eesti keeles, mis kirjeldab, mida kood
                genereerib (nt "72cm kõrgune ruudukujuline laua jalg seinapaksusega 3mm").
            """;

        // ---- Tool schema ----
        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", "submit_freeform");
        tool.put("description", "Esita CadQuery kood, mis sandbox'is käivitub ja cq.Workplane tagastab.");
        ObjectNode toolSchema = tool.putObject("input_schema");
        toolSchema.put("type", "object");
        ArrayNode required = toolSchema.putArray("required");
        required.add("code"); required.add("summary_et");
        ObjectNode props = toolSchema.putObject("properties");
        props.putObject("code").put("type", "string")
                .put("description", "Terviklik Python-kood, mis defineerib build() -> cq.Workplane.");
        props.putObject("summary_et").put("type", "string")
                .put("description", "Üks lause eesti keeles — mida kood genereerib.");
        props.putObject("entrypoint").put("type", "string")
                .put("description", "Funktsiooni nimi, mida kutsuda. Vaikimisi 'build'.");

        // ---- Body ----
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 2000);
        body.put("system", system);
        ArrayNode tools = body.putArray("tools");
        tools.add(tool);
        ObjectNode choice = body.putObject("tool_choice");
        choice.put("type", "tool");
        choice.put("name", "submit_freeform");

        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", "Kasutaja soov (eesti keeles):\n"
                + (userPrompt == null ? "" : userPrompt)
                + "\n\nKataloogist ei leitud sobivat malli — palun leiuta detail CadQuery koodina.");

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
        for (JsonNode block : resp.get("content")) {
            if ("tool_use".equals(block.path("type").asText())
                    && "submit_freeform".equals(block.path("name").asText())) {
                ObjectNode out = (ObjectNode) block.get("input").deepCopy();
                if (!out.has("entrypoint") || out.path("entrypoint").asText().isBlank()) {
                    out.put("entrypoint", "build");
                }
                return out;
            }
        }
        throw new RuntimeException("Claude did not call submit_freeform: " + resp);
    }

    public JsonNode specFromPrompt(String userPrompt, JsonNode templateCatalog) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY not configured");
        }

        String system = """
            You are a CAD parameter extractor for an Estonian 3D-printing service.
            The user describes a part in Estonian (or English). Your job: pick ONE
            template from the provided catalog and fill its numeric parameters.

            Respond ONLY with a JSON object of shape:
              { "template": "<template_name>", "params": { ... }, "summary_et": "..." }

            Rules:
            - All param values MUST respect the min/max in the catalog. Clamp if needed.
            - If the user omits a value, use the default.
            - If no template fits, respond {"error":"no_match","summary_et":"..."}.
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

        String text = resp.get("content").get(0).get("text").asText().trim();
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
     * Ühe spetsialist-agendi (persona) review. Sama skeem mis
     * {@link #reviewDesign(String, JsonNode, String)}, aga süsteem-prompti
     * annab hääle ühele-konkreetsele-ekspertiisile (struktuur / protsess /
     * maksumus / esteetika) ning arvestab ka rule-based DFM raportiga kui
     * see on olemas.
     *
     * @param persona       millise agendi hääl
     * @param userPromptEt  kasutaja originaal soov
     * @param spec          resolveeritud spec
     * @param dfmReport     optional — worker/dfm.py poolt tagastatud issues,
     *                      antakse LLM-ile ette et ei pea ise avastama
     * @param imageBase64Png three.js preview PNG (nullable)
     */
    public JsonNode reviewAsPersona(AgentPersona persona,
                                    String userPromptEt,
                                    JsonNode spec,
                                    JsonNode dfmReport,
                                    String imageBase64Png) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY not configured");
        }

        String system = persona.systemPromptEt()
                + "\n\nVasta AINULT tööriistaga submit_persona_review. "
                + "Kõik väljad eesti keeles. Kui numbriline muudatus on kohane, "
                + "too `param` ja `new_value` välja välja — frontend rakendab need "
                + "ühe klikiga.";

        // Tool schema — sama struktuur mis single-agent review, aga lisan
        // välja `focus_area_et` kus agent selgitab mis ON TEMA ÜLESANNE
        // (et kasutaja mõistaks, miks see score on siis madalam kui teisel).
        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", "submit_persona_review");
        tool.put("description", "Spetsialist-agendi struktureeritud review.");
        ObjectNode ts = tool.putObject("input_schema");
        ts.put("type", "object");
        ArrayNode req = ts.putArray("required");
        req.add("score"); req.add("verdict_et"); req.add("findings"); req.add("suggestions");
        ObjectNode props = ts.putObject("properties");
        props.putObject("score").put("type", "integer").put("minimum", 1).put("maximum", 10);
        props.putObject("verdict_et").put("type", "string")
                .put("description", "Üks lause eesti keeles, AGENT'I VAATEST.");
        ObjectNode findings = props.putObject("findings");
        findings.put("type", "array");
        findings.put("description", "2–4 avastust eesti keeles (nii positiivseid kui negatiivseid).");
        findings.putObject("items").put("type", "string");
        ObjectNode sug = props.putObject("suggestions");
        sug.put("type", "array");
        sug.put("description", "0–3 konkreetset soovitust. Iga üks kas tekstisiidne või numbriline.");
        ObjectNode sit = sug.putObject("items");
        sit.put("type", "object");
        ArrayNode sitReq = sit.putArray("required");
        sitReq.add("label_et"); sitReq.add("rationale_et");
        ObjectNode sitProps = sit.putObject("properties");
        sitProps.putObject("label_et").put("type", "string");
        sitProps.putObject("rationale_et").put("type", "string");
        sitProps.putObject("param").put("type", "string");
        sitProps.putObject("new_value").put("type", "number");

        // Body
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 1200);
        body.put("system", system);
        ArrayNode tools = body.putArray("tools");
        tools.add(tool);
        ObjectNode choice = body.putObject("tool_choice");
        choice.put("type", "tool");
        choice.put("name", "submit_persona_review");

        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        ArrayNode contents = msg.putArray("content");

        // Tekst
        StringBuilder ctx = new StringBuilder();
        ctx.append("PERSONA: ").append(persona.displayNameEt()).append("\n\n");
        ctx.append("Kasutaja algne soov (EE):\n");
        ctx.append(userPromptEt == null || userPromptEt.isBlank() ? "(pole salvestatud)" : userPromptEt);
        ctx.append("\n\nResolveeritud spec:\n").append(spec.toPrettyString());
        if (dfmReport != null && !dfmReport.isMissingNode() && dfmReport.has("issues")) {
            ctx.append("\n\nEeluuring (reeglipõhine DFM-audit):\n")
               .append(dfmReport.toPrettyString())
               .append("\n\nKasuta eeluuringu avastusi — ära korda neid sõna-sõnalt, vaid ")
               .append("lisa OMA PERSONA vaatenurk. Kui eeluuring märkas midagi, ")
               .append("mis sinu eriala vaatest on OLULINE vs TÜHINE, too see esile.");
        }
        ObjectNode textBlock = contents.addObject();
        textBlock.put("type", "text");
        textBlock.put("text", ctx.toString());

        if (imageBase64Png != null && !imageBase64Png.isBlank()) {
            ObjectNode img = contents.addObject();
            img.put("type", "image");
            ObjectNode src = img.putObject("source");
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
            throw new RuntimeException("Empty response from Claude for persona " + persona.code());
        }
        for (JsonNode block : resp.get("content")) {
            if ("tool_use".equals(block.path("type").asText())
                    && "submit_persona_review".equals(block.path("name").asText())) {
                ObjectNode out = (ObjectNode) block.get("input").deepCopy();
                out.put("persona", persona.code());
                out.put("persona_display", persona.displayNameEt());
                out.put("weight", persona.weight());
                return out;
            }
        }
        throw new RuntimeException("Claude did not call submit_persona_review for " + persona.code());
    }

    /**
     * Synthesizer — saab kõigi nelja agendi vastused ette ja koostab
     * ühe koondverdikti + konsolideeritud soovituste pingerea.
     *
     * <p>Kasutab kaalutud keskmist scoride puhul (persona.weight()) ja
     * eemaldab duplikaat-soovitusi (kui struktuur ja protsess mõlemad
     * soovitasid suurendada seina, võetakse üks, suurima new_value'ga).
     */
    public JsonNode synthesizeCouncil(String userPromptEt,
                                      JsonNode spec,
                                      JsonNode agentResponses) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY not configured");
        }

        String system = """
            Sa oled disaini-nõukogu juht. Sinu ette tuuakse NELJA spetsialist-
            agendi review (struktuur, prindiprotsess, maksumus, esteetika).
            Iga agent on hinnanud sama detaili oma vaatest.

            Sinu töö:
              1. Esita ühe-lause KOONDVERDIKT eesti keeles — kas detail on printimis-
                 valmis, kas on kriitilisi takistusi?
              2. Reasta 1–5 TOP-priority soovitust — need, mis kõige rohkem
                 parandaksid detaili. Kui kaks agenti soovitasid sama asja
                 (nt "suurenda seinapaksust 5mm peale"), too see üks kord,
                 AGA märgi mitu agenti seda kinnitas → "backed_by": [persona-koodid].
              3. Anna OVERALL verdict: "ship_it" (kõik OK, mine printima),
                 "iterate" (paranda paar asja), "redesign" (fundamentaalselt valesti).

            Vasta AINULT tööriistaga submit_synthesis. Kõik tekstid ON EESTI KEELES.
            """;

        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", "submit_synthesis");
        tool.put("description", "Koondatud nõukogu verdikti esitamine.");
        ObjectNode ts = tool.putObject("input_schema");
        ts.put("type", "object");
        ArrayNode req = ts.putArray("required");
        req.add("verdict_et"); req.add("overall"); req.add("top_actions");
        ObjectNode props = ts.putObject("properties");
        props.putObject("verdict_et").put("type", "string");
        props.putObject("overall").put("type", "string")
                .putArray("enum").add("ship_it").add("iterate").add("redesign");
        ObjectNode tops = props.putObject("top_actions");
        tops.put("type", "array");
        ObjectNode tit = tops.putObject("items");
        tit.put("type", "object");
        ArrayNode titReq = tit.putArray("required");
        titReq.add("label_et"); titReq.add("rationale_et"); titReq.add("priority");
        ObjectNode titProps = tit.putObject("properties");
        titProps.putObject("label_et").put("type", "string");
        titProps.putObject("rationale_et").put("type", "string");
        titProps.putObject("priority").put("type", "integer").put("minimum", 1).put("maximum", 5);
        titProps.putObject("param").put("type", "string");
        titProps.putObject("new_value").put("type", "number");
        ObjectNode backed = titProps.putObject("backed_by");
        backed.put("type", "array");
        backed.putObject("items").put("type", "string");

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 1500);
        body.put("system", system);
        ArrayNode tools = body.putArray("tools");
        tools.add(tool);
        ObjectNode choice = body.putObject("tool_choice");
        choice.put("type", "tool");
        choice.put("name", "submit_synthesis");

        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content",
                "Kasutaja soov:\n" +
                (userPromptEt == null ? "(pole)" : userPromptEt) +
                "\n\nSpec:\n" + spec.toPrettyString() +
                "\n\nNelja agendi review:\n" + agentResponses.toPrettyString());

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
            throw new RuntimeException("Empty synthesis response");
        }
        for (JsonNode block : resp.get("content")) {
            if ("tool_use".equals(block.path("type").asText())
                    && "submit_synthesis".equals(block.path("name").asText())) {
                return block.get("input");
            }
        }
        throw new RuntimeException("Synthesizer did not call submit_synthesis");
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
