package libraryagent.domain.genre;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import libraryagent.domain.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenreCompositeTest {

    private final Book hobbit = new Book("1", "The Hobbit", "Tolkien", 310, "");
    private final Book dune = new Book("2", "Dune", "Herbert", 600, "");
    private final Book history = new Book("3", "SPQR", "Beard", 500, "");
    private GenreCategory root;

    @BeforeEach
    void setUp() {
        Subgenre fantasy = new Subgenre("Fantasy");
        Subgenre sciFi = new Subgenre("Science Fiction");
        Subgenre ancient = new Subgenre("History");
        GenreCategory fiction = new GenreCategory("Fiction");
        fiction.add(fantasy);
        fiction.add(sciFi);
        GenreCategory nonfiction = new GenreCategory("Nonfiction");
        nonfiction.add(ancient);
        root = new GenreCategory("All");
        root.add(fiction);
        root.add(nonfiction);
        fantasy.addBook(hobbit);
        sciFi.addBook(dune);
        ancient.addBook(history);
    }

    @Test
    void categoryReturnsBooksFromAllSubgenres() {
        GenreComponent fiction = root.find("fiction").orElseThrow();
        assertEquals(List.of(hobbit, dune), fiction.getBooks());
        assertTrue(fiction.contains(dune));
        assertFalse(fiction.contains(history));
    }

    @Test
    void subgenreIsFoundCaseInsensitivelyAnywhereInTree() {
        GenreComponent sciFi = root.find("  SCIENCE fiction ").orElseThrow();
        assertEquals(List.of(dune), sciFi.getBooks());
    }

    @Test
    void unknownGenreIsNotFound() {
        assertTrue(root.find("Poetry").isEmpty());
    }

    @Test
    void filingABookRecordsTheGenreOnTheBook() {
        assertEquals(List.of("Fantasy"), hobbit.getGenres());
    }

    @Test
    void subgenreNamesListsLeavesOnly() {
        assertEquals(List.of("Fantasy", "Science Fiction", "History"), root.subgenreNames());
    }
}
