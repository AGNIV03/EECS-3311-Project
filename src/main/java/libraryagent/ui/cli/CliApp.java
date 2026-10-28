package libraryagent.ui.cli;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import libraryagent.agent.AgentFactory;
import libraryagent.agent.AgentResponse;
import libraryagent.agent.GenreTagger;
import libraryagent.agent.Planner;
import libraryagent.agent.ToolTrace;
import libraryagent.agent.llm.ClaudeClient;
import libraryagent.agent.llm.TokenUsage;
import libraryagent.command.CommandResult;
import libraryagent.config.AppConfig;
import libraryagent.controller.AccountSummary;
import libraryagent.controller.AgentController;
import libraryagent.controller.LibraryServices;
import libraryagent.controller.SessionContext;
import libraryagent.domain.Book;
import libraryagent.domain.BookFeedback;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Loan;
import libraryagent.domain.Member;
import libraryagent.domain.MemberType;
import libraryagent.domain.Rating;
import libraryagent.domain.ReadingList;

/**
 * Command-line interface. Short commands map directly to {@link AgentController} methods;
 * anything else is plain language for the AI assistant.
 *
 * <p>Options: {@code --member <id>} logs in on start, {@code --reset-db} deletes the database
 * so it is re-created from the sample data.
 */
public class CliApp {

    private final BufferedReader in;
    private final PrintStream out;
    private final AgentController controller;
    private final boolean llmEnabled;
    private final CliCommandParser parser = new CliCommandParser();
    private boolean verbose;

    public CliApp(BufferedReader in, PrintStream out, AgentController controller, boolean llmEnabled) {
        this.in = in;
        this.out = out;
        this.controller = controller;
        this.llmEnabled = llmEnabled;
    }

