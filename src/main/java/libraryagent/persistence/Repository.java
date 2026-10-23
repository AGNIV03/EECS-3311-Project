package libraryagent.persistence;

import java.util.List;
import java.util.Optional;

/**
 * Stores one kind of domain object. Implementations keep every loaded object in memory
 * (one instance per id) and write each change through to SQLite.
 */
public interface Repository<T> {

    /** Inserts or updates the object. */
    void save(T item);

    Optional<T> findById(String id);

    List<T> findAll();
}
