package libraryagent.agent;

import java.util.Map;

/** The model's request to run one tool. */
public record ToolCall(String id, String toolName, Map<String, Object> args) {

    public ToolCall {
        args = args == null ? Map.of() : args;
    }
}
