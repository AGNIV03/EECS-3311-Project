package libraryagent.agent.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Offline checks of the translation between the app's types and the Anthropic SDK's. */
class ClaudeClientTest {

    private final ClaudeClient client = new ClaudeClient(
            AnthropicOkHttpClient.builder().apiKey("test-key-not-used").build(), "medium");

    private static final ToolSpec SEARCH = new ToolSpec("searchCatalog", "Search the catalogue.",
            Map.of("keywords", Map.of("type", "string", "description", "Words to match")), List.of("keywords"));

    /** A recorded reply: a thinking block, some text and a tool call. */
    private static final String REPLY_JSON = """
            {"id":"msg_01","type":"message","role":"assistant","model":"claude-sonnet-5-5",
             "content":[
               {"type":"thinking","thinking":"","signature":"sig-abc"},
               {"type":"text","text":"Let me look that up."},
               {"type":"tool_use","id":"toolu_01","name":"searchCatalog","input":{"keywords":"hobbit","maxPages":300}}
             ],
             "stop_reason":"tool_use","stop_sequence":null,
             "usage":{"input_tokens":1200,"output_tokens":80,"cache_read_input_tokens":1000,"cache_creation_input_tokens":0}}
            """;

    private JsonNode body(LlmRequest request) {
        MessageCreateParams params = client.toParams(request);
        return ObjectMappers.jsonMapper().valueToTree(params._body());
    }

    @Test
    void requestUsesSonnetEffortAndCaching() {
        JsonNode body = body(new LlmRequest("You are a librarian.", List.of(SEARCH),
                List.of(Message.user("Find The Hobbit")), 4000));
        assertEquals("claude-sonnet-5-5", body.path("model").asText());
        assertEquals(4000, body.path("max_tokens").asInt());
        assertEquals("medium", body.path("output_config").path("effort").asText());
        assertEquals("ephemeral", body.path("cache_control").path("type").asText());
        assertEquals("You are a librarian.", body.path("system").get(0).path("text").asText());
        assertEquals("ephemeral", body.path("system").get(0).path("cache_control").path("type").asText());
        assertFalse(body.has("temperature"), "sampling parameters are rejected by the API");
        assertFalse(body.has("thinking"), "Sonnet 5.5 thinks adaptively by default");
    }

    @Test
    void toolsAreStrictWithClosedSchemas() {
        JsonNode tool = body(new LlmRequest("s", List.of(SEARCH), List.of(Message.user("hi")), 100))
                .path("tools").get(0);
        assertEquals("searchCatalog", tool.path("name").asText());
        assertTrue(tool.path("strict").asBoolean());
        assertEquals("object", tool.path("input_schema").path("type").asText());
        assertFalse(tool.path("input_schema").path("additionalProperties").asBoolean(true));
        assertEquals("keywords", tool.path("input_schema").path("required").get(0).asText());
        assertEquals("string", tool.path("input_schema").path("properties").path("keywords").path("type").asText());
    }

    @Test
    void replyIsParsedIntoTextToolCallsStopReasonAndUsage() throws Exception {
        LlmResponse response = ClaudeClient.fromMessage(ObjectMappers.jsonMapper()
                .readValue(REPLY_JSON, com.anthropic.models.messages.Message.class));
        assertEquals(LlmResponse.StopReason.TOOL_USE, response.stopReason());
        assertEquals(List.of(new MessagePart.Text("Let me look that up."),
                new MessagePart.ToolUse("toolu_01", "searchCatalog", Map.of("keywords", "hobbit", "maxPages", 300))),
                response.content());
        assertEquals(new TokenUsage(1200, 80, 1000, 0), response.usage());
    }

    @Test
    void assistantTurnIsReplayedUnchangedIncludingThinking() throws Exception {
        LlmResponse response = ClaudeClient.fromMessage(ObjectMappers.jsonMapper()
                .readValue(REPLY_JSON, com.anthropic.models.messages.Message.class));
        Message assistant = response.toMessage();
        assertTrue(assistant.providerMessage() instanceof MessageParam);

        Message results = Message.toolResults(List.of(new MessagePart.ToolResult("toolu_01", "{\"books\":[]}", false)));
        JsonNode messages = body(new LlmRequest("s", List.of(SEARCH),
                List.of(Message.user("Find The Hobbit"), assistant, results), 100)).path("messages");

        JsonNode replayed = messages.get(1).path("content");
        assertEquals("thinking", replayed.get(0).path("type").asText());
        assertEquals("sig-abc", replayed.get(0).path("signature").asText());
        assertEquals("tool_use", replayed.get(2).path("type").asText());

        JsonNode toolResult = messages.get(2).path("content").get(0);
        assertEquals("user", messages.get(2).path("role").asText());
        assertEquals("tool_result", toolResult.path("type").asText());
        assertEquals("toolu_01", toolResult.path("tool_use_id").asText());
        assertFalse(toolResult.path("is_error").asBoolean());
    }

    @Test
    void costEstimateUsesSonnetPrices() {
        TokenUsage usage = new TokenUsage(1_000_000, 100_000, 2_000_000, 0);
        assertEquals("3.4000", usage.estimatedCostUsd().toPlainString());
        assertEquals(new TokenUsage(2_000_000, 200_000, 4_000_000, 0), usage.plus(usage));
    }

    @Test
    void fakeClientReplaysScriptAndRecordsRequests() {
        FakeLLMClient fake = new FakeLLMClient()
                .reply(FakeLLMClient.text("hello"))
                .fail(new LlmException(LlmException.Kind.UNAVAILABLE, "down", null));
        LlmRequest request = new LlmRequest("s", List.of(), List.of(Message.user("hi")), 100);
        assertEquals("hello", fake.complete(request).toMessage().text());
        LlmException e = org.junit.jupiter.api.Assertions.assertThrows(LlmException.class, () -> fake.complete(request));
        assertEquals(LlmException.Kind.UNAVAILABLE, e.kind());
        assertSame(request, fake.requests().get(0));
        assertTrue(fake.exhausted());
    }
}
