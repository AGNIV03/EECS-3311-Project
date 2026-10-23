package libraryagent.domain.genre;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import libraryagent.domain.Book;

/** Composite node: a category such as "Fiction" that groups subgenres or other categories. */
public class GenreCategory extends GenreComponent {

    private final List<GenreComponent> children = new ArrayList<>();

    public GenreCategory(String name) {
        super(name);
    }

    public void add(GenreComponent child) {
        children.add(child);
    }

    @Override
    public List<Book> getBooks() {
        Set<Book> books = new LinkedHashSet<>();
        for (GenreComponent child : children) {
            books.addAll(child.getBooks());
        }
        return List.copyOf(books);
    }

    @Override
    public List<GenreComponent> getChildren() {
        return List.copyOf(children);
    }

    @Override
    public List<String> subgenreNames() {
        List<String> names = new ArrayList<>();
        for (GenreComponent child : children) {
            names.addAll(child.subgenreNames());
        }
        return names;
    }
}
