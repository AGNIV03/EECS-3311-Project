package libraryagent.controller;

import java.nio.file.Path;
import java.time.Clock;
import libraryagent.notification.NotificationService;
import libraryagent.persistence.DataStore;
import libraryagent.service.CatalogService;
import libraryagent.service.CirculationService;
import libraryagent.service.MemberService;
import libraryagent.service.ReadingListService;

/** Builds the data store and every domain service once, and shares them with the controller. */
public final class LibraryServices implements AutoCloseable {

    private final DataStore store;
    private final Clock clock;
    private final NotificationService notifications = new NotificationService();
    private final CatalogService catalog;
    private final MemberService members;
    private final CirculationService circulation;
    private final ReadingListService readingLists;

    private LibraryServices(DataStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
        this.catalog = new CatalogService(store.books(), store.genres());
        this.members = new MemberService(store.members(), store.genres());
        this.circulation = new CirculationService(store.books(), store.loans(), store.reservations(),
                notifications, clock);
        this.readingLists = new ReadingListService(store.readingLists());
    }

    /** Opens (and seeds, if new) the database file. */
    public static LibraryServices open(Path dbFile, Clock clock) {
        return new LibraryServices(DataStore.open(dbFile, clock), clock);
    }

    /** A seeded in-memory library; used by tests. */
    public static LibraryServices inMemory(Clock clock) {
        return new LibraryServices(DataStore.inMemory(clock, true), clock);
    }

    public DataStore store() {
        return store;
    }

    public Clock clock() {
        return clock;
    }

    public NotificationService notifications() {
        return notifications;
    }

    public CatalogService catalog() {
        return catalog;
    }

    public MemberService members() {
        return members;
    }

    public CirculationService circulation() {
        return circulation;
    }

    public ReadingListService readingLists() {
        return readingLists;
    }

    @Override
    public void close() {
        store.close();
    }
}
