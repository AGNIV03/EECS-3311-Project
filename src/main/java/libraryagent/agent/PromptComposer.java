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
 * Assembles what the model sees: the system prompt (with the current member and date), the
 * tool descriptions and the conversation. The system prompt is the same for every request in a
 * session, so it stays cached.
 */
public class PromptComposer {

    private final String template;
    private final Clock clock;

    public PromptComposer(Clock clock) {
        this.clock = clock;
        try (InputStream in = PromptComposer.class.getResourceAsStream("/prompts/system.txt")) {
            if (in == null) {
                throw new IllegalStateException("prompts/system.txt is missing from resources");
            }
            this.template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the system prompt", e);
        }
    }

    public String buildSystemPrompt(Member member) {
        String who = member == null
                ? "not logged in"
                : member.getName() + " (member ID " + member.getMemberId() + ", "
                        + member.getType().name().toLowerCase() + " member"
                        + (member.isLibrarian() ? ", librarian" : "") + ")";
        return template.replace("{{MEMBER}}", who).replace("{{TODAY}}", LocalDate.now(clock).toString());
    }

    public LlmRequest buildRequest(AgentContext context, List<ToolSpec> tools) {
        return new LlmRequest(buildSystemPrompt(context.getMember().orElse(null)), tools, context.getMessages(),
                LlmRequest.DEFAULT_MAX_TOKENS);
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
