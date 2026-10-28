package libraryagent.ui.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns a line of CLI input into a {@link CliAction}. Known command words become commands;
 * anything else is plain language for the AI assistant. Double quotes group words, so
 * {@code list add "Summer reads" 9781000000001} has three arguments.
 */
public class CliCommandParser {

    static final Set<String> COMMANDS = Set.of(
            "help", "exit", "quit", "login", "logout", "whoami",
            "search", "book", "genres",
            "reserve", "cancel", "checkout", "return", "renew", "overdue",
            "account", "history", "lists", "list",
            "register", "addbook", "undo");

    public CliAction parse(String line) {
        String trimmed = line == null ? "" : line.strip();
        if (trimmed.isEmpty()) {
            return new CliAction("empty", List.of());
        }
        List<String> tokens = tokenize(trimmed);
        String first = tokens.get(0).toLowerCase(Locale.ROOT);
        if (!COMMANDS.contains(first)) {
            return new CliAction("chat", List.of(trimmed));
        }
        String kind = first.equals("quit") ? "exit" : first;
        return new CliAction(kind, List.copyOf(tokens.subList(1, tokens.size())));
    }

    static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean hasToken = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
                hasToken = true;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (hasToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    hasToken = false;
                }
            } else {
                current.append(c);
                hasToken = true;
            }
        }
        if (hasToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
