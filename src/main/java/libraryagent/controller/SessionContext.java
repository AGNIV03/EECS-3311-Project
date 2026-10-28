package libraryagent.controller;

import java.util.Optional;
import java.util.UUID;
import libraryagent.command.CommandHistory;
import libraryagent.domain.Member;

/**
 * One user session in the GUI or CLI: who is logged in and their own undo history. Logging in
 * or out starts a fresh history so nobody can undo another user's actions.
 */
public class SessionContext {

    private final String sessionId = UUID.randomUUID().toString();
    private Member currentMember;
    private CommandHistory history = new CommandHistory();

    public String getSessionId() {
        return sessionId;
    }

    public Optional<Member> getCurrentMember() {
        return Optional.ofNullable(currentMember);
    }

    public CommandHistory getHistory() {
        return history;
    }

    public void login(Member member) {
        currentMember = member;
        history = new CommandHistory();
    }

    public void logout() {
        currentMember = null;
        history = new CommandHistory();
    }
}
