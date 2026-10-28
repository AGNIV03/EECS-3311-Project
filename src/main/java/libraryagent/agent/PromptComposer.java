package libraryagent.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import libraryagent.agent.llm.LlmRequest;
import libraryagent.agent.llm.ToolSpec;
import libraryagent.domain.Book;
import libraryagent.domain.Member;

/**
 * Assembles what the model sees. The system prompt never changes, so it stays cached and the
 * model's replayed reasoning blocks stay valid. Session details (the logged-in member, today's
 * date) are sent as system notes inside the conversation instead.
 */
public class PromptComposer {

    private final String systemPrompt;
    private final Clock clock;

    public PromptComposer(Clock clock) {
        this.clock = clock;
        try (InputStream in = PromptComposer.class.getResourceAsStream("/prompts/system.txt")) {
            if (in == null) {
                throw new IllegalStateException("prompts/system.txt is missing from resources");
            }
            this.systemPrompt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the system prompt", e);
        }
    }

    public String buildSystemPrompt() {
        return systemPrompt;
    }

    public LlmRequest buildRequest(AgentContext context, List<ToolSpec> tools) {
        return new LlmRequest(systemPrompt, tools, context.getMessages(), LlmRequest.DEFAULT_MAX_TOKENS);
    }

    /** The session note: who is logged in and today's date. */
    public String sessionNote(Member member) {
        String who = member == null
                ? "The user is not logged in."
                : "The user is " + member.getName() + " (member ID " + member.getMemberId() + ", "
                        + member.getType().name().toLowerCase() + " member"
                        + (member.isLibrarian() ? ", librarian" : "") + ").";
        return "Session: " + who + " Today's date: " + LocalDate.now(clock) + ".";
    }

    /**
     * The text of the user's message for this turn. The books shown in the previous answer are
     * attached so references such as "the second one" can be resolved.
     */
    public String userTurn(String message, List<Book> previousResults) {
        if (previousResults.isEmpty()) {
            return message;
        }
        StringBuilder sb = new StringBuilder(message).append("\n\n[Books shown in my previous answer]");
        for (int i = 0; i < previousResults.size(); i++) {
            Book b = previousResults.get(i);
            sb.append('\n').append(i + 1).append(". ").append(b.getTitle()).append(" by ").append(b.getAuthor())
                    .append(" (ISBN ").append(b.getIsbn()).append(')');
        }
        return sb.toString();
    }
}
