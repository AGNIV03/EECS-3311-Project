package libraryagent.agent.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import libraryagent.agent.AgentContext;
import libraryagent.agent.ToolCall;
import libraryagent.agent.llm.ToolSpec;
import libraryagent.domain.LibraryException;

/** Holds the registered tools and runs the one the model names. */
public class ToolManager {

    private static final Logger LOG = Logger.getLogger(ToolManager.class.getName());

    // Insertion order keeps the tool list identical between requests, so it stays cached.
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public void register(Tool tool) {
        if (tools.putIfAbsent(tool.name(), tool) != null) {
            throw new IllegalArgumentException("Tool already registered: " + tool.name());
        }
    }

    /** Runs a tool call. Never throws: every failure becomes an error result the model can read. */
    public ToolResult execute(ToolCall call, AgentContext context) {
        Tool tool = tools.get(call.toolName());
        if (tool == null) {
            return ToolResult.error("There is no tool called \"" + call.toolName() + "\".");
        }
        try {
            return tool.execute(call.args(), context);
        } catch (LibraryException e) {
            return ToolResult.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Tool " + call.toolName() + " failed", e);
            return ToolResult.error("The " + call.toolName() + " tool failed unexpectedly.");
        }
    }

    public List<ToolSpec> descriptions() {
        return tools.values().stream().map(Tool::spec).toList();
    }

    public List<String> names() {
        return List.copyOf(tools.keySet());
    }
}
