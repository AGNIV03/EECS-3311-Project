package libraryagent.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatabaseTest {

    private Database db;

    @BeforeEach
    void setUp() {
        db = Database.inMemory();
        db.update("CREATE TABLE notes (text TEXT)");
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private long notes() {
        return db.count("SELECT COUNT(*) FROM notes");
    }

    @Test
    void writeTogetherReturnsTheResultAndSavesEverything() {
        int result = db.writeTogether(() -> {
            db.update("INSERT INTO notes VALUES ('a')");
            db.writeTogether(() -> db.update("INSERT INTO notes VALUES ('b')"));
            return 7;
        });
        assertEquals(7, result);
        assertEquals(2, notes());
    }

    @Test
    void writeTogetherKeepsRowsWrittenBeforeAnException() {
        assertThrows(IllegalStateException.class, () -> db.writeTogether(() -> {
            db.update("INSERT INTO notes VALUES ('a')");
            throw new IllegalStateException("rule violated after the first write");
        }));
        assertEquals(1, notes());
        // The connection is usable and auto-committing again.
        db.update("INSERT INTO notes VALUES ('b')");
        assertEquals(2, notes());
    }

    @Test
    void inTransactionStillRollsBack() {
        assertThrows(IllegalStateException.class, () -> db.inTransaction(() -> {
            db.update("INSERT INTO notes VALUES ('a')");
            throw new IllegalStateException("failed");
        }));
        assertEquals(0, notes());
    }
}
