package libraryagent.persistence;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import libraryagent.domain.Book;
import libraryagent.domain.BookFeedback;
import libraryagent.domain.Member;
import libraryagent.domain.Rating;

/** One rating per member and book; saving again replaces the earlier rating. */
public class FeedbackRepository implements Repository<BookFeedback> {

    private final Database db;
    private final Map<String, BookFeedback> feedback = new LinkedHashMap<>();

    public FeedbackRepository(Database db, BookRepository books, MemberRepository members) {
        this.db = db;
        db.query("SELECT member_id, isbn, rating, comment, rated_on FROM feedback",
                        rs -> new BookFeedback(members.findById(rs.getString(1)).orElseThrow(),
                                books.findById(rs.getString(2)).orElseThrow(),
                                Rating.valueOf(rs.getString(3)), rs.getString(4), LocalDate.parse(rs.getString(5))))
                .forEach(f -> feedback.put(key(f.getMember(), f.getBook()), f));
    }

    @Override
    public void save(BookFeedback f) {
        db.update("INSERT INTO feedback(member_id, isbn, rating, comment, rated_on) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT(member_id, isbn) DO UPDATE SET rating = excluded.rating, "
                        + "comment = excluded.comment, rated_on = excluded.rated_on",
                f.getMember().getMemberId(), f.getBook().getIsbn(), f.getRating().name(), f.getComment(),
                f.getDate().toString());
        feedback.put(key(f.getMember(), f.getBook()), f);
    }

    /** Id format: {@code memberId|isbn}. */
    @Override
    public Optional<BookFeedback> findById(String id) {
        return Optional.ofNullable(feedback.get(id));
    }

    @Override
    public List<BookFeedback> findAll() {
        return List.copyOf(feedback.values());
    }

    public Optional<BookFeedback> find(Member member, Book book) {
        return Optional.ofNullable(feedback.get(key(member, book)));
    }

    public List<BookFeedback> findByMember(Member member) {
        return feedback.values().stream().filter(f -> f.getMember().equals(member)).toList();
    }

    private static String key(Member member, Book book) {
        return member.getMemberId() + "|" + book.getIsbn();
    }
}