    /** When on, each tool the assistant calls is printed with its arguments and outcome. */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public static void main(String[] args) throws IOException {
        AppConfig config = AppConfig.load();
        List<String> options = Arrays.asList(args);
        if (options.contains("--reset-db")) {
            Files.deleteIfExists(config.dbPath());
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        try (LibraryServices services = LibraryServices.open(config.dbPath(), Clock.systemDefaultZone())) {
            ClaudeClient llm = config.apiKey().map(key -> new ClaudeClient(key, config.effort())).orElse(null);
            Planner planner = llm == null ? null : AgentFactory.createPlanner(services, llm, config.maxSteps());
            GenreTagger tagger = llm == null ? null : AgentFactory.createTagger(services, llm);
            AgentController controller = new AgentController(services, new SessionContext(), planner, tagger);
            controller.subscribe(new CliNotifier(out, controller));
            CliApp app = new CliApp(in, out, controller, controller.assistantAvailable());
            app.setVerbose(options.contains("--verbose"));
            int memberFlag = options.indexOf("--member");
            if (memberFlag >= 0 && memberFlag + 1 < options.size()) {
                app.dispatch(new CliAction("login", List.of(options.get(memberFlag + 1))));
            }
            app.repl();
        }
    }

    public void repl() throws IOException {
        out.println("Library Agent CLI. Type 'help' for commands, 'exit' to quit.");
        if (!llmEnabled) {
            out.println("(AI assistant disabled: no API key configured)");
        }
        while (true) {
            out.print(controller.currentMember().map(m -> m.getMemberId() + "> ").orElse("> "));
            out.flush();
            String line = in.readLine();
            if (line == null) {
                break;
            }
            CliAction action = parser.parse(line);
            if (action.kind().equals("exit")) {
                break;
            }
            dispatch(action);
        }
        TokenUsage usage = controller.assistantUsage();
        if (usage.inputTokens() + usage.outputTokens() > 0) {
            out.println("AI usage this session: " + usage.inputTokens() + " input tokens ("
                    + usage.cacheReadTokens() + " read from cache, " + usage.cacheWriteTokens() + " written to cache), "
                    + usage.outputTokens() + " output tokens, about $" + usage.estimatedCostUsd().toPlainString() + ".");
        }
        out.println("Goodbye.");
    }

    void dispatch(CliAction action) throws IOException {
        try {
            switch (action.kind()) {
                case "empty" -> { }
                case "help" -> out.println(CliPresenter.HELP);
                case "login" -> login(action);
                case "logout" -> {
                    controller.logout();
                    out.println("Logged out.");
                }
                case "whoami" -> out.println(controller.currentMember()
                        .map(m -> m.getName() + " (" + m.getMemberId() + (m.isLibrarian() ? ", librarian" : "") + ")")
                        .orElse("Not logged in."));
                case "register" -> register();
                case "search" -> search(action);
                case "book" -> book(action);
                case "genres" -> out.println("Subgenres: " + String.join(", ", controller.subgenres()));
                case "reserve" -> print(controller.reserve(currentMemberId(), require(action, 0, "reserve <isbn>")));
                case "cancel" -> print(controller.cancelReservation(require(action, 0, "cancel <reservationId>")));
                case "renew" -> print(controller.renew(require(action, 0, "renew <loanId>")));
                case "checkout" -> print(controller.checkOut(require(action, 0, "checkout <memberId> <copyId>"),
                        require(action, 1, "checkout <memberId> <copyId>")));
                case "return" -> print(controller.checkIn(require(action, 0, "return <copyId>")));
                case "overdue" -> overdue();
                case "account" -> account();
                case "history" -> history();
                case "lists" -> lists();
                case "list" -> list(action);
                case "addbook" -> addBook();
                case "undo" -> out.println(controller.undoLast());
                case "rate" -> rate(action);
                case "tracking" -> tracking(action);
                case "recommend" -> recommend();
                case "chat" -> chat(action.arg(0));
                default -> out.println("Unknown command. Type 'help'.");
            }
        } catch (LibraryException e) {
            out.println(e.getMessage());
        }
    }

    // ---- Command handlers ----------------------------------------------------------------

    private void login(CliAction action) {
        Member member = controller.login(require(action, 0, "login <memberId>"));
        out.println("Welcome, " + member.getName() + "." + (member.isLibrarian() ? " (librarian)" : ""));
        AccountSummary summary = controller.accountSummary(member.getMemberId());
        summary.holds().stream()
                .filter(h -> h.reservation().getCopy() != null && h.queuePosition() == 0)
                .forEach(h -> out.println("[Notice: Hold ready] \"" + h.reservation().getBook().getTitle()
                        + "\" is waiting for you until " + h.reservation().pickupDeadline() + "."));
        long overdue = summary.loans().stream().filter(l -> l.isOverdue(summary.today())).count();
        if (overdue > 0) {
            out.println("[Notice: Overdue] You have " + overdue + " overdue loan(s). Type 'account' for details.");
        }
    }

    private void register() throws IOException {
        String name = ask("Name: ");
        String email = ask("Email: ");
        MemberType type = MemberType.parse(ask("Member type (standard/student): "));
        Member member = controller.register(name, email, type);
        out.println("Welcome! Your member ID is " + member.getMemberId() + ". You are now logged in.");
        controller.login(member.getMemberId());
        List<String> genres = splitList(ask("Favourite genres, comma-separated (optional): "));
        List<String> authors = splitList(ask("Favourite authors, comma-separated (optional): "));
        String pages = ask("Preferred maximum length in pages (blank for none): ");
        int maxPages = pages.isBlank() ? 0 : parseInt(pages, "Maximum length must be a number.");
        controller.updatePreferences(member.getMemberId(), genres, authors, maxPages);
        out.println("Saved your reading preferences.");
    }

    private void search(CliAction action) {
        String text = action.rest(0);
        if (text == null) {
            throw new LibraryException("Usage: search <keywords>");
        }
        List<Book> results = controller.searchKeywords(text);
        if (results.isEmpty()) {
            out.println("No books matched \"" + text + "\".");
            return;
        }
        out.println(results.size() + " result(s):");
        for (int i = 0; i < results.size(); i++) {
            out.println(CliPresenter.bookLine(i + 1, results.get(i), controller.availability(results.get(i))));
        }
    }

    private void book(CliAction action) {
        Book book = controller.bookDetails(require(action, 0, "book <isbn>"));
        out.println(CliPresenter.bookDetails(book, controller.availability(book)));
    }

    private void overdue() {
        List<Loan> overdue = controller.findOverdue();
        if (overdue.isEmpty()) {
            out.println("No loans are overdue.");
            return;
        }
        out.println(overdue.size() + " overdue loan(s):");
        java.time.LocalDate today = controller.accountSummary(currentMemberId()).today();
        overdue.forEach(l -> out.println(CliPresenter.loanLine(l, today) + " | " + l.getMember().getName()
                + " (" + l.getMember().getMemberId() + ")"));
    }

    private void account() {
        out.println(CliPresenter.account(controller.accountSummary(currentMemberId())));
    }

    private void history() {
        List<Loan> loans = controller.loanHistory(currentMemberId());
        if (loans.isEmpty()) {
            out.println("You have not borrowed any books yet.");
            return;
        }
        String memberId = currentMemberId();
        loans.forEach(l -> out.println("  " + l.getCheckoutDate() + " | " + l.getBook().getTitle() + " ("
                + l.getBook().getIsbn() + ") | " + (l.isOpen() ? "on loan" : "returned " + l.getReturnDate())
                + controller.ratingFor(memberId, l.getBook().getIsbn())
                        .map(f -> " | you rated it " + f.getRating().name().toLowerCase()).orElse("")));
    }

    private void rate(CliAction action) {
        String usage = "rate <isbn> liked|neutral|disliked [comment]";
        String isbn = require(action, 0, usage);
        Rating rating = Rating.parse(require(action, 1, usage));
        String comment = action.rest(2);
        BookFeedback saved = controller.rateBook(currentMemberId(), isbn, rating, comment == null ? "" : comment);
        out.println("Saved: you " + saved.getRating().name().toLowerCase() + " \"" + saved.getBook().getTitle()
                + "\".");
    }

    private void tracking(CliAction action) {
        boolean on = require(action, 0, "tracking on|off").equalsIgnoreCase("on");
        controller.setTracking(currentMemberId(), on);
        out.println(on ? "Reading feedback tracking is on. Your ratings will shape recommendations."
                : "Reading feedback tracking is off. Ratings won't be saved; recommendations use your stated "
                        + "preferences only.");
    }

    private void recommend() {
        List<Book> books = controller.recommendations(currentMemberId(), 5);
        if (books.isEmpty()) {
            out.println("No recommendations yet. Tell the assistant what you like, or rate books you've borrowed.");
            return;
        }
        out.println("Recommended for you:");
        for (int i = 0; i < books.size(); i++) {
            out.println(CliPresenter.bookLine(i + 1, books.get(i), controller.availability(books.get(i))));
        }
    }

    private void lists() {
        List<ReadingList> lists = controller.readingLists();
        if (lists.isEmpty()) {
            out.println("You have no reading lists. Create one with: list create \"<name>\"");
            return;
        }
        lists.forEach(l -> out.println(CliPresenter.readingList(l)));
    }

    private void list(CliAction action) {
        String usage = "list create|show|add|remove|move \"<name>\" ...";
        String sub = require(action, 0, usage).toLowerCase();
        String name = require(action, 1, usage);
        switch (sub) {
            case "create" -> print(controller.createList(name));
            case "show" -> out.println(CliPresenter.readingList(controller.readingList(name)));
            case "add" -> print(controller.addToList(name, require(action, 2, "list add \"<name>\" <isbn>")));
            case "remove" -> print(controller.removeFromList(name, require(action, 2, "list remove \"<name>\" <isbn>")));
            case "move" -> print(controller.moveInList(name, require(action, 2, "list move \"<name>\" <isbn> <n>"),
                    parseInt(require(action, 3, "list move \"<name>\" <isbn> <n>"), "Position must be a number.")));
            default -> throw new LibraryException("Usage: " + usage);
        }
    }

    private void addBook() throws IOException {
        if (controller.currentMember().map(m -> !m.isLibrarian()).orElse(true)) {
            throw new LibraryException("Only librarians can add books.");
        }
        String isbn = ask("ISBN: ");
        String title = ask("Title: ");
        String author = ask("Author: ");
        int pages = parseInt(ask("Pages: "), "Pages must be a number.");
        String description = ask("Description: ");
        int copies = parseInt(ask("Number of copies: "), "Number of copies must be a number.");
        Book draft = new Book(isbn, title, author, pages, description);
        List<String> suggested = List.of();
        if (llmEnabled) {
            out.println("(asking the assistant for genre suggestions...)");
            try {
                suggested = controller.suggestGenres(draft);
            } catch (LibraryException e) {
                out.println(e.getMessage());
            }
        }
        out.println("Subgenres: " + String.join(", ", controller.subgenres()));
        String prompt = "Subgenres, comma-separated: ";
        if (!suggested.isEmpty()) {
            out.println("Suggested: " + String.join(", ", suggested));
            prompt = "Subgenres, comma-separated (press Enter to accept the suggestion): ";
        }
        String answer = ask(prompt);
        List<String> genres = answer.isBlank() ? suggested : splitList(answer);
        Book added = controller.addBook(draft, copies, genres);
        out.println("Added \"" + added.getTitle() + "\" with " + added.getCopies().size() + " copies, filed under "
                + String.join(", ", added.getGenres()) + ".");
    }

    private void chat(String text) {
        if (!llmEnabled) {
            out.println("The AI assistant is turned off (no API key configured). Type 'help' for commands.");
            return;
        }
        out.println("(thinking...)");
        out.flush();
        AgentResponse response = controller.chat(text);
        if (verbose) {
            for (ToolTrace call : response.trace()) {
                out.println("  [tool] " + call.tool() + " " + call.args()
                        + (call.success() ? " -> ok" : " -> error: " + call.error()));
            }
        }
        out.println(response.text());
        for (int i = 0; i < response.books().size(); i++) {
            Book book = response.books().get(i);
            out.println(CliPresenter.bookLine(i + 1, book, controller.availability(book)));
        }
    }

    // ---- Helpers -------------------------------------------------------------------------

    private String currentMemberId() {
        return controller.currentMember()
                .orElseThrow(() -> new LibraryException("Please log in first."))
                .getMemberId();
    }

    private void print(CommandResult result) {
        out.println(result.message());
    }

    private String ask(String prompt) throws IOException {
        out.print(prompt);
        out.flush();
        String line = in.readLine();
        if (line == null) {
            throw new LibraryException("Cancelled.");
        }
        return line.strip();
    }

    private static String require(CliAction action, int index, String usage) {
        String value = action.arg(index);
        if (value == null || value.isBlank()) {
            throw new LibraryException("Usage: " + usage);
        }
        return value;
    }

    private static List<String> splitList(String text) {
        return Arrays.stream(text.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static int parseInt(String text, String error) {
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            throw new LibraryException(error);
        }
    }
}
