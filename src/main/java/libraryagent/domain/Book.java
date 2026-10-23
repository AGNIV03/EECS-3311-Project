package libraryagent.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import libraryagent.domain.copy.BookCopy;

/** A catalogue title with its physical copies and hold queue. */
public class Book {

    private final String isbn;
    private final String title;
    private final String author;
    private final int pageCount;
    private final String description;
    /** Names of the subgenres this book is filed under. */
    private final List<String> genres = new ArrayList<>();
    private final List<BookCopy> copies = new ArrayList<>();
    private final HoldQueue holdQueue = new HoldQueue();

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

    public List<BookCopy> getCopies() {
        return List.copyOf(copies);
    }

    public void addCopy(BookCopy copy) {
        if (!copy.getBook().equals(this)) {
            throw new IllegalArgumentException("Copy " + copy.getCopyId() + " belongs to another book");
        }
        if (!copies.contains(copy)) {
            copies.add(copy);
        }
    }

    /** Copies currently on the shelf and free to reserve or lend. */
    public List<BookCopy> availableCopies() {
        return copies.stream().filter(BookCopy::isAvailable).toList();
    }

    public HoldQueue getHoldQueue() {
        return holdQueue;
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
