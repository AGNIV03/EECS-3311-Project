package libraryagent.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import libraryagent.domain.genre.GenreCategory;
import libraryagent.domain.genre.GenreComponent;
import libraryagent.domain.genre.Subgenre;

/** Loads the genre hierarchy. A genre with children is a category; one without is a subgenre. */
public class GenreRepository {

    public static final String ROOT_NAME = "All Genres";

    private final Database db;
    private GenreCategory root = new GenreCategory(ROOT_NAME);

    public GenreRepository(Database db) {
        this.db = db;
        reload();
    }

    public GenreCategory root() {
        return root;
    }

    public Optional<GenreComponent> find(String name) {
        return root.find(name);
    }

    public Optional<Subgenre> findSubgenre(String name) {
        return root.find(name).filter(Subgenre.class::isInstance).map(Subgenre.class::cast);
    }

    /** Adds a genre under a parent (null for top level). Call {@link #reload()} afterwards. */
    public void insert(String name, String parentName, int position) {
        db.update("INSERT INTO genres(name, parent_name, position) VALUES (?, ?, ?)", name, parentName, position);
    }

    public boolean isEmpty() {
        return db.count("SELECT COUNT(*) FROM genres") == 0;
    }

    /** Rebuilds the tree from the database. Books must be filed again by the book repository. */
    public void reload() {
        record Row(String name, String parent) {
        }
        List<Row> rows = db.query("SELECT name, parent_name FROM genres ORDER BY position",
                rs -> new Row(rs.getString(1), rs.getString(2)));
        Map<String, List<String>> children = new LinkedHashMap<>();
        for (Row row : rows) {
            children.computeIfAbsent(row.parent() == null ? ROOT_NAME : row.parent(), k -> new ArrayList<>())
                    .add(row.name());
        }
        GenreCategory newRoot = new GenreCategory(ROOT_NAME);
        for (String top : children.getOrDefault(ROOT_NAME, List.of())) {
            newRoot.add(build(top, children));
        }
        root = newRoot;
    }

    private GenreComponent build(String name, Map<String, List<String>> children) {
        List<String> kids = children.get(name);
        if (kids == null || kids.isEmpty()) {
            return new Subgenre(name);
        }
        GenreCategory category = new GenreCategory(name);
        for (String kid : kids) {
            category.add(build(kid, children));
        }
        return category;
    }
}
