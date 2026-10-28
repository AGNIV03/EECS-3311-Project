package libraryagent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import libraryagent.command.CommandResult;
import libraryagent.domain.Book;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Member;
import libraryagent.domain.MemberType;
import libraryagent.notification.EventType;
import libraryagent.notification.LibraryEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T14:00:00Z"), ZoneId.of("America/Toronto"));
    private static final String HOBBIT = "9781000000001";
    private static final String PERSUASION = "9781000000018";

    private LibraryServices services;
    private AgentController controller;

    @BeforeEach
    void setUp() {
        services = LibraryServices.inMemory(CLOCK);
        controller = new AgentController(services, new SessionContext());
    }

    @AfterEach
    void tearDown() {
        services.close();
    }

    @Test
    void actionsRequireLogin() {
        CommandResult result = controller.reserve("M001", HOBBIT);
        assertFalse(result.success());
        assertEquals("Please log in first.", result.message());
    }

    @Test
    void membersCannotActForOtherMembers() {
        controller.login("M001");
        assertEquals("You can only do that for your own account.", controller.reserve("M002", PERSUASION).message());
        assertThrows(LibraryException.class, () -> controller.accountSummary("M002"));
    }

    @Test
    void deskOperationsAreLibrarianOnly() {
        controller.login("M001");
        assertEquals("Only librarians can check out books.", controller.checkOut("M001", "C0001").message());
        assertEquals("Only librarians can check in books.", controller.checkIn("C0001").message());
        assertThrows(LibraryException.class, controller::findOverdue);
    }

    @Test
    void librarianCanLendReturnAndSeeAnyAccount() {
        controller.login("LIB001");
        Book persuasion = controller.bookDetails(PERSUASION);
        String copyId = persuasion.getCopies().get(0).getCopyId();

        CommandResult out = controller.checkOut("M002", copyId);
        assertTrue(out.success(), out.message());
        assertEquals(2, controller.accountSummary("M002").loans().size());

        CommandResult back = controller.checkIn(copyId);
        assertTrue(back.success(), back.message());
        assertEquals(1, controller.findOverdue().size());
    }

    @Test
    void unknownIdsBecomeFailedResults() {
        controller.login("M001");
        assertEquals("No book with ISBN 999 is in the catalogue.", controller.reserve("M001", "999").message());
        assertEquals("No loan with ID L9999.", controller.renew("L9999").message());
    }

    @Test
    void accountSummaryShowsLoansHoldsAndFees() {
        controller.login("M001");
        controller.reserve("M001", HOBBIT);
        AccountSummary summary = controller.accountSummary("M001");
        assertEquals(1, summary.loans().size());
        assertEquals(1, summary.holds().size());
        assertEquals(1, summary.holds().get(0).queuePosition());
        assertEquals(new BigDecimal("2.25"), summary.feesOwed());
    }

    @Test
    void undoHistoryIsPerLoginSession() {
        controller.login("M001");
        controller.reserve("M001", PERSUASION);
        controller.login("M002");
        assertEquals("Nothing to undo.", controller.undoLast());
        controller.login("M001");
        assertEquals("Nothing to undo.", controller.undoLast());
    }

    @Test
    void readingListFlowWithOneBasedPositions() {
        controller.login("M001");
        assertTrue(controller.createList("Winter").success());
        controller.addToList("winter", "9781000000002");
        controller.addToList("winter", "9781000000003");
        CommandResult moved = controller.moveInList("Winter", "9781000000003", 1);
        assertEquals("Moved \"The Name of the Wind\" to position 1 in Winter.", moved.message());
        assertEquals("Undone: move of \"The Name of the Wind\" in Winter.", controller.undoLast());
        assertEquals(List.of("A Wizard of Earthsea", "The Name of the Wind"),
                controller.readingList("WINTER").getBooks().stream().map(Book::getTitle).toList());
    }

    @Test
    void registrationThenPreferences() {
        Member jo = controller.register("Jo", "jo@example.com", MemberType.STUDENT);
        controller.login(jo.getMemberId());
        controller.updatePreferences(jo.getMemberId(), List.of("fantasy"), List.of("Le Guin"), 300);
        assertEquals(List.of("Fantasy"), jo.getPreferences().getFavoriteGenres());
    }

    @Test
    void subscribersReceiveHoldReadyEvents() {
        List<LibraryEvent> events = new ArrayList<>();
        controller.subscribe(events::add);
        controller.login("M001");
        controller.reserve("M001", HOBBIT);
        controller.login("LIB001");
        String copyId = controller.bookDetails(HOBBIT).getCopies().get(0).getCopyId();
        controller.checkIn(copyId);
        assertEquals(1, events.size());
        assertEquals(EventType.HOLD_READY, events.get(0).type());
        assertEquals("M001", events.get(0).memberId());
    }
}
