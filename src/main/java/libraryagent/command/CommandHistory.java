package libraryagent.command;

import java.util.ArrayDeque;
import java.util.Deque;
import libraryagent.domain.LibraryException;

/** Command pattern invoker: runs commands and remembers successful ones for undo. */
public class CommandHistory {

    private final Deque<Command> done = new ArrayDeque<>();

    /** Executes the command; a rule violation becomes a failed result and is not recorded. */
    public CommandResult run(Command command) {
        CommandResult result;
        try {
            result = command.execute();
        } catch (LibraryException e) {
            return CommandResult.failure(e.getMessage());
        }
        if (result.success()) {
            done.push(command);
        }
        return result;
    }

    /** Undoes the most recent successful command and returns a message for the user. */
    public String undoLast() {
        Command last = done.poll();
        if (last == null) {
            return "Nothing to undo.";
        }
        try {
            last.undo();
            return "Undone: " + last.describe() + ".";
        } catch (LibraryException e) {
            return "Could not undo the " + last.describe() + ": " + e.getMessage();
        }
    }

    public boolean canUndo() {
        return !done.isEmpty();
    }

    /** Description of the command that undo would reverse, or null. */
    public String peekDescription() {
        Command last = done.peek();
        return last == null ? null : last.describe();
    }
}
