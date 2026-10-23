package libraryagent.domain.copy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import libraryagent.domain.Book;
import libraryagent.domain.LibraryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CopyStateTest {

    private Book book;
    private BookCopy copy;

    @BeforeEach
    void setUp() {
        book = new Book("978-1", "The Hobbit", "J.R.R. Tolkien", 310, "");
        copy = new BookCopy("C1", book);
        book.addCopy(copy);
    }

    @Test
    void newCopyIsAvailable() {
        assertEquals("Available", copy.getState().name());
        assertEquals(List.of(copy), book.availableCopies());
    }

    @Test
    void availableCopyCanBeCheckedOutAndReturned() {
        copy.checkOut("M1");
        assertEquals("On Loan", copy.getState().name());
        assertTrue(book.availableCopies().isEmpty());
        copy.checkIn();
        assertEquals("Available", copy.getState().name());
    }

    @Test
    void reservedCopyOnlyGoesToTheReservingMember() {
        copy.reserve("M1");
        assertEquals("Reserved", copy.getState().name());
        LibraryException e = assertThrows(LibraryException.class, () -> copy.checkOut("M2"));
        assertEquals("Copy C1 is being held for another member.", e.getMessage());
        copy.checkOut("M1");
        assertEquals("On Loan", copy.getState().name());
        assertNull(copy.getReservedFor());
    }

    @Test
    void releasingAHeldCopyMakesItAvailable() {
        copy.reserve("M1");
        copy.release();
        assertTrue(copy.isAvailable());
        assertNull(copy.getReservedFor());
    }

    @Test
    void loanedCopyCannotBeReservedOrLentAgain() {
        copy.checkOut("M1");
        assertThrows(LibraryException.class, () -> copy.reserve("M2"));
        assertThrows(LibraryException.class, () -> copy.checkOut("M2"));
    }

    @Test
    void overdueOnlyAppliesToLoanedCopies() {
        assertThrows(LibraryException.class, copy::markOverdue);
        copy.checkOut("M1");
        copy.markOverdue();
        assertEquals("Overdue", copy.getState().name());
        copy.markOverdue();
        assertEquals("Overdue", copy.getState().name());
        assertThrows(LibraryException.class, () -> copy.checkOut("M2"));
        copy.checkIn();
        assertTrue(copy.isAvailable());
    }

    @Test
    void availableCopyCannotBeCheckedInOrReleased() {
        assertThrows(LibraryException.class, copy::checkIn);
        assertThrows(LibraryException.class, copy::release);
    }

    @Test
    void restoreRebuildsSavedState() {
        BookCopy restored = BookCopy.restore("C9", book, "Reserved", "M5");
        assertEquals("Reserved", restored.getState().name());
        assertEquals("M5", restored.getReservedFor());
        assertThrows(IllegalArgumentException.class, () -> BookCopy.restore("C9", book, "Lost", null));
    }
}
