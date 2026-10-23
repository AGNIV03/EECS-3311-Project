package libraryagent.persistence;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import libraryagent.domain.Book;
import libraryagent.domain.copy.BookCopy;

/** Books with their copies and genre filing. */
public class BookRepository implements Repository<Book> {

    private final Database db;
    private final GenreRepository genres;
    private final Map<String, Book> books = new LinkedHashMap<>();
    private final Map<String, BookCopy> copies = new LinkedHashMap<>();

    public BookRepository(Database db, GenreRepository genres) {
        this.db = db;
        this.genres = genres;
        load();
    }

    private void load() {
        db.query("SELECT isbn, title, author, page_count, description FROM books",
                rs -> new Book(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5)))
                .forEach(b -> books.put(b.getIsbn(), b));
        db.query("SELECT copy_id, isbn, state, reserved_for FROM copies ORDER BY copy_id", rs -> {
            Book book = books.get(rs.getString(2));
            BookCopy copy = BookCopy.restore(rs.getString(1), book, rs.getString(3), rs.getString(4));
            book.addCopy(copy);
            return copy;
        }).forEach(c -> copies.put(c.getCopyId(), c));
        db.query("SELECT isbn, genre_name FROM book_genres", rs -> {
            Book book = books.get(rs.getString(1));
            genres.findSubgenre(rs.getString(2)).ifPresent(s -> s.addBook(book));
            return book;
        });
    }

    @Override
    public void save(Book book) {
        db.inTransaction(() -> {
            db.update("INSERT INTO books(isbn, title, author, page_count, description) VALUES (?, ?, ?, ?, ?) "
                            + "ON CONFLICT(isbn) DO UPDATE SET title = excluded.title, author = excluded.author, "
                            + "page_count = excluded.page_count, description = excluded.description",
                    book.getIsbn(), book.getTitle(), book.getAuthor(), book.getPageCount(), book.getDescription());
            for (BookCopy copy : book.getCopies()) {
                saveCopy(copy);
            }
            for (String genre : book.getGenres()) {
                db.update("INSERT OR IGNORE INTO book_genres(isbn, genre_name) VALUES (?, ?)", book.getIsbn(), genre);
            }
        });
        books.put(book.getIsbn(), book);
    }

    /** Persists a copy's current state. */
    public void saveCopy(BookCopy copy) {
        db.update("INSERT INTO copies(copy_id, isbn, state, reserved_for) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT(copy_id) DO UPDATE SET state = excluded.state, "
                        + "reserved_for = excluded.reserved_for",
                copy.getCopyId(), copy.getBook().getIsbn(), copy.getState().name(), copy.getReservedFor());
        copies.put(copy.getCopyId(), copy);
    }

    @Override
    public Optional<Book> findById(String isbn) {
        return Optional.ofNullable(books.get(isbn.strip()));
    }

    /** All books, sorted by title. */
    @Override
    public List<Book> findAll() {
        return books.values().stream().sorted(Comparator.comparing(Book::getTitle)).toList();
    }

    public Optional<BookCopy> findCopy(String copyId) {
        return Optional.ofNullable(copies.get(copyId.strip().toUpperCase()));
    }

    public String nextCopyId() {
        return db.nextId("C", 4);
    }
}
