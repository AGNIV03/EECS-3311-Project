package libraryagent.ui.cli;

import java.util.List;

/**
 * A parsed CLI line: a lower-case command word and its arguments. Unrecognised input has kind
 * {@code "chat"} with the original text as the single argument.
 */
public record CliAction(String kind, List<String> args) {

    public String arg(int index) {
        return index < args.size() ? args.get(index) : null;
    }

    /** Arguments from {@code index} onwards joined with spaces, or null if there are none. */
    public String rest(int index) {
        return index < args.size() ? String.join(" ", args.subList(index, args.size())) : null;
    }
}
