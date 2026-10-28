package libraryagent.agent.llm;

import java.util.Map;

/** One piece of a conversation message. */
public sealed interface MessagePart permits MessagePart.Text, MessagePart.ToolUse, MessagePart.ToolResult {

    /** Plain text from the user or the model. */
    record Text(String text) implements MessagePart {
    }

    /** The model asking to run a tool; {@code id} links it to the matching result. */
    record ToolUse(String id, String name, Map<String, Object> input) implements MessagePart {
    }

    /** What a tool returned (JSON text), sent back to the model in the next user message. */
    record ToolResult(String toolUseId, String content, boolean isError) implements MessagePart {
    }
}
