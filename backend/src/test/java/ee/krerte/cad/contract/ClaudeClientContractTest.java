package ee.krerte.cad.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import ee.krerte.cad.ClaudeClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Contract test ClaudeClient'ile — tagame, et me saadame Anthropic Messages
 * API'le õige kujuga päringu (model, system, messages, max_tokens) ja suudame
 * parsida nii puhast JSON'it kui ka ```json fenced vastust.
 *
 * <p>Kasutame WireMock'i, et:
 * <ul>
 *   <li>Ei kuluta päris API-krediiti (~0.01€ per kõne × sadu teste = päris raha)</li>
 *   <li>Determinismi: sama päring = sama vastus iga kord</li>
 *   <li>Kontraktid: kui Anthropic muudaks response schema't, siis meie kood
 *       katkeks siit, mitte prod'is</li>
 * </ul>
 */
@SpringBootTest
@DisplayName("ClaudeClient ↔ Anthropic API contract")
class ClaudeClientContractTest {

    @RegisterExtension
    static WireMockExtension CLAUDE = WireMockExtension.newInstance()
        .options(com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort())
        .build();

    @DynamicPropertySource
    static void claudeProps(DynamicPropertyRegistry r) {
        r.add("app.claude.base-url", () -> CLAUDE.baseUrl());
        r.add("app.claude.api-key",  () -> "test-key-xxx");
        r.add("app.claude.model",    () -> "claude-sonnet-4-5");
    }

    @Autowired ClaudeClient client;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("specFromPrompt saadab API võtme header'is ja parseb clean JSON'i")
    void happyPath_cleanJson() throws Exception {
        CLAUDE.stubFor(post(urlEqualTo("/v1/messages"))
            .withHeader("x-api-key", equalTo("test-key-xxx"))
            .withHeader("anthropic-version", equalTo("2023-06-01"))
            .withRequestBody(matchingJsonPath("$.model", equalTo("claude-sonnet-4-5")))
            .withRequestBody(matchingJsonPath("$.max_tokens"))
            .withRequestBody(matchingJsonPath("$.system"))
            .willReturn(okJson("""
                {
                  "id":"msg_1",
                  "type":"message",
                  "content":[{"type":"text","text":"{\\"template\\":\\"cube\\",\\"params\\":{\\"size\\":50}}"}]
                }
                """)));

        JsonNode catalog = json.readTree("{\"cube\":{\"size\":{\"min\":10,\"max\":100,\"default\":20}}}");
        JsonNode spec = client.specFromPrompt("50mm kuup", catalog);

        assertThat(spec.get("template").asText()).isEqualTo("cube");
        assertThat(spec.get("params").get("size").asInt()).isEqualTo(50);
        CLAUDE.verify(1, postRequestedFor(urlEqualTo("/v1/messages")));
    }

    @Test
    @DisplayName("Parseb ka ```json fenced vastuse (Claude teeb seda vahel)")
    void handlesMarkdownFence() throws Exception {
        CLAUDE.stubFor(post(urlEqualTo("/v1/messages"))
            .willReturn(okJson("""
                {"content":[{"type":"text","text":"```json\\n{\\"template\\":\\"cube\\",\\"params\\":{\\"size\\":30}}\\n```"}]}
                """)));

        JsonNode spec = client.specFromPrompt("30mm", json.readTree("{}"));
        assertThat(spec.get("params").get("size").asInt()).isEqualTo(30);
    }

    @Test
    @DisplayName("API 500 error → RuntimeException (ei vaiki alla)")
    void upstreamFailureBubbles() {
        CLAUDE.stubFor(post(urlEqualTo("/v1/messages"))
            .willReturn(aResponse().withStatus(500).withBody("upstream down")));

        assertThatThrownBy(() -> client.specFromPrompt("x", json.readTree("{}")))
            .isInstanceOf(Exception.class);
    }
}
