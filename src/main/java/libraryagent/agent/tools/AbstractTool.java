package libraryagent.agent.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import libraryagent.agent.AgentContext;

/**
 * Template Method: {@link #execute} is fixed for every tool (validate the model's arguments,
 * stop with a clear error if any are wrong, otherwise do the work). Concrete tools supply only
 * the two steps {@link #validate} and {@link #doExecute}.
 */
public abstract class AbstractTool implements Tool {

    @Override
    public final ToolResult execute(Map<String, Object> args, AgentContext context) {
        Map<String, Object> safeArgs = args == null ? Map.of() : args;
        List<String> errors = validate(safeArgs);
        if (!errors.isEmpty()) {
            return ToolResult.error("Invalid arguments for " + name() + ": " + String.join("; ", errors));
        }
        return doExecute(safeArgs, context);
    }

    /** Checks the arguments and returns one message per problem (empty when all are valid). */
    protected abstract List<String> validate(Map<String, Object> args);

    /** Performs the tool's action with arguments that passed {@link #validate}. */
    protected abstract ToolResult doExecute(Map<String, Object> args, AgentContext context);

    // ---- Schema helpers (strict mode: no numeric or length constraints in the schema) -------

    protected static Map<String, Object> stringProperty(String description) {
        return Map.of("type", "string", "description", description);
    }

    protected static Map<String, Object> integerProperty(String description) {
        return Map.of("type", "integer", "description", description);
    }

    protected static Map<String, Object> stringArrayProperty(String description) {
        return Map.of("type", "array", "items", Map.of("type", "string"), "description", description);
    }

    protected static Map<String, Object> enumProperty(String description, String... values) {
        return Map.of("type", "string", "enum", List.of(values), "description", description);
    }

    protected static Map<String, Object> properties(Object... nameSchemaPairs) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (int i = 0; i < nameSchemaPairs.length; i += 2) {
            props.put((String) nameSchemaPairs[i], nameSchemaPairs[i + 1]);
        }
        return props;
    }

    // ---- Argument helpers ---------------------------------------------------------------

    /** A trimmed string argument, or null when absent or blank. */
    protected static String string(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().strip();
        return text.isEmpty() ? null : text;
    }

    /** An integer argument, or null when absent; throws if present but not a whole number. */
    protected static Integer integer(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString().strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a whole number");
        }
    }

    /** A list of non-blank strings, or an empty list when absent. */
    protected static List<String> strings(Map<String, Object> args, String key) {
        Object value = args.get(key);
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    result.add(item.toString().strip());
                }
            }
        } else if (value != null && !value.toString().isBlank()) {
            result.add(value.toString().strip());
        }
        return result;
    }

    /** Adds an error when a required string argument is missing. */
    protected static void requireString(Map<String, Object> args, String key, List<String> errors) {
        if (string(args, key) == null) {
            errors.add(key + " is required");
        }
    }

    /** Adds an error when an integer argument is present but invalid or below {@code min}. */
    protected static void checkInteger(Map<String, Object> args, String key, int min, List<String> errors) {
        try {
            Integer value = integer(args, key);
            if (value != null && value < min) {
                errors.add(key + " must be at least " + min);
            }
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
        }
    }
}
