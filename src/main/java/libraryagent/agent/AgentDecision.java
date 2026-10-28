package libraryagent.agent;

import java.util.List;

/** What the model decided this step: run tools, or answer. */
public sealed interface AgentDecision permits AgentDecision.ToolCalls, AgentDecision.FinalAnswer {

    /** One or more tools to run before asking the model again. */
    record ToolCalls(List<ToolCall> calls) implements AgentDecision {
        public ToolCalls {
            calls = List.copyOf(calls);
        }
    }

    /** The answer to show the user. */
    record FinalAnswer(String text) implements AgentDecision {
    }
}
