package libraryagent.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import libraryagent.domain.Book;
import libraryagent.domain.Loan;
import libraryagent.domain.Member;
import libraryagent.domain.MemberType;
import libraryagent.domain.ReadingPreferences;
import libraryagent.domain.copy.BookCopy;

/** Fills an empty database with the sample catalogue, members and loans in {@code seed/library.json}. */
final class SeedLoader {

    record GenreSeed(String name, List<GenreSeed> children) {
    }

    record BookSeed(String isbn, String title, String author, int pages, int copies,
                    List<String> genres, String description) {
    }

    record MemberSeed(String id, String name, String email, String type, boolean librarian,
                      List<String> favoriteGenres, List<String> favoriteAuthors, int maxPages) {
    }

    record LoanSeed(String memberEmail, String isbn, int checkedOutDaysAgo, Integer returnedDaysAgo) {
    }

    record SeedFile(List<GenreSeed> genres, List<BookSeed> books, List<MemberSeed> members, List<LoanSeed> loans) {
    }

    private SeedLoader() {
    }

    static SeedFile read() {
        try (InputStream in = SeedLoader.class.getResourceAsStream("/seed/library.json")) {
            if (in == null) {
                throw new IllegalStateException("seed/library.json is missing from resources");
            }
            return new ObjectMapper().readValue(in, SeedFile.class);
        } catch (IOException e) {
            throw new PersistenceException("Could not read seed data", e);
        }
    }

    static void seedGenres(SeedFile seed, GenreRepository genres) {
        int[] position = {0};
        for (GenreSeed top : seed.genres()) {
            insertGenre(top, null, genres, position);
        }
        genres.reload();
    }

    private static void insertGenre(GenreSeed node, String parent, GenreRepository genres, int[] position) {
        genres.insert(node.name(), parent, position[0]++);
        if (node.children() != null) {
            for (GenreSeed child : node.children()) {
                insertGenre(child, node.name(), genres, position);
            }
        }
    }

    static void seedData(SeedFile seed, DataStore store, Clock clock) {
        for (BookSeed b : seed.books()) {
            Book book = new Book(b.isbn(), b.title(), b.author(), b.pages(), b.description());
            for (String genre : b.genres()) {
                store.genres().findSubgenre(genre)
                        .orElseThrow(() -> new IllegalStateException("Unknown seed genre " + genre))
                        .addBook(book);
            }
            for (int i = 0; i < b.copies(); i++) {
                book.addCopy(new BookCopy(store.books().nextCopyId(), book));
            }
            store.books().save(book);
        }
        for (MemberSeed m : seed.members()) {
            String id = m.id() != null ? m.id() : store.members().nextId();
            ReadingPreferences prefs = new ReadingPreferences(m.favoriteGenres(), m.favoriteAuthors(),
                    m.maxPages(), true);
            store.members().save(new Member(id, m.name(), m.email(), MemberType.valueOf(m.type()),
                    m.librarian(), prefs));
        }
        LocalDate today = LocalDate.now(clock);
        for (LoanSeed l : seed.loans()) {
            Member member = store.members().findByEmail(l.memberEmail()).orElseThrow();
            Book book = store.books().findById(l.isbn()).orElseThrow();
            BookCopy copy = book.availableCopies().get(0);
            Loan loan = new Loan(store.loans().nextId(), copy, member, today.minusDays(l.checkedOutDaysAgo()));
            copy.checkOut(member.getMemberId());
            if (l.returnedDaysAgo() != null) {
                LocalDate returned = today.minusDays(l.returnedDaysAgo());
                copy.checkIn();
                BigDecimal fee = loan.calculateFee(member.getType().feePolicy(), returned);
                loan.close(returned, fee);
            } else if (loan.isOverdue(today)) {
                copy.markOverdue();
            }
            store.books().saveCopy(copy);
            store.loans().save(loan);
        }
    }
}
