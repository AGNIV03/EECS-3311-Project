package libraryagent.agent.llm;

/** A call to the language model failed. The message is suitable for showing to the user. */
public class LlmException extends RuntimeException {

    public enum Kind {
        /** Network failure, server error or overload; trying again later may work. */
        UNAVAILABLE,
        RATE_LIMITED,
        /** The API key is missing, invalid or lacks permission. */
        AUTHENTICATION,
        /** The request was rejected as malformed; indicates a bug. */
        INVALID_REQUEST
    }

    private final Kind kind;

    public LlmException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
