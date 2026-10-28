package libraryagent.agent.tools;

import java.util.List;
import java.util.Map;
import libraryagent.agent.AgentContext;
import libraryagent.agent.llm.ToolSpec;

/** "Undo that": reverses the session's most recent action, whether done by chat, menu or command. */
public class UndoLastActionTool extends AbstractTool {

    @Override
    public String name() {
        return "undoLastAction";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "Undo the most recent action in this session (a reservation, return, renewal, checkout or "
                        + "reading-list change), whether it was done through chat or the menus. Only use it when "
                        + "the user asks to undo or take back their last action.",
                properties(),
                List.of());
    }

    @Override
    protected List<String> validate(Map<String, Object> args) {
        return List.of();
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> args, AgentContext context) {
        String message = context.getHistory().undoLast();
        boolean undone = message.startsWith("Undone");
        return undone ? ToolResult.ok(Map.of("message", message)) : ToolResult.error(message);
    }
}
