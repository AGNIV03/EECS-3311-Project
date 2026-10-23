package libraryagent.domain.genre;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import libraryagent.domain.Book;

/**
 * Composite pattern: a node in the genre hierarchy. Categories and subgenres are treated
 * uniformly, so searching "Fiction" returns books from every subgenre beneath it.
 */
public abstract class GenreComponent {

    private final String name;

    protected GenreComponent(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public String getName() {
        return name;
    }

    /** All books filed under this node, including every descendant subgenre. */
    public abstract List<Book> getBooks();

    /** Direct children; empty for a subgenre. */
    public abstract List<GenreComponent> getChildren();

    public boolean contains(Book book) {
        return getBooks().contains(book);
    }

    /** Finds a node by name (case-insensitive) in this subtree. */
    public Optional<GenreComponent> find(String genreName) {
        if (name.equalsIgnoreCase(genreName.strip())) {
            return Optional.of(this);
        }
        for (GenreComponent child : getChildren()) {
            Optional<GenreComponent> match = child.find(genreName);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    /** Names of every subgenre (leaf) in this subtree. */
    public abstract List<String> subgenreNames();

    protected static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return name;
    }
}
