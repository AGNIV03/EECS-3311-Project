package libraryagent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadingListTest {

    private final Member owner = new Member("M001", "Ana", "ana@example.com",
            MemberType.STANDARD, false, new ReadingPreferences());
    private final Book a = new Book("1", "A", "X", 100, "");
    private final Book b = new Book("2", "B", "X", 100, "");
    private final Book c = new Book("3", "C", "X", 100, "");
    private ReadingList list;

    @BeforeEach
    void setUp() {
        list = new ReadingList("RL1", owner, "Summer reads");
        list.add(a);
        list.add(b);
        list.add(c);
    }

    @Test
    void addingDuplicateIsRejected() {
        LibraryException e = assertThrows(LibraryException.class, () -> list.add(a));
        assertEquals("\"A\" is already on Summer reads.", e.getMessage());
    }

    @Test
    void removeReturnsOriginalPosition() {
        assertEquals(1, list.remove(b));
        assertEquals(List.of(a, c), list.getBooks());
    }

    @Test
    void removingMissingBookIsRejected() {
        list.remove(b);
        assertThrows(LibraryException.class, () -> list.remove(b));
    }

    @Test
    void moveReordersAndReportsPreviousPosition() {
        assertEquals(2, list.move(c, 0));
        assertEquals(List.of(c, a, b), list.getBooks());
    }

    @Test
    void movePositionIsClampedToBounds() {
        list.move(a, 99);
        assertEquals(List.of(b, c, a), list.getBooks());
        list.move(a, -5);
        assertEquals(List.of(a, b, c), list.getBooks());
    }

    @Test
    void insertRestoresBookAtPosition() {
        int from = list.remove(b);
        list.insert(b, from);
        assertEquals(List.of(a, b, c), list.getBooks());
    }
}
