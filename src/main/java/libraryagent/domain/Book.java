package libraryagent.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A catalogue title. Physical copies are tracked separately as {@code BookCopy}. */
public class Book {

    private final String isbn;
    private final String title;
    private final String author;
    private final int pageCount;
    private final String description;
    /** Names of the subgenres this book is filed under. */
    private final List<String> genres = new ArrayList<>();

    public Book(String isbn, String title, String author, int pageCount, String description) {
        this.isbn = Objects.requireNonNull(isbn);
        this.title = Objects.requireNonNull(title);
        this.author = Objects.requireNonNull(author);
        this.pageCount = pageCount;
        this.description = description == null ? "" : description;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public int getPageCount() {
        return pageCount;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getGenres() {
        return List.copyOf(genres);
    }

    /** Records that the book is filed under a subgenre; called by the genre hierarchy. */
    public void addGenre(String subgenre) {
        if (genres.stream().noneMatch(g -> g.equalsIgnoreCase(subgenre))) {
            genres.add(subgenre);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Book other && isbn.equals(other.isbn);
    }

    @Override
    public int hashCode() {
        return isbn.hashCode();
    }

    @Override
    public String toString() {
        return title + " by " + author;
    }
}
