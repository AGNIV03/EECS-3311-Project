package libraryagent.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import libraryagent.domain.Book;
import libraryagent.domain.BookFeedback;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Loan;
import libraryagent.domain.Member;
import libraryagent.domain.Rating;
import libraryagent.persistence.FeedbackRepository;
import libraryagent.persistence.LoanRepository;
import libraryagent.persistence.MemberRepository;

/** Records whether members liked what they borrowed and turns that into a taste profile. */
public class FeedbackService {

    private final FeedbackRepository feedback;
    private final MemberRepository members;
    private final LoanRepository loans;
    private final Clock clock;

    public FeedbackService(FeedbackRepository feedback, MemberRepository members, LoanRepository loans, Clock clock) {
        this.feedback = feedback;
        this.members = members;
        this.loans = loans;
        this.clock = clock;
    }

    /**
     * Records a rating for a book the member has borrowed. Rating the same book again replaces
     * the earlier rating. Refused while the member has tracking turned off.
     */
    public BookFeedback recordFeedback(Member member, Book book, Rating rating, String comment) {
        if (!member.getPreferences().isTrackingEnabled()) {
            throw new LibraryException("Reading feedback tracking is turned off for this account, so ratings are not "
                    + "saved. Turn it on to rate books (command: tracking on).");
        }
        if (!hasBorrowed(member, book)) {
            throw new LibraryException("You can only rate books you have borrowed. \"" + book.getTitle()
                    + "\" is not in your borrowing history.");
        }
        BookFeedback saved = new BookFeedback(member, book, rating, comment == null ? "" : comment.strip(),
                LocalDate.now(clock));
        feedback.save(saved);
        return saved;
    }

    public void setTracking(Member member, boolean on) {
        member.getPreferences().setTrackingEnabled(on);
        members.save(member);
    }

    public Optional<BookFeedback> feedbackFor(Member member, Book book) {
        return feedback.find(member, book);
    }

    public List<BookFeedback> feedbackFor(Member member) {
        return feedback.findByMember(member);
    }

    /**
     * Liked genres and authors combine the member's stated preferences with the books they rated
     * liked. With tracking off, only stated preferences are used. Borrowed and disliked books are
     * always excluded from recommendations.
     */
    public TasteProfile tasteProfile(Member member) {
        Set<String> genres = new LinkedHashSet<>(member.getPreferences().getFavoriteGenres());
        Set<String> authors = new LinkedHashSet<>(member.getPreferences().getFavoriteAuthors());
        Set<String> disliked = new LinkedHashSet<>();
        if (member.getPreferences().isTrackingEnabled()) {
            for (BookFeedback f : feedback.findByMember(member)) {
                if (f.getRating() == Rating.LIKED) {
                    genres.addAll(f.getBook().getGenres());
                    authors.add(f.getBook().getAuthor());
                } else if (f.getRating() == Rating.DISLIKED) {
                    disliked.add(f.getBook().getIsbn());
                }
            }
        }
        Set<String> excluded = new LinkedHashSet<>(disliked);
        loans.findByMember(member).forEach(l -> excluded.add(l.getBook().getIsbn()));
        return new TasteProfile(List.copyOf(genres), List.copyOf(authors), excluded, disliked,
                member.getPreferences().getMaxPages());
    }

    private boolean hasBorrowed(Member member, Book book) {
        return loans.findByMember(member).stream().map(Loan::getBook).anyMatch(book::equals);
    }
}
