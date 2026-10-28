package libraryagent.agent.tools;

import java.util.Map;
import libraryagent.agent.AgentContext;
import libraryagent.agent.llm.ToolSpec;

/** An action the agent may take. Every tool validates its arguments before calling a service. */
public interface Tool {

    String name();

    /** Description and argument schema shown to the model. */
    ToolSpec spec();

    ToolResult execute(Map<String, Object> args, AgentContext context);
}
