package libraryagent.ui.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns a line of CLI input into a {@link CliAction}. A line is a command only when it starts
 * with a command word <em>and</em> its arguments fit that command's shape; anything else (for
 * example "reserve two short sci-fi books" or "undo that") is plain language for the AI
 * assistant. Double quotes group words, so {@code list add "Summer reads" 9781000000001} has
 * three arguments.
 */
public class CliCommandParser {

    private static final Pattern ANY = Pattern.compile(".*", Pattern.DOTALL);
    private static final Pattern ISBN = Pattern.compile("[0-9][0-9-]{8,16}[0-9Xx]");
    private static final Pattern MEMBER_ID = Pattern.compile("(?i)[a-z]+\\d+");
    private static final Pattern COPY_ID = Pattern.compile("(?i)c\\d+");
    private static final Pattern LOAN_ID = Pattern.compile("(?i)l\\d+");
    private static final Pattern RESERVATION_ID = Pattern.compile("(?i)r\\d+");
    private static final Pattern LIST_ACTION = Pattern.compile("(?i)create|show|add|remove|move");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+");
    private static final Pattern RATING = Pattern.compile("(?i)liked|neutral|disliked");
    private static final Pattern ON_OFF = Pattern.compile("(?i)on|off");

    /** Most arguments a command takes, and the pattern each argument must match. */
    private record Shape(int maxArgs, List<Pattern> patterns) {
        static Shape none() {
            return new Shape(0, List.of());
        }

        static Shape of(Pattern... patterns) {
            return new Shape(patterns.length, List.of(patterns));
        }

        boolean fits(List<String> args) {
            if (args.size() > maxArgs) {
                return false;
            }
            for (int i = 0; i < args.size(); i++) {
                Pattern p = i < patterns.size() ? patterns.get(i) : ANY;
                if (!p.matcher(args.get(i)).matches()) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final Map<String, Shape> COMMANDS = Map.ofEntries(
            Map.entry("help", Shape.none()),
            Map.entry("exit", Shape.none()),
            Map.entry("quit", Shape.none()),
            Map.entry("logout", Shape.none()),
            Map.entry("whoami", Shape.none()),
            Map.entry("genres", Shape.none()),
            Map.entry("overdue", Shape.none()),
            Map.entry("account", Shape.none()),
            Map.entry("history", Shape.none()),
            Map.entry("lists", Shape.none()),
            Map.entry("register", Shape.none()),
            Map.entry("addbook", Shape.none()),
            Map.entry("undo", Shape.none()),
            Map.entry("recommend", Shape.none()),
            Map.entry("tracking", Shape.of(ON_OFF)),
            Map.entry("rate", new Shape(Integer.MAX_VALUE, List.of(ISBN, RATING))),
            Map.entry("login", Shape.of(MEMBER_ID)),
            Map.entry("search", new Shape(Integer.MAX_VALUE, List.of())),
            Map.entry("book", Shape.of(ISBN)),
            Map.entry("reserve", Shape.of(ISBN)),
            Map.entry("cancel", Shape.of(RESERVATION_ID)),
            Map.entry("renew", Shape.of(LOAN_ID)),
            Map.entry("return", Shape.of(COPY_ID)),
            Map.entry("checkout", Shape.of(MEMBER_ID, COPY_ID)),
            Map.entry("list", Shape.of(LIST_ACTION, ANY, ISBN, NUMBER)));

    public CliAction parse(String line) {
        String trimmed = line == null ? "" : line.strip();
        if (trimmed.isEmpty()) {
            return new CliAction("empty", List.of());
        }
        List<String> tokens = tokenize(trimmed);
        String first = tokens.get(0).toLowerCase(Locale.ROOT);
        List<String> args = List.copyOf(tokens.subList(1, tokens.size()));
        Shape shape = COMMANDS.get(first);
        if (shape == null || !shape.fits(args)) {
            return new CliAction("chat", List.of(trimmed));
        }
        return new CliAction(first.equals("quit") ? "exit" : first, args);
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
