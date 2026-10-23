package libraryagent.command;

/**
 * Command pattern: an undoable library action. The GUI, the CLI and the agent's tools all
 * run commands through {@link CommandHistory}, so one undo works for every entry point.
 */
public interface Command {

    /** Performs the action. May throw {@link libraryagent.domain.LibraryException} if a rule forbids it. */
    CommandResult execute();

    /**
     * Reverses the action. Throws {@link libraryagent.domain.LibraryException} with the reason
     * when the action can no longer be reversed.
     */
    void undo();

    /** Short description used in confirmations, e.g. "reservation of \"Dune\"". */
    String describe();
}
