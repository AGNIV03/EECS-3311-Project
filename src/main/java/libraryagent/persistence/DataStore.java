package libraryagent.persistence;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Opens the database, creates the schema, seeds sample data into an empty database and
 * builds the repositories in dependency order.
 */
public final class DataStore implements AutoCloseable {

    private final Database db;
    private final GenreRepository genres;
    private final BookRepository books;
    private final MemberRepository members;
    private final LoanRepository loans;
    private final ReservationRepository reservations;
    private final ReadingListRepository readingLists;
    private final FeedbackRepository feedback;

    private DataStore(Database db, Clock clock, boolean seed) {
        this.db = db;
        db.createSchema();
        SeedLoader.SeedFile seedFile = null;
        genres = new GenreRepository(db);
        if (seed && genres.isEmpty()) {
            seedFile = SeedLoader.read();
            SeedLoader.seedGenres(seedFile, genres);
        }
        books = new BookRepository(db, genres);
        members = new MemberRepository(db);
        loans = new LoanRepository(db, books, members);
        reservations = new ReservationRepository(db, books, members);
        readingLists = new ReadingListRepository(db, books, members);
        feedback = new FeedbackRepository(db, books, members);
        if (seedFile != null) {
            SeedLoader.SeedFile data = seedFile;
            db.inTransaction(() -> SeedLoader.seedData(data, this, clock));
        }
    }

    /** Opens (and if new, seeds) the database file. */
    public static DataStore open(Path file, Clock clock) {
        return new DataStore(Database.open(file), clock, true);
    }

    /** An in-memory database, optionally seeded; used by tests. */
    public static DataStore inMemory(Clock clock, boolean seed) {
        return new DataStore(Database.inMemory(), clock, seed);
    }

    public Database database() {
        return db;
    }

    public GenreRepository genres() {
        return genres;
    }

    public BookRepository books() {
        return books;
    }

    public MemberRepository members() {
        return members;
    }

    public LoanRepository loans() {
        return loans;
    }

    public ReservationRepository reservations() {
        return reservations;
    }

    public ReadingListRepository readingLists() {
        return readingLists;
    }

    public FeedbackRepository feedback() {
        return feedback;
    }

    @Override
    public void close() {
        db.close();
    }
}
