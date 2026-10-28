package libraryagent.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import libraryagent.agent.llm.Message;
import libraryagent.agent.tools.ToolResult;
import libraryagent.command.CommandHistory;
import libraryagent.domain.Book;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Member;

/**
 * The state of one agent turn: who is asking, the conversation so far, and every book that
 * tools have returned during this turn (the only books the agent may show as results).
 */
public class AgentContext {

    private final String sessionId;
    private final Member member;
    private final CommandHistory history;
    private final List<Message> messages = new ArrayList<>();
    private final Map<String, Book> toolBooks = new LinkedHashMap<>();
    private final List<Book> previousResults;
    private final List<String> toolsUsed = new ArrayList<>();

    public AgentContext(String sessionId, Member member, CommandHistory history, List<Message> conversation,
                        List<Book> previousResults) {
        this.sessionId = sessionId;
        this.member = member;
        this.history = history;
        this.messages.addAll(conversation);
        this.previousResults = List.copyOf(previousResults);
    }

    public String getSessionId() {
        return sessionId;
    }

    public Optional<Member> getMember() {
        return Optional.ofNullable(member);
    }

    /** The logged-in member, or a rule violation the model can explain. */
    public Member requireMember() {
        if (member == null) {
            throw new LibraryException("The user is not logged in. Ask them to log in with their member ID first.");
        }
        return member;
    }

    /** The session's undo history; agent actions are recorded here so "undo" works for them too. */
    public CommandHistory getHistory() {
        return history;
    }

    public List<Message> getMessages() {
        return List.copyOf(messages);
    }

    public void addMessage(Message message) {
        messages.add(message);
    }

    /** Records a tool's outcome: which tool ran and which books it returned. */
    public void addToolResult(String toolName, ToolResult result) {
        toolsUsed.add(toolName);
        result.books().forEach(b -> toolBooks.putIfAbsent(b.getIsbn(), b));
    }

    /** Books returned by tools during this turn, in the order they first appeared. */
    public List<Book> getToolBooks() {
        return List.copyOf(toolBooks.values());
    }

    /** Books shown in the previous answer, for references like "the second one". */
    public List<Book> getPreviousResults() {
        return previousResults;
    }

    public List<String> getToolsUsed() {
        return List.copyOf(toolsUsed);
    }
}
