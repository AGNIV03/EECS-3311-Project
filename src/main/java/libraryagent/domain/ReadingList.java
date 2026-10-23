package libraryagent.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A member's named, ordered list of books. Positions are zero-based. */
public class ReadingList {

    private final String listId;
    private final Member owner;
    private final String name;
    private final List<Book> books = new ArrayList<>();

    public ReadingList(String listId, Member owner, String name) {
        this.listId = Objects.requireNonNull(listId);
        this.owner = Objects.requireNonNull(owner);
        this.name = Objects.requireNonNull(name);
    }

    public String getListId() {
        return listId;
    }

    public Member getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public List<Book> getBooks() {
        return List.copyOf(books);
    }

    public boolean contains(Book book) {
        return books.contains(book);
    }

    public int indexOf(Book book) {
        return books.indexOf(book);
    }

    /** Appends a book. */
    public void add(Book book) {
        insert(book, books.size());
    }

    /** Inserts a book at a position (clamped to the list bounds). */
    public void insert(Book book, int position) {
        if (books.contains(book)) {
            throw new LibraryException("\"" + book.getTitle() + "\" is already on " + name + ".");
        }
        books.add(clamp(position, books.size()), book);
    }

    /** Removes a book and returns the position it had. */
    public int remove(Book book) {
        int index = books.indexOf(book);
        if (index < 0) {
            throw new LibraryException("\"" + book.getTitle() + "\" is not on " + name + ".");
        }
        books.remove(index);
        return index;
    }

    /** Moves a book to a new position (clamped) and returns its previous position. */
    public int move(Book book, int position) {
        int from = remove(book);
        books.add(clamp(position, books.size()), book);
        return from;
    }

    private static int clamp(int position, int max) {
        return Math.max(0, Math.min(position, max));
    }
}
