package libraryagent.command;

/** Outcome of running a command, with a message suitable for showing to the user. */
public record CommandResult(boolean success, String message) {

    public static CommandResult ok(String message) {
        return new CommandResult(true, message);
    }

    public static CommandResult failure(String message) {
        return new CommandResult(false, message);
    }
}
