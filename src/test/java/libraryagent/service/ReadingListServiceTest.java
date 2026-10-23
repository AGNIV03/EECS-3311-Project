package libraryagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import libraryagent.domain.LibraryException;
import libraryagent.domain.Member;
import libraryagent.persistence.DataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadingListServiceTest {

    private DataStore store;
    private ReadingListService lists;
    private Member alex;

    @BeforeEach
    void setUp() {
        store = ServiceTestSupport.seededStore(ServiceTestSupport.clock());
        lists = new ReadingListService(store.readingLists());
        alex = store.members().findById("M001").orElseThrow();
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void listNamesAreUniquePerMemberIgnoringCase() {
        lists.createList(alex, "Summer reads");
        LibraryException e = assertThrows(LibraryException.class, () -> lists.createList(alex, "summer READS "));
        assertEquals("You already have a list called \"summer READS\".", e.getMessage());
        Member sam = store.members().findById("M002").orElseThrow();
        lists.createList(sam, "Summer reads");
        assertEquals(1, lists.listsFor(sam).size());
    }

    @Test
    void blankOrLongNamesAreRejected() {
        assertThrows(LibraryException.class, () -> lists.createList(alex, "  "));
        assertThrows(LibraryException.class, () -> lists.createList(alex, "x".repeat(61)));
    }

    @Test
    void missingListIsReported() {
        LibraryException e = assertThrows(LibraryException.class, () -> lists.requireList(alex, "Winter"));
        assertEquals("You don't have a reading list called \"Winter\".", e.getMessage());
    }
}
