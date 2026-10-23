package libraryagent.domain.genre;

import java.util.ArrayList;
import java.util.List;
import libraryagent.domain.Book;

/** Composite leaf: a subgenre such as "Fantasy" that books are filed under directly. */
public class Subgenre extends GenreComponent {

    private final List<Book> books = new ArrayList<>();

    public Subgenre(String name) {
        super(name);
    }

    /** Files a book under this subgenre and records the subgenre on the book. */
    public void addBook(Book book) {
        if (!books.contains(book)) {
            books.add(book);
        }
        book.addGenre(getName());
    }

    @Override
    public List<Book> getBooks() {
        return List.copyOf(books);
    }

    @Override
    public List<GenreComponent> getChildren() {
        return List.of();
    }

    @Override
    public List<String> subgenreNames() {
        return List.of(getName());
    }
}
