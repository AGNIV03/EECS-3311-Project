package libraryagent.agent.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import libraryagent.config.AppConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Calls the real Anthropic API (costs a fraction of a cent). Excluded from {@code gradlew test};
 * run with {@code gradlew liveTest}. Checks a full tool round trip with Claude Sonnet 5.5,
 * including replaying the model's turn unchanged.
 */
@Tag("live")
class ClaudeClientLiveTest {

    @Test
    void toolRoundTripWithSonnet() {
        AppConfig config = AppConfig.load();
        assumeTrue(config.llmEnabled(), "ANTHROPIC_API_KEY is not configured");
        ClaudeClient client = new ClaudeClient(config.apiKey().orElseThrow(), config.effort());

        ToolSpec countCopies = new ToolSpec("countCopies",
                "Returns how many copies of a book the library owns. Always use this tool to answer copy-count questions.",
                Map.of("title", Map.of("type", "string", "description", "Exact book title")), List.of("title"));
        String system = "You are a library assistant. Use tools for facts. Answer in one short sentence.";
        List<Message> conversation = new ArrayList<>(List.of(Message.user("How many copies of Dune do we own?")));

        LlmResponse first = client.complete(new LlmRequest(system, List.of(countCopies), conversation, 2000));
        assertEquals(LlmResponse.StopReason.TOOL_USE, first.stopReason());
        MessagePart.ToolUse call = first.toMessage().toolUses().get(0);
        assertEquals("countCopies", call.name());
        assertTrue(String.valueOf(call.input().get("title")).toLowerCase().contains("dune"));

        conversation.add(first.toMessage());
        conversation.add(Message.toolResults(List.of(new MessagePart.ToolResult(call.id(), "{\"copies\": 7}", false))));
        LlmResponse second = client.complete(new LlmRequest(system, List.of(countCopies), conversation, 2000));
        assertEquals(LlmResponse.StopReason.END_TURN, second.stopReason());
        assertTrue(second.toMessage().text().contains("7"), second.toMessage().text());

        TokenUsage total = client.totalUsage();
        System.out.println("Live test usage: " + total + " ~ $" + total.estimatedCostUsd());
    }
}
