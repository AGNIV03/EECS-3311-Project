package libraryagent.agent.llm;

import java.util.List;

/** Everything sent to the model for one step: instructions, available tools and the conversation. */
public record LlmRequest(String system, List<ToolSpec> tools, List<Message> messages, int maxTokens) {

    public static final int DEFAULT_MAX_TOKENS = 16_000;

    public LlmRequest {
        tools = List.copyOf(tools);
        messages = List.copyOf(messages);
    }
}
