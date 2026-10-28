package libraryagent.agent.llm;

import java.util.List;
import java.util.Map;

/**
 * How a tool is described to the model.
 *
 * @param properties JSON Schema for each argument, e.g. {@code "isbn" -> {type: string, description: ...}}
 * @param required   argument names the model must always supply
 */
public record ToolSpec(String name, String description, Map<String, Object> properties, List<String> required) {

    public ToolSpec {
        properties = Map.copyOf(properties);
        required = List.copyOf(required);
    }
}
