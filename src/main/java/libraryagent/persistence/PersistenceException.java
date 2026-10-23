package libraryagent.persistence;

/** A database operation failed. Indicates a bug or a broken database file, not a user error. */
public class PersistenceException extends RuntimeException {

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
