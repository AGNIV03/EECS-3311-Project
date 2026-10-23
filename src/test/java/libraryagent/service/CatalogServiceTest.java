package libraryagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import libraryagent.domain.Book;
import libraryagent.domain.LibraryException;
import libraryagent.persistence.DataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogServiceTest {

    private DataStore store;
    private CatalogService catalog;

    @BeforeEach
    void setUp() {
        store = ServiceTestSupport.seededStore(ServiceTestSupport.clock());
        catalog = new CatalogService(store.books(), store.genres());
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private static List<String> titles(List<Book> books) {
        return books.stream().map(Book::getTitle).toList();
    }

    @Test
    void categorySearchIncludesEverySubgenre() {
        List<Book> fiction = catalog.search(new BookQuery(null, null, "Fiction", null, null));
        assertTrue(titles(fiction).contains("Dune"));
        assertTrue(titles(fiction).contains("The Hobbit"));
        assertFalse(titles(fiction).contains("Atomic Habits"));
    }

    @Test
    void filtersCombineGenreAuthorAndLength() {
        List<Book> result = catalog.search(new BookQuery(null, "weir", "Science Fiction", 400, null));
        assertEquals(List.of("The Martian"), titles(result));
    }

    @Test
    void keywordSearchRanksBooksMatchingMoreKeywordsFirst() {
        List<Book> result = catalog.search(BookQuery.ofKeywords("hobbit tolkien dragon"));
        assertEquals("The Hobbit", result.get(0).getTitle());
    }

    @Test
    void keywordSearchNeedsAtLeastOneMatch() {
        assertTrue(catalog.search(BookQuery.ofKeywords("xyzzy")).isEmpty());
    }

    @Test
    void emptyQueryReturnsWholeCatalogue() {
        assertEquals(40, catalog.search(new BookQuery(null, null, null, null, null)).size());
    }

    @Test
    void unknownGenreIsReportedWithKnownSubgenres() {
        LibraryException e = assertThrows(LibraryException.class,
                () -> catalog.search(new BookQuery(null, null, "Poetry", null, null)));
        assertTrue(e.getMessage().startsWith("There is no genre called \"Poetry\""));
        assertTrue(e.getMessage().contains("Fantasy"));
    }

    @Test
    void availabilityReportsFreeCopiesAndQueue() {
        Book hobbit = catalog.requireBook("978-1000000001");
        Availability availability = catalog.availability(hobbit);
        assertEquals(0, availability.availableCopies());
        assertEquals(1, availability.totalCopies());
        assertEquals(0, availability.queueLength());
    }

    @Test
    void addBookCreatesCopiesAndFilesGenres() {
        Book added = catalog.addBook(new Book("978-0-00-000000-1", "New Title", "New Author", 250, "About it"),
                2, List.of("fantasy", "Mystery"));
        assertEquals("9780000000001", added.getIsbn());
        assertEquals(2, added.getCopies().size());
        assertEquals(List.of("Fantasy", "Mystery"), added.getGenres());
        assertTrue(store.genres().find("Fiction").orElseThrow().contains(added));
        assertEquals(added, catalog.findByIsbn("9780000000001").orElseThrow());
    }

    @Test
    void addBookRejectsDuplicateIsbn() {
        LibraryException e = assertThrows(LibraryException.class, () -> catalog.addBook(
                new Book("9781000000001", "Copy", "Someone", 100, ""), 1, List.of("Fantasy")));
        assertEquals("A book with ISBN 9781000000001 is already in the catalogue.", e.getMessage());
    }

    @Test
    void addBookValidatesFieldsBeforeSaving() {
        assertThrows(LibraryException.class, () -> catalog.addBook(
                new Book("123", "T", "A", 100, ""), 1, List.of("Fantasy")));
        assertThrows(LibraryException.class, () -> catalog.addBook(
                new Book("9780000000002", "T", "A", 0, ""), 1, List.of("Fantasy")));
        assertThrows(LibraryException.class, () -> catalog.addBook(
                new Book("9780000000002", "T", "A", 100, ""), 0, List.of("Fantasy")));
        assertThrows(LibraryException.class, () -> catalog.addBook(
                new Book("9780000000002", "T", "A", 100, ""), 1, List.of("Fiction")));
        assertTrue(catalog.findByIsbn("9780000000002").isEmpty());
    }

    @Test
    void assignGenreAddsAnotherSubgenre() {
        Book hobbit = catalog.requireBook("9781000000001");
        catalog.assignGenre(hobbit, "YA Fantasy");
        assertEquals(List.of("Fantasy", "YA Fantasy"), hobbit.getGenres());
        assertThrows(LibraryException.class, () -> catalog.assignGenre(hobbit, "Space Opera"));
    }
}
