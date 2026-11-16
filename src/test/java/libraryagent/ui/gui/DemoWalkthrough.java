package libraryagent.ui.gui;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.GridPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import libraryagent.controller.AgentController;
import libraryagent.domain.Book;
import libraryagent.domain.Loan;
import libraryagent.domain.ReadingList;

/**
 * Drives the real GUI through every feature for the demonstration video. It starts
 * {@link LibraryApp} unchanged and then presses the same buttons and fills the same fields a
 * person would, with pauses, while ffmpeg records the window. Nothing is mocked: the assistant
 * replies come from the live model, so run it only when a recording is wanted (it costs money).
 *
 * <p>Arguments: {@code <ffmpeg.exe or "-"> <output folder> [quick]}. With "-" nothing is recorded.
 * With "quick" it runs the short version: one pass per feature, typed at a person's speed, with each
 * feature's label typed into the chat box before its request.
 */
public final class DemoWalkthrough {

    private static final long START_NANOS = System.nanoTime();
    private static long recordingStart;
    private static PrintWriter timeline;
    private static Process recorder;
    private static MainWindow main;
    private static AgentController controller;
    private static boolean quick;
    private static final java.util.Random RANDOM = new java.util.Random();

    private DemoWalkthrough() {
    }

    public static void main(String[] args) throws Exception {
        String ffmpeg = args.length > 0 ? args[0] : "-";
        Path out = Path.of(args.length > 1 ? args[1] : "build/demo");
        quick = args.length > 2 && args[2].equals("quick");
        Files.createDirectories(out);
        Thread app = new Thread(() -> Application.launch(LibraryApp.class, "--reset-db"), "demo-app");
        app.setDaemon(true);
        app.start();

        Stage stage = waitFor(() -> Window.getWindows().stream()
                .filter(w -> w instanceof Stage && w.isShowing()).map(w -> (Stage) w).findFirst().orElse(null), 60);
        fx(() -> {
            var screen = Screen.getPrimary().getVisualBounds();
            stage.setWidth(Math.min(1280, screen.getWidth()));
            stage.setHeight(Math.min(880, screen.getHeight()));
            stage.setX(screen.getMinX() + 20);
            stage.setY(screen.getMinY() + 10);
            stage.toFront();
            return null;
        });
        main = (MainWindow) fx(() -> stage.getScene().getRoot());
        Field field = MainWindow.class.getDeclaredField("controller");
        field.setAccessible(true);
        controller = (AgentController) field.get(main);
        sleep(1500);

        timeline = new PrintWriter(Files.newBufferedWriter(out.resolve("gui-timeline.txt"), StandardCharsets.UTF_8), true);
        if (!ffmpeg.equals("-")) {
            recorder = new ProcessBuilder(ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i",
                    "gfxcapture=window_title='^Library Agent$':capture_cursor=false:max_framerate=20",
                    "-vf", "hwdownload,format=bgra,scale=trunc(iw/2)*2:trunc(ih/2)*2,format=yuv420p",
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", out.resolve("gui-raw.mp4").toString())
                    .redirectErrorStream(true)
                    .redirectOutput(out.resolve("gui-ffmpeg.log").toFile())
                    .start();
            sleep(2500);
        }
        recordingStart = System.nanoTime();
        try {
            if (quick) {
                quickScript();
            } else {
                script();
            }
        } catch (Throwable t) {
            caption("STOPPED: " + t);
            t.printStackTrace();
        } finally {
            caption("END");
            sleep(1500);
            if (recorder != null) {
                try (OutputStream in = recorder.getOutputStream()) {
                    in.write('q');
                    in.flush();
                }
                recorder.waitFor(30, TimeUnit.SECONDS);
            }
            timeline.close();
            Platform.exit();
            System.exit(0);
        }
    }

    // ---- The walkthrough ---------------------------------------------------------------------

    private static void script() throws Exception {
        caption("Library Agent: GUI walkthrough. The real application, driven automatically; "
                + "assistant replies come live from Claude Sonnet 5.5");
        sleep(4500);

        // F01 ------------------------------------------------------------------------------
        caption("F01 Conversational registration. Error case: the registration form refuses an email "
                + "that already has an account (deterministic rule)");
        type(labeled("Name"), "Jordan Lee");
        type(labeled("Email"), "alex.morgan@example.com");
        click("Create account");
        sleep(4000);
        caption("F01 Conversational registration. The assistant collects the details in conversation and "
                + "calls the registerMember tool; Java validates and creates the account");
        click("Sign up by chatting with the assistant");
        waitChat();
        chat("I'm Jordan Lee, my email is jordan.lee@example.com, and I'm a standard member. "
                + "I enjoy Fantasy and Science Fiction, I like Ursula K. Le Guin, and I prefer books under 400 pages.");
        if (controller.currentMember().isEmpty()) {
            chat("Yes, that's all correct. Please create my account.");
        }
        sleep(2500);

        // F02, F13 -------------------------------------------------------------------------
        caption("F02 Natural-language search. The assistant turns the request into a searchCatalog tool call; "
                + "only books returned by the tool are shown as cards");
        chat("Find me science fiction books that are under 320 pages.");
        caption("F13 Conversation memory. A follow-up refers to the previous answer");
        chat("Tell me more about the second one.");
        caption("F02 Natural-language search. Error case: nothing matches, and the assistant does not invent titles");
        chat("Do you have any books about underwater basket weaving?");
        caption("F02 Search without AI: keywords, genre and length in the Catalog tab");
        tab("Catalog");
        type(prompt("Title, author or topic"), "murder");
        click("Search");
        sleep(3000);
        caption("F02 Search. Error case: the length filter must be a number");
        type(prompt("Max pages"), "short");
        click("Search");
        sleep(3500);
        click("Show all");
        sleep(1500);

        // F10 ------------------------------------------------------------------------------
        caption("F10 Grounded questions. \"Ask about this book\" sends the question to the assistant, which answers "
                + "from the catalogue record. This book's description contains a planted instruction, which is ignored");
        searchAndSelect("lighthouse", "The Lighthouse Ledger");
        click("Ask about this book");
        waitChat();
        caption("F10 Grounded questions. Alternative case: a book the library does not own");
        chat("Do you have Nineteen Eighty-Four by George Orwell?");

        // F04 ------------------------------------------------------------------------------
        caption("F04 Multi-book find and reserve. One request, several tool calls: search, check availability, "
                + "reserve. Each reservation runs through an undoable command");
        chat("Reserve two short fantasy books for me, whichever are available right now.");
        caption("F04/F05 Alternative case: every copy is out, so the assistant offers the hold queue "
                + "and only joins it after the member agrees");
        chat("Also reserve The Hobbit for me.");
        chat("Yes, please add me to the queue.");
        caption("F05 Holds in the Account tab: ready holds with a pick-up deadline, and the queue position");
        tab("Account");
        sleep(5000);
        caption("F04 Direct reservation with the Catalog tab's Reserve button (no AI involved)");
        searchAndSelect("foundation", "Foundation");
        click("Reserve");
        sleep(3500);
        caption("F04 Error case: reserving the same book twice is refused");
        click("Join hold queue", "Reserve");
        sleep(4000);

        // F12 ------------------------------------------------------------------------------
        caption("F12 Undo. The toolbar's Undo button reverses the last action, whichever interface made it");
        click("Undo");
        sleep(4000);
        caption("F12 Undo by chat: the assistant calls the undoLastAction tool");
        tab("Chat");
        chat("Undo my last action.");
        caption("F05 Joining a hold queue with the Catalog tab's button");
        searchAndSelect("hobbit", "The Hobbit");
        click("Join hold queue");
        sleep(4000);

        // F03 ------------------------------------------------------------------------------
        caption("F03 Reading lists. Create a list in the Account tab");
        tab("Account");
        reveal(prompt("New list name"));
        type(prompt("New list name"), "Weekend reads");
        click("Create");
        sleep(2500);
        caption("F03 Reading lists. Add books from the Catalog tab");
        for (String[] book : new String[][] {{"piranesi", "Piranesi"}, {"earthsea", "A Wizard of Earthsea"},
                {"martian", "The Martian"}}) {
            searchAndSelect(book[0], book[1]);
            choose("Reading list", "Weekend reads");
            click("Add to list");
            sleep(2200);
        }
        caption("F03 Error case: a book cannot be added to the same list twice");
        choose("Reading list", "Weekend reads");
        click("Add to list");
        sleep(4000);
        caption("F03 Reading lists. Reorder and remove; every change can be undone");
        tab("Account");
        reveal(prompt("New list name"));
        selectList("Weekend reads");
        sleep(1500);
        selectListBook("The Martian");
        click("Move up");
        sleep(2000);
        click("Move up");
        sleep(2500);
        selectListBook("A Wizard of Earthsea");
        click("Remove");
        sleep(3000);
        caption("F03 Reading lists by chat: the assistant calls the manageReadingList tool");
        tab("Chat");
        chat("Add Dune to my Weekend reads list.");

        // F09 ------------------------------------------------------------------------------
        caption("F09 Recommendations. \"Recommended for you\" is computed in Java from favourite genres, "
                + "authors and ratings (no AI)");
        tab("Catalog");
        click("Show all");
        sleep(6000);
        caption("F09 Curated lists. The assistant picks from Java's recommendation candidates only, "
                + "names the list and saves it");
        tab("Account");
        reveal(prompt("New list name"));
        click("Generate a list with AI");
        waitChat();
        tab("Account");
        reveal(prompt("New list name"));
        sleep(5000);
        click("Sign out");
        sleep(2000);

        // F08 (Sam) ------------------------------------------------------------------------
        caption("F08 Renewals. Sam has the only copy of The Hobbit. Error case: a renewal is refused "
                + "while another member is waiting for the book");
        signIn("M002");
        tab("Account");
        sleep(2500);
        selectRow(table(0), l -> ((Loan) l).getBook().getTitle().equals("The Hobbit"));
        click("Renew selected");
        sleep(5000);
        click("Sign out");
        sleep(1500);

        // Librarian, part 1 ----------------------------------------------------------------
        caption("F12 Undo. Error case: a new session has nothing to undo");
        signIn("LIB001");
        click("Undo");
        sleep(3500);
        caption("F08 Overdue loans. The librarian's list shows who is late and the fee so far "
                + "(fee policy chosen by member type)");
        tab("Librarian");
        click("Check for overdue loans");
        sleep(5000);
        String hobbitCopy = copyOf("The Hobbit");
        String duneCopy = fx(() -> lateLoan("Dune").getCopy().getCopyId());
        caption("F07 Return. Checking in The Hobbit closes Sam's loan and holds the copy for the next member in the queue");
        type(prompt("Copy ID being returned"), hobbitCopy);
        click("Check in");
        sleep(5500);
        caption("F06 Checkout. Error case: a copy held for one member cannot be lent to another "
                + "(decided by the copy's state)");
        type(prompt("Member ID, e.g. M002"), "M001");
        type(prompt("Copy ID, e.g. C0010"), hobbitCopy);
        click("Check out");
        sleep(5500);
        click("Sign out");
        sleep(1500);

        // Jordan: hold ready ---------------------------------------------------------------
        caption("F05 Hold queue. Jordan's hold is now ready, with the copy and the pick-up deadline (3 days)");
        signIn("M003");
        tab("Account");
        sleep(6000);
        click("Sign out");
        sleep(1500);

        // Librarian, part 2 ----------------------------------------------------------------
        caption("F06 Checkout. The librarian lends the held copy to Jordan; the hold is fulfilled");
        signIn("LIB001");
        tab("Librarian");
        type(prompt("Member ID, e.g. M002"), "M003");
        type(prompt("Copy ID, e.g. C0010"), hobbitCopy);
        click("Check out");
        sleep(5000);
        caption("F06 Checkout with limits. Alex (standard member, limit 5) already has one loan; "
                + "four more checkouts succeed");
        List<String> spare = fx(() -> controller.searchKeywords("the").stream()
                .filter(b -> !List.of("The Hobbit", "Dune", "The Martian").contains(b.getTitle()))
                .flatMap(b -> b.getCopies().stream().filter(c -> c.isAvailable()).limit(1))
                .map(c -> c.getCopyId()).limit(5).toList());
        type(prompt("Member ID, e.g. M002"), "M001");
        for (int i = 0; i < 4; i++) {
            type(prompt("Copy ID, e.g. C0010"), spare.get(i));
            click("Check out");
            sleep(1800);
        }
        caption("F06 Error case: the sixth loan is refused by the borrowing limit");
        type(prompt("Copy ID, e.g. C0010"), spare.get(4));
        click("Check out");
        sleep(5500);
        caption("F07 Return of an overdue book: the late fee is calculated by the member's fee policy");
        type(prompt("Copy ID being returned"), duneCopy);
        click("Check in");
        sleep(5500);
        caption("F12 Undo of a return: the loan is reopened and the copy is overdue again");
        click("Undo");
        sleep(5000);

        // F11 ------------------------------------------------------------------------------
        caption("F11 Add a book with AI genre tagging. The assistant suggests subgenres; "
                + "suggestions outside the genre hierarchy are dropped");
        reveal(labeled("ISBN"));
        type(labeled("ISBN"), "9781000000001");
        type(labeled("Title"), "The Clockwork Orchard");
        type(labeled("Author"), "Mira Tanaka");
        type(labeled("Pages"), "312");
        type(labeled("Description"), "A young inventor discovers a hidden orchard of mechanical trees and must "
                + "protect it from a rival guild of mages in this adventure of magic and machines.");
        click("Suggest genres");
        waitFor(() -> fx(() -> !button("Suggest genres").isDisabled()) ? Boolean.TRUE : null, 120);
        sleep(4500);
        caption("F11 Error case: the ISBN belongs to a book already in the catalogue");
        reveal(labeled("ISBN"));
        click("Add book");
        sleep(5000);
        caption("F11 With a new ISBN the book is added and filed under the chosen subgenres");
        type(labeled("ISBN"), "9781000000041");
        click("Add book");
        sleep(5000);
        searchAndSelect("clockwork", "The Clockwork Orchard");
        sleep(4000);
        click("Sign out");
        sleep(1500);

        // Jordan renews --------------------------------------------------------------------
        caption("F08 Renewals. Jordan renews The Hobbit: nobody is waiting and it is not overdue, so it succeeds");
        signIn("M003");
        tab("Account");
        sleep(2000);
        selectRow(table(0), l -> ((Loan) l).getBook().getTitle().equals("The Hobbit"));
        click("Renew selected");
        sleep(5000);
        click("Sign out");
        sleep(1500);

        // Alex: overdue, fees, feedback ----------------------------------------------------
        caption("F08 Overdue and fees. Alex's copy of Dune is overdue; the fee so far is shown. "
                + "Error case: an overdue loan cannot be renewed");
        signIn("M001");
        tab("Account");
        sleep(3000);
        selectRow(table(0), l -> ((Loan) l).getBook().getTitle().equals("Dune"));
        click("Renew selected");
        sleep(5500);
        caption("F14 Reading feedback. Rate a book from the borrowing history; ratings shape recommendations");
        reveal(button("Rate selected"));
        selectRow(table(2), l -> ((Loan) l).getBook().getTitle().equals("The Martian"));
        choose("Did you like it?", "Liked");
        type(prompt("Comment (optional)"), "Funny and clever");
        click("Rate selected");
        sleep(4500);
        caption("F14 Error case: with tracking turned off, ratings are not saved");
        reveal(first(CheckBox.class, c -> true));
        toggleTracking();
        sleep(2500);
        reveal(button("Rate selected"));
        selectRow(table(2), l -> ((Loan) l).getBook().getTitle().equals("And Then There Were None"));
        choose("Did you like it?", "Disliked");
        click("Rate selected");
        sleep(5000);
        reveal(first(CheckBox.class, c -> true));
        toggleTracking();
        sleep(2500);
        caption("F09/F14 Recommendations by chat use the member's ratings and preferences");
        tab("Chat");
        chat("Based on what I've liked, what should I read next?");
        caption("End of the GUI walkthrough");
        sleep(4000);
    }

    // ---- The short walkthrough ----------------------------------------------------------------

    private static int wordsPerMinute = 45;

    /** The pause after a key, for typing at 40 to 50 words a minute with an uneven rhythm. */
    private static long keyDelay(char typed) {
        if (typed == ' ') {
            wordsPerMinute = 40 + RANDOM.nextInt(11);
        }
        double perKey = 60000.0 / (wordsPerMinute * 5);
        double delay = perKey * (0.65 + RANDOM.nextDouble() * 0.6);
        if ((typed == ',' || typed == '.' || typed == ':') && RANDOM.nextInt(3) == 0) {
            delay += 150 + RANDOM.nextInt(250);
        }
        return Math.round(delay);
    }

    /** Types the feature's label into the chat box, leaves it there for a moment and clears it. */
    private static void label(String text) throws Exception {
        caption(text);
        tab("Chat");
        ChatView view = first(ChatView.class, v -> true);
        TextInputControl input = fx(() -> within(view, TextInputControl.class).get(0));
        type(input, text);
        sleep(1300);
        fx(() -> {
            input.clear();
            return null;
        });
        sleep(300);
    }

    private static void quickScript() throws Exception {
        sleep(1500);
        click("Sign up by chatting with the assistant");
        waitChat();
        label("F01 Conversational registration");
        chat("Sign me up: Jordan Lee, jordan.lee@example.com, standard member. I like Fantasy.");
        if (controller.currentMember().isEmpty()) {
            chat("Yes, create it.");
        }

        label("F02 Natural-language search");
        chat("Find sci-fi books under 320 pages");

        label("F13 Memory and F10 Grounded Q&A");
        chat("What is the second one about?");

        label("F04 Multi-book reserve and F05 Hold queue");
        chat("Reserve Piranesi and The Hobbit");
        chat("Yes, join the queue");

        label("F12 Undo last action");
        click("Undo");
        sleep(3200);

        label("F03 Reading lists");
        chat("Make a list called Weekend with Dune");
        tab("Account");
        reveal(prompt("New list name"));
        sleep(3000);

        label("F09 AI-curated reading list");
        tab("Account");
        reveal(prompt("New list name"));
        click("Generate a list with AI");
        waitChat();
        click("Sign out");
        sleep(1200);

        signIn("M001");
        label("F14 Reading feedback");
        chat("I loved The Martian");
        label("F08 Renewals and late fees");
        chat("Renew Dune");
        click("Sign out");
        sleep(1200);

        signIn("LIB001");
        label("F06 Checkout and F07 Return");
        tab("Librarian");
        click("Check for overdue loans");
        sleep(2500);
        String duneCopy = fx(() -> lateLoan("Dune").getCopy().getCopyId());
        String spare = fx(() -> controller.searchKeywords("the").stream()
                .filter(b -> !List.of("The Hobbit", "Dune", "The Martian", "Piranesi").contains(b.getTitle()))
                .flatMap(b -> b.getCopies().stream().filter(c -> c.isAvailable()).limit(1))
                .map(c -> c.getCopyId()).findFirst().orElseThrow());
        type(prompt("Member ID, e.g. M002"), "M002");
        type(prompt("Copy ID, e.g. C0010"), spare);
        click("Check out");
        sleep(3500);
        type(prompt("Copy ID being returned"), duneCopy);
        click("Check in");
        sleep(4000);

        label("F11 AI genre tagging");
        tab("Librarian");
        reveal(labeled("ISBN"));
        type(labeled("ISBN"), "9781000000041");
        type(labeled("Title"), "The Clockwork Orchard");
        type(labeled("Author"), "Mira Tanaka");
        type(labeled("Pages"), "312");
        type(labeled("Description"), "An inventor guards a magical clockwork orchard.");
        click("Suggest genres");
        waitFor(() -> fx(() -> !button("Suggest genres").isDisabled()) ? Boolean.TRUE : null, 120);
        sleep(3500);
        reveal(labeled("ISBN"));
        click("Add book");
        sleep(4000);
    }

    // ---- Steps -------------------------------------------------------------------------------

    private static void signIn(String memberId) throws Exception {
        type(prompt("Member ID, e.g. M001"), memberId);
        click("Sign in");
        sleep(2500);
    }

    private static void tab(String name) throws Exception {
        fx(() -> {
            main.selectTab(name);
            return null;
        });
        sleep(quick ? 900 : 1800);
    }

    /** Types a message in the Chat tab, sends it and waits for the assistant's reply. */
    private static void chat(String message) throws Exception {
        tab("Chat");
        ChatView view = first(ChatView.class, v -> true);
        TextInputControl input = fx(() -> within(view, TextInputControl.class).get(0));
        type(input, message);
        sleep(600);
        click("Send");
        waitChat();
    }

    private static void waitChat() throws Exception {
        ChatView view = first(ChatView.class, v -> true);
        sleep(1200);
        waitFor(() -> fx(() -> !view.isBusy()) ? Boolean.TRUE : null, 240);
        for (int i = 0; i < 3; i++) {
            sleep(250);
            fx(() -> {
                within(view, ScrollPane.class).forEach(s -> s.setVvalue(1.0));
                return null;
            });
        }
        dismissDialogs();
        sleep(quick ? 2800 : 7500);
    }

    private static void searchAndSelect(String keywords, String title) throws Exception {
        tab("Catalog");
        type(prompt("Title, author or topic"), keywords);
        click("Search");
        sleep(1200);
        CatalogView view = first(CatalogView.class, v -> true);
        @SuppressWarnings("unchecked")
        TableView<Object> results = (TableView<Object>) fx(() -> within(view, TableView.class).get(0));
        selectRow(results, b -> ((Book) b).getTitle().equals(title));
        sleep(2500);
    }

    private static String copyOf(String title) throws Exception {
        return fx(() -> controller.searchKeywords(title).stream().filter(b -> b.getTitle().equals(title))
                .findFirst().orElseThrow().getCopies().get(0).getCopyId());
    }

    private static Loan lateLoan(String title) {
        LibrarianView view = (LibrarianView) all(LibrarianView.class).get(0);
        for (TableView<?> table : within(view, TableView.class)) {
            for (Object item : table.getItems()) {
                if (item instanceof Loan loan && loan.getBook().getTitle().equals(title)) {
                    return loan;
                }
            }
        }
        throw new IllegalStateException("No overdue loan of " + title);
    }

    /** The n-th table of the Account tab: 0 loans, 1 holds, 2 history. */
    @SuppressWarnings("unchecked")
    private static TableView<Object> table(int index) throws Exception {
        AccountView view = first(AccountView.class, v -> true);
        return (TableView<Object>) fx(() -> within(view, TableView.class).get(index));
    }

    private static void selectRow(TableView<Object> table, Predicate<Object> match) throws Exception {
        fx(() -> {
            Object item = table.getItems().stream().filter(match).findFirst()
                    .orElseThrow(() -> new IllegalStateException("row not found in " + table.getItems()));
            table.requestFocus();
            table.getSelectionModel().select(item);
            table.scrollTo(item);
            return null;
        });
        sleep(1200);
    }

    @SuppressWarnings("unchecked")
    private static void selectList(String name) throws Exception {
        AccountView view = first(AccountView.class, v -> true);
        fx(() -> {
            ListView<Object> lists = (ListView<Object>) within(view, ListView.class).get(0);
            Object list = lists.getItems().stream().filter(l -> ((ReadingList) l).getName().equals(name))
                    .findFirst().orElseThrow();
            lists.getSelectionModel().select(list);
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static void selectListBook(String title) throws Exception {
        AccountView view = first(AccountView.class, v -> true);
        fx(() -> {
            ListView<Object> books = (ListView<Object>) within(view, ListView.class).get(1);
            Object book = books.getItems().stream().filter(b -> ((Book) b).getTitle().equals(title))
                    .findFirst().orElseThrow();
            books.requestFocus();
            books.getSelectionModel().select(book);
            return null;
        });
        sleep(1200);
    }

    private static void toggleTracking() throws Exception {
        CheckBox box = first(CheckBox.class, c -> true);
        Platform.runLater(box::fire);
    }

    @SuppressWarnings("unchecked")
    private static void choose(String promptText, String value) throws Exception {
        ComboBox<Object> combo = (ComboBox<Object>) first(ComboBox.class, c -> promptText.equals(c.getPromptText()));
        fx(() -> {
            combo.requestFocus();
            combo.show();
            return null;
        });
        sleep(700);
        fx(() -> {
            combo.getSelectionModel().select(value);
            combo.hide();
            return null;
        });
        sleep(600);
    }

    // ---- Finding and operating controls -------------------------------------------------------

    private static void click(String... texts) throws Exception {
        Button button = fx(() -> {
            for (String text : texts) {
                Optional<Button> found = all(Button.class).stream().map(n -> (Button) n)
                        .filter(b -> text.equals(b.getText()) && !b.isDisabled()).findFirst();
                if (found.isPresent()) {
                    return found.get();
                }
            }
            throw new IllegalStateException("No enabled button " + List.of(texts));
        });
        fx(() -> {
            button.requestFocus();
            button.arm();
            return null;
        });
        sleep(350);
        // Not waited for: the action may open a dialog, which blocks until it is closed.
        Platform.runLater(() -> {
            button.disarm();
            button.fire();
        });
        sleep(700);
        dismissDialogs();
    }

    private static Button button(String text) {
        return all(Button.class).stream().map(n -> (Button) n).filter(b -> text.equals(b.getText()))
                .findFirst().orElseThrow(() -> new IllegalStateException("No button " + text));
    }

    private static TextInputControl prompt(String promptText) throws Exception {
        return first(TextInputControl.class, c -> promptText.equals(c.getPromptText()));
    }

    /** The field next to a form label, e.g. "Name" in the registration form. */
    private static TextInputControl labeled(String labelText) throws Exception {
        return fx(() -> {
            for (Node n : all(Label.class)) {
                Label label = (Label) n;
                if (labelText.equals(label.getText()) && label.getParent() instanceof GridPane grid) {
                    int row = Optional.ofNullable(GridPane.getRowIndex(label)).orElse(0);
                    int column = Optional.ofNullable(GridPane.getColumnIndex(label)).orElse(0);
                    for (Node other : grid.getChildren()) {
                        if (other instanceof TextInputControl field
                                && Optional.ofNullable(GridPane.getRowIndex(other)).orElse(0) == row
                                && Optional.ofNullable(GridPane.getColumnIndex(other)).orElse(0) == column + 1) {
                            return field;
                        }
                    }
                }
            }
            throw new IllegalStateException("No field labelled " + labelText);
        });
    }

    private static void type(TextInputControl field, String text) throws Exception {
        fx(() -> {
            field.requestFocus();
            field.clear();
            return null;
        });
        sleep(250);
        for (char c : text.toCharArray()) {
            fx(() -> {
                field.appendText(String.valueOf(c));
                return null;
            });
            sleep(quick ? keyDelay(c) : text.length() > 60 ? 14 : 45);
        }
        sleep(450);
    }

    /** Scrolls the enclosing scroll pane so the node is in view. */
    private static void reveal(Node node) throws Exception {
        fx(() -> {
            for (Parent p = node.getParent(); p != null; p = p.getParent()) {
                if (p instanceof ScrollPane pane && pane.getContent() != null
                        && (pane instanceof AccountView || pane instanceof LibrarianView)) {
                    Bounds content = pane.getContent().getBoundsInLocal();
                    Bounds target = pane.getContent().sceneToLocal(node.localToScene(node.getBoundsInLocal()));
                    double scrollable = content.getHeight() - pane.getViewportBounds().getHeight();
                    double wanted = target.getMinY() - pane.getViewportBounds().getHeight() / 3;
                    pane.setVvalue(scrollable <= 0 ? 0 : Math.max(0, Math.min(1, wanted / scrollable)));
                    break;
                }
            }
            return null;
        });
        sleep(900);
    }

    private static void dismissDialogs() throws Exception {
        for (int i = 0; i < 3; i++) {
            Button ok = fx(() -> Window.getWindows().stream()
                    .filter(w -> w.isShowing() && w.getScene() != null && w.getScene().getRoot() != main)
                    .flatMap(w -> collect(w.getScene().getRoot(), new ArrayList<>()).stream())
                    .filter(n -> n instanceof Button b && b.isDefaultButton()).map(n -> (Button) n)
                    .findFirst().orElse(null));
            if (ok == null) {
                return;
            }
            sleep(2500);
            Platform.runLater(ok::fire);
            sleep(600);
        }
    }

    private static <T extends Node> T first(Class<T> type, Predicate<T> match) throws Exception {
        return fx(() -> all(type).stream().map(type::cast).filter(match).findFirst()
                .orElseThrow(() -> new IllegalStateException("No visible " + type.getSimpleName())));
    }

    /** Every visible node of the type in the main window. Call on the FX thread. */
    private static List<Node> all(Class<? extends Node> type) {
        return within(main, type).stream().map(n -> (Node) n).toList();
    }

    private static <T> List<T> within(Node root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Node node : collect(root, new ArrayList<>())) {
            if (type.isInstance(node) && shown(node)) {
                found.add(type.cast(node));
            }
        }
        return found;
    }

    private static List<Node> collect(Node node, List<Node> into) {
        into.add(node);
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, into);
            }
        }
        return into;
    }

    private static boolean shown(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return node.getScene() != null;
    }

    // ---- Plumbing ----------------------------------------------------------------------------

    private static void caption(String text) {
        double seconds = (System.nanoTime() - (recordingStart == 0 ? START_NANOS : recordingStart)) / 1e9;
        String line = String.format(Locale.ROOT, "%.2f\t%s", seconds, text);
        System.out.println(line);
        if (timeline != null) {
            timeline.println(line);
        }
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return action.call();
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result.get(60, TimeUnit.SECONDS);
    }

    private static <T> T waitFor(Callable<T> probe, int seconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            try {
                T value = probe.call();
                if (value != null) {
                    return value;
                }
            } catch (IllegalStateException | IndexOutOfBoundsException e) {
                // not there yet
            }
            sleep(200);
        }
        throw new IOException("Timed out after " + seconds + " s");
    }

    private static void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
