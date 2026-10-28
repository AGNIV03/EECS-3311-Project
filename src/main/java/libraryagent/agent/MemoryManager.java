package libraryagent.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import libraryagent.agent.llm.Message;
import libraryagent.domain.Book;

/**
 * Per-session conversation memory: the message history and the books shown in the last answer.
 *
 * <p>History is append-only, because the model's reasoning blocks are only valid if the
 * conversation before them is unchanged. When a conversation grows past {@link #MAX_USER_TURNS},
 * it is restarted with a short plain-text recap of the most recent exchanges instead of
 * trimming old turns.
 */
public class MemoryManager {

    static final int MAX_USER_TURNS = 30;
    static final int RECAP_EXCHANGES = 4;

    private record Exchange(String user, String assistant) {
    }

    private static final class Session {
        final List<Message> history = new ArrayList<>();
        final List<Exchange> exchanges = new ArrayList<>();
        List<Book> lastResults = List.of();
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public List<Message> history(String sessionId) {
        return List.copyOf(session(sessionId).history);
    }

    /** Appends one message to the session's history. */
    public void append(String sessionId, Message message) {
        session(sessionId).history.add(message);
    }

    /** Appends a whole turn and remembers its exchange for recaps. */
    public void appendTurn(String sessionId, List<Message> turn, String userText, String answer) {
        Session s = session(sessionId);
        s.history.addAll(turn);
        s.exchanges.add(new Exchange(userText, answer));
        if (userTurns(s.history) >= MAX_USER_TURNS) {
            restartWithRecap(s);
        }
    }

    public List<Book> lastResults(String sessionId) {
        return session(sessionId).lastResults;
    }

    public void setLastResults(String sessionId, List<Book> books) {
        session(sessionId).lastResults = List.copyOf(books);
    }

    /** Forgets everything for a session (on login or logout). */
    public void clear(String sessionId) {
        sessions.remove(sessionId);
    }

    private Session session(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> new Session());
    }

    private static long userTurns(List<Message> history) {
        // Tool results are user-role messages too; only count messages that contain user text.
        return history.stream().filter(m -> m.role() == Message.Role.USER && !m.text().isEmpty()).count();
    }

    private static void restartWithRecap(Session s) {
        List<Exchange> recent = s.exchanges.subList(Math.max(0, s.exchanges.size() - RECAP_EXCHANGES),
                s.exchanges.size());
        StringBuilder recap = new StringBuilder(
                "[Earlier in this conversation (summary of the most recent exchanges)]");
        for (Exchange e : recent) {
            recap.append("\nUser: ").append(e.user()).append("\nAssistant: ").append(e.assistant());
        }
        List<Exchange> kept = new ArrayList<>(recent);
        s.history.clear();
        s.history.add(Message.user(recap.toString()));
        s.history.add(Message.assistant(List.of(new libraryagent.agent.llm.MessagePart.Text(
                "Understood. I'll continue from there.")), null));
        s.exchanges.clear();
        s.exchanges.addAll(kept);
    }
}
