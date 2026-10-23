package libraryagent.domain;

/**
 * A library rule was violated (for example, a borrowing limit was reached). The message is
 * written for the end user and is shown as-is by the GUI, the CLI and the agent's tools.
 */
public class LibraryException extends RuntimeException {

    public LibraryException(String message) {
        super(message);
    }
}
