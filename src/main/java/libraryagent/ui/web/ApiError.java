package libraryagent.ui.web;

/** An HTTP error with a status code and a message for the browser. */
final class ApiError extends RuntimeException {

    final int status;

    ApiError(int status, String message) {
        super(message);
        this.status = status;
    }
}
