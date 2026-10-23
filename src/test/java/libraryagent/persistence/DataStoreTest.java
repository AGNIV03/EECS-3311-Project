package libraryagent.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import libraryagent.domain.Book;
import libraryagent.domain.BookFeedback;
import libraryagent.domain.Loan;
import libraryagent.domain.Member;
import libraryagent.domain.Rating;
import libraryagent.domain.ReadingList;
import libraryagent.domain.Reservation;
import libraryagent.domain.copy.BookCopy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataStoreTest {

    private static final ZoneId ZONE = ZoneId.of("America/Toronto");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T14:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.now(CLOCK);

    @TempDir
    Path tempDir;

    @Test
    void seedLoadsCatalogueMembersAndLoans() {
        try (DataStore store = DataStore.inMemory(CLOCK, true)) {
            assertEquals(40, store.books().findAll().size());
            assertEquals(3, store.members().findAll().size());
            assertEquals(5, store.loans().findAll().size());
            assertTrue(store.members().findById("LIB001").orElseThrow().isLibrarian());
            assertEquals("M001", store.members().findByEmail("ALEX.MORGAN@example.com").orElseThrow().getMemberId());
        }
    }

    @Test
    void seededGenreTreeFindsBooksThroughCategories() {
        try (DataStore store = DataStore.inMemory(CLOCK, true)) {
            Book hobbit = store.books().findById("9781000000001").orElseThrow();
            assertTrue(store.genres().find("Fiction").orElseThrow().contains(hobbit));
            assertFalse(store.genres().find("Nonfiction").orElseThrow().contains(hobbit));
            assertEquals(List.of("Fantasy"), hobbit.getGenres());
        }
    }

    @Test
    void seededOverdueLoanHasOverdueCopy() {
        try (DataStore store = DataStore.inMemory(CLOCK, true)) {
            Member alex = store.members().findById("M001").orElseThrow();
            Loan dune = store.loans().findByMember(alex).stream()
                    .filter(Loan::isOpen).findFirst().orElseThrow();
            assertTrue(dune.isOverdue(TODAY));
            assertEquals("Overdue", dune.getCopy().getState().name());
        }
    }

    @Test
    void unseededStoreIsEmpty() {
        try (DataStore store = DataStore.inMemory(CLOCK, false)) {
            assertTrue(store.books().findAll().isEmpty());
            assertTrue(store.genres().isEmpty());
        }
    }

    @Test
    void changesSurviveReopeningTheFile() {
        Path file = tempDir.resolve("library.db");
        String loanId;
        try (DataStore store = DataStore.open(file, CLOCK)) {
            Member sam = store.members().findById("M002").orElseThrow();
            Member alex = store.members().findById("M001").orElseThrow();
            Book persuasion = store.books().findById("9781000000018").orElseThrow();
            BookCopy copy = persuasion.availableCopies().get(0);
            copy.checkOut(sam.getMemberId());
            store.books().saveCopy(copy);
            Loan loan = new Loan(store.loans().nextId(), copy, sam, TODAY);
            store.loans().save(loan);
            loanId = loan.getLoanId();

            Reservation waiting = new Reservation(store.reservations().nextId(), alex, persuasion,
                    LocalDateTime.now(CLOCK));
            persuasion.getHoldQueue().enqueue(waiting);
            store.reservations().save(waiting);

            ReadingList list = new ReadingList(store.readingLists().nextId(), alex, "Classics");
            list.add(persuasion);
            list.add(store.books().findById("9781000000017").orElseThrow());
            list.move(persuasion, 1);
            store.readingLists().save(list);

            Book dune = store.books().findById("9781000000006").orElseThrow();
            store.feedback().save(new BookFeedback(alex, dune, Rating.LIKED, "great", TODAY));
            store.feedback().save(new BookFeedback(alex, dune, Rating.DISLIKED, "changed my mind", TODAY));
        }

        try (DataStore store = DataStore.open(file, CLOCK)) {
            assertEquals(40, store.books().findAll().size(), "reopening must not re-seed");
            Book persuasion = store.books().findById("9781000000018").orElseThrow();
            assertEquals("On Loan", persuasion.getCopies().get(0).getState().name());
            Loan loan = store.loans().findById(loanId).orElseThrow();
            assertEquals(TODAY.plusDays(21), loan.getDueDate());

            Member alex = store.members().findById("M001").orElseThrow();
            assertEquals(1, persuasion.getHoldQueue().positionOf(alex));

            ReadingList list = store.readingLists().findByName(alex, "classics").orElseThrow();
            assertEquals(List.of("Pride and Prejudice", "Persuasion"),
                    list.getBooks().stream().map(Book::getTitle).toList());

            Book dune = store.books().findById("9781000000006").orElseThrow();
            assertEquals(Rating.DISLIKED, store.feedback().find(alex, dune).orElseThrow().getRating());
            assertEquals(1, store.feedback().findByMember(alex).size());
        }
    }

    @Test
    void idsIncrementPerPrefix() {
        try (DataStore store = DataStore.inMemory(CLOCK, false)) {
            assertEquals("L0001", store.loans().nextId());
            assertEquals("L0002", store.loans().nextId());
            assertEquals("R0001", store.reservations().nextId());
        }
    }
}
