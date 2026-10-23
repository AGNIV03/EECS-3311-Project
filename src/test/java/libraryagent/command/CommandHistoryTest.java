package libraryagent.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import libraryagent.domain.Book;
import libraryagent.domain.Loan;
import libraryagent.domain.Member;
import libraryagent.domain.ReadingList;
import libraryagent.domain.ReservationStatus;
import libraryagent.domain.copy.BookCopy;
import libraryagent.notification.NotificationService;
import libraryagent.persistence.DataStore;
import libraryagent.service.CirculationService;
import libraryagent.service.ReadingListService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommandHistoryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T14:00:00Z"), ZoneId.of("America/Toronto"));

    private DataStore store;
    private CirculationService circulation;
    private ReadingListService readingLists;
    private CommandHistory history;
    private Member alex;
    private Member sam;

    @BeforeEach
    void setUp() {
        store = DataStore.inMemory(CLOCK, true);
        circulation = new CirculationService(store.books(), store.loans(), store.reservations(),
                new NotificationService(), CLOCK);
        readingLists = new ReadingListService(store.readingLists());
        history = new CommandHistory();
        alex = store.members().findById("M001").orElseThrow();
        sam = store.members().findById("M002").orElseThrow();
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private Book book(String isbn) {
        return store.books().findById(isbn).orElseThrow();
    }

    @Test
    void emptyHistoryHasNothingToUndo() {
        assertFalse(history.canUndo());
        assertEquals("Nothing to undo.", history.undoLast());
    }

    @Test
    void undoReservationCancelsIt() {
        ReserveBookCommand reserve = new ReserveBookCommand(circulation, alex, book("9781000000018"));
        CommandResult result = history.run(reserve);
        assertTrue(result.success());
        assertTrue(result.message().startsWith("Reserved \"Persuasion\""));

        assertEquals("Undone: reservation of \"Persuasion\".", history.undoLast());
        assertEquals(ReservationStatus.CANCELLED, reserve.getReservation().getStatus());
        assertTrue(reserve.getReservation().getCopy().isAvailable());
    }

    @Test
    void queuedReservationReportsPosition() {
        CommandResult result = history.run(new ReserveBookCommand(circulation, alex, book("9781000000001")));
        assertEquals("No copies of \"The Hobbit\" are free right now. You are number 1 in the hold queue.",
                result.message());
    }

    @Test
    void failedCommandsAreReportedAndNotRecorded() {
        CommandResult result = history.run(new ReserveBookCommand(circulation, sam, book("9781000000001")));
        assertFalse(result.success());
        assertEquals("You already have \"The Hobbit\" on loan.", result.message());
        assertFalse(history.canUndo());
    }

    @Test
    void undoIsLastInFirstOut() {
        history.run(new ReserveBookCommand(circulation, alex, book("9781000000018")));
        BookCopy copy = book("9781000000017").availableCopies().get(0);
        history.run(new CheckOutCommand(circulation, sam, copy));
        assertEquals("checkout of \"Pride and Prejudice\" to Sam Patel", history.peekDescription());

        assertEquals("Undone: checkout of \"Pride and Prejudice\" to Sam Patel.", history.undoLast());
        assertTrue(copy.isAvailable());
        assertEquals("Undone: reservation of \"Persuasion\".", history.undoLast());
        assertFalse(history.canUndo());
    }

    @Test
    void returnWithPromotedHoldCannotBeUndone() {
        history.run(new ReserveBookCommand(circulation, alex, book("9781000000001")));
        BookCopy hobbitCopy = book("9781000000001").getCopies().get(0);
        CommandResult result = history.run(new ReturnBookCommand(circulation, hobbitCopy.getCopyId()));
        assertTrue(result.message().endsWith("The copy is now held for the next member in the queue."));

        String undo = history.undoLast();
        assertTrue(undo.startsWith("Could not undo the return of \"The Hobbit\""), undo);
        assertEquals("Reserved", hobbitCopy.getState().name());
    }

    @Test
    void returnOfOverdueBookShowsFeeAndUndoRestoresOverdueLoan() {
        Loan dune = circulation.loansFor(alex).get(0);
        CommandResult result = history.run(new ReturnBookCommand(circulation, dune.getCopy().getCopyId()));
        assertTrue(result.message().contains("Late fee: $2.25."), result.message());
        history.undoLast();
        assertTrue(dune.isOpen());
        assertEquals("Overdue", dune.getCopy().getState().name());
    }

    @Test
    void renewalUndoRestoresDueDate() {
        Loan hobbit = circulation.loansFor(sam).get(0);
        var due = hobbit.getDueDate();
        CommandResult result = history.run(new RenewLoanCommand(circulation, hobbit));
        assertTrue(result.message().contains("renewal 1 of 2"));
        history.undoLast();
        assertEquals(due, hobbit.getDueDate());
        assertEquals(0, hobbit.getRenewals());
    }

    @Test
    void readingListChangesAreUndoable() {
        ReadingList list = readingLists.createList(alex, "Summer");
        Book a = book("9781000000002");
        Book b = book("9781000000003");
        Book c = book("9781000000004");
        history.run(new AddToReadingListCommand(readingLists, list, a));
        history.run(new AddToReadingListCommand(readingLists, list, b));
        history.run(new AddToReadingListCommand(readingLists, list, c));

        CommandResult moved = history.run(new MoveInReadingListCommand(readingLists, list, c, 0));
        assertEquals("Moved \"Mistborn: The Final Empire\" to position 1 in Summer.", moved.message());
        history.run(new RemoveFromReadingListCommand(readingLists, list, a));
        assertEquals(List.of(c, b), list.getBooks());

        history.undoLast();
        assertEquals(List.of(c, a, b), list.getBooks());
        history.undoLast();
        assertEquals(List.of(a, b, c), list.getBooks());
        history.undoLast();
        assertEquals(List.of(a, b), list.getBooks());

        ReadingList reloaded = store.readingLists().findByName(alex, "summer").orElseThrow();
        assertEquals(List.of(a, b), reloaded.getBooks());
    }

    @Test
    void duplicateListEntryFailsWithoutRecording() {
        ReadingList list = readingLists.createList(alex, "Summer");
        Book a = book("9781000000002");
        history.run(new AddToReadingListCommand(readingLists, list, a));
        CommandResult dup = history.run(new AddToReadingListCommand(readingLists, list, a));
        assertFalse(dup.success());
        assertEquals("\"A Wizard of Earthsea\" is already on Summer.", dup.message());
        assertEquals("addition of \"A Wizard of Earthsea\" to Summer", history.peekDescription());
    }
}
