package libraryagent.ui.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import libraryagent.controller.AgentController;
import libraryagent.controller.LibraryServices;
import libraryagent.controller.SessionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Drives the CLI with scripted input against the seeded in-memory library. */
class CliAppTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T14:00:00Z"), ZoneId.of("America/Toronto"));

    private LibraryServices services;

    @BeforeEach
    void setUp() {
        services = LibraryServices.inMemory(CLOCK);
    }

    @AfterEach
    void tearDown() {
        services.close();
    }

    private String run(String... lines) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        AgentController controller = new AgentController(services, new SessionContext());
        controller.subscribe(new CliNotifier(out, controller));
        BufferedReader in = new BufferedReader(new StringReader(String.join("\n", lines) + "\n"));
        new CliApp(in, out, controller, false).repl();
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static void assertContains(String output, String expected) {
        assertTrue(output.contains(expected), () -> "Expected output to contain:\n" + expected + "\n---\n" + output);
    }

    @Test
    void searchShowsAvailabilityWithoutLogin() throws IOException {
        String output = run("search hobbit", "exit");
        assertContains(output, "9781000000001 | The Hobbit by J.R.R. Tolkien, 310 pages | all 1 out");
    }

    @Test
    void loginShowsOverdueNoticeAndAccount() throws IOException {
        String output = run("login m001", "account", "exit");
        assertContains(output, "Welcome, Alex Morgan.");
        assertContains(output, "[Notice: Overdue] You have 1 overdue loan(s).");
        assertContains(output, "Dune (copy");
        assertContains(output, "OVERDUE since");
        assertContains(output, "Fees owed: $2.25");
        assertContains(output, "M001> ");
    }

    @Test
    void reserveQueueReturnNotifyAndUndoRefusal() throws IOException {
        String output = run(
                "login M001", "reserve 9781000000001",
                "login LIB001", "return C0001",
                "undo",
                "login M001", "account",
                "exit");
        assertContains(output, "You are number 1 in the hold queue.");
        assertContains(output, "Returned \"The Hobbit\" (copy C0001) from Sam Patel.");
        assertContains(output, "Could not undo the return of \"The Hobbit\"");
        assertContains(output, "[Notice: Hold ready] \"The Hobbit\" is waiting for you until");
        assertContains(output, "READY: copy C0001 held until");
    }

    @Test
    void readingListCommands() throws IOException {
        String output = run(
                "login M001",
                "list create \"Summer reads\"",
                "list add \"summer reads\" 9781000000002",
                "list add \"Summer reads\" 9781000000005",
                "list move \"Summer reads\" 9781000000005 1",
                "list show \"Summer reads\"",
                "undo",
                "lists",
                "exit");
        assertContains(output, "Created the reading list \"Summer reads\".");
        assertContains(output, "Summer reads:\n  1. Piranesi (9781000000005)\n  2. A Wizard of Earthsea");
        assertContains(output, "Undone: move of \"Piranesi\" in Summer reads.");
        assertContains(output, "Summer reads:\n  1. A Wizard of Earthsea (9781000000002)\n  2. Piranesi");
    }

    @Test
    void registerAsksForDetailsAndLogsIn() throws IOException {
        String output = run("register", "Jo Lee", "jo@example.com", "student", "Fantasy, sci-fi", "", "", "exit");
        assertContains(output, "Welcome! Your member ID is M003.");
        assertContains(output, "\"sci-fi\" is not a genre in the catalogue.");
        assertContains(output, "M003> ");
    }

    @Test
    void librarianAddsBookAndMembersCannot() throws IOException {
        String member = run("login M001", "addbook", "exit");
        assertContains(member, "Only librarians can add books.");

        String librarian = run("login LIB001", "addbook",
                "9780000000099", "Test Title", "Test Author", "123", "A test book.", "2", "Mystery, Thriller",
                "search test title", "exit");
        assertContains(librarian, "Added \"Test Title\" with 2 copies, filed under Mystery, Thriller.");
        assertContains(librarian, "9780000000099 | Test Title by Test Author, 123 pages | 2 of 2 available");
    }

    @Test
    void usageErrorsAndOverdueList() throws IOException {
        String output = run("reserve", "login LIB001", "overdue", "checkout M002", "exit");
        assertContains(output, "Please log in first.");
        assertContains(output, "1 overdue loan(s):");
        assertContains(output, "Alex Morgan (M001)");
        assertContains(output, "Usage: checkout <memberId> <copyId>");
    }

    @Test
    void plainLanguageWithoutKeyExplainsAssistantIsOff() throws IOException {
        String output = run("find me a short fantasy book", "exit");
        assertContains(output, "The AI assistant is turned off");
    }
}
