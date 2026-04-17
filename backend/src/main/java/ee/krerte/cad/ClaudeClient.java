package ee.krerte.cad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
}
