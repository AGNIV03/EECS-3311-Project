package libraryagent.agent;

import java.util.List;
import libraryagent.agent.llm.LlmResponse;
import libraryagent.agent.llm.MessagePart;

/** Turns the model's raw reply into a typed {@link AgentDecision}. */
public class ResponseParser {

    static final String REFUSAL_TEXT =
            "Sorry, I can't help with that request. I can help you find, reserve, borrow and rate library books.";
    static final String EMPTY_TEXT = "Sorry, I didn't come up with an answer. Could you rephrase that?";
    static final String TRUNCATED_NOTE = " (My answer was cut short; ask me to continue if you need more.)";

    public AgentDecision parse(LlmResponse response) {
        List<ToolCall> calls = response.content().stream()
                .filter(MessagePart.ToolUse.class::isInstance)
                .map(MessagePart.ToolUse.class::cast)
                .map(u -> new ToolCall(u.id(), u.name(), u.input()))
                .toList();
        if (response.stopReason() == LlmResponse.StopReason.TOOL_USE && !calls.isEmpty()) {
            return new AgentDecision.ToolCalls(calls);
        }
        if (response.stopReason() == LlmResponse.StopReason.REFUSAL) {
            return new AgentDecision.FinalAnswer(REFUSAL_TEXT);
        }
        String text = response.toMessage().text();
        if (text.isEmpty()) {
            return new AgentDecision.FinalAnswer(EMPTY_TEXT);
        }
        if (response.stopReason() == LlmResponse.StopReason.MAX_TOKENS) {
            return new AgentDecision.FinalAnswer(text + TRUNCATED_NOTE);
        }
        return new AgentDecision.FinalAnswer(text);
    }
}
