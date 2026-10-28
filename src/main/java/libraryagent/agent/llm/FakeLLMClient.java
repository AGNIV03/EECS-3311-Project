package libraryagent.agent.llm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Replays scripted replies in order and records every request it receives, so the agent loop
 * and tools can be tested deterministically without network access or cost.
 */
public class FakeLLMClient implements LLMClient {

    private final Deque<Object> script = new ArrayDeque<>();
    private final List<LlmRequest> requests = new ArrayList<>();

    /** Queues a reply. */
    public FakeLLMClient reply(LlmResponse response) {
        script.addLast(response);
        return this;
    }

    /** Queues a failure, as if the API were down. */
    public FakeLLMClient fail(LlmException error) {
        script.addLast(error);
        return this;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        requests.add(request);
        Object next = script.pollFirst();
        if (next == null) {
            throw new IllegalStateException("FakeLLMClient has no scripted reply left for request #" + requests.size());
        }
        if (next instanceof LlmException error) {
            throw error;
        }
        return (LlmResponse) next;
    }

    public List<LlmRequest> requests() {
        return List.copyOf(requests);
    }

    public boolean exhausted() {
        return script.isEmpty();
    }

    /** A final text answer. */
    public static LlmResponse text(String text) {
        return new LlmResponse(List.of(new MessagePart.Text(text)), LlmResponse.StopReason.END_TURN,
                TokenUsage.ZERO, null);
    }

    /** A reply asking for one or more tool calls. */
    public static LlmResponse toolCalls(MessagePart.ToolUse... calls) {
        return new LlmResponse(List.of(calls), LlmResponse.StopReason.TOOL_USE, TokenUsage.ZERO, null);
    }

    public static MessagePart.ToolUse call(String id, String name, Map<String, Object> input) {
        return new MessagePart.ToolUse(id, name, input);
    }

    /** A safety refusal. */
    public static LlmResponse refusal() {
        return new LlmResponse(List.of(), LlmResponse.StopReason.REFUSAL, TokenUsage.ZERO, null);
    }
}
