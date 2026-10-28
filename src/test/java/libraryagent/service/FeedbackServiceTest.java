package libraryagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import libraryagent.domain.Book;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Member;
import libraryagent.domain.Rating;
import libraryagent.persistence.DataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Seed: Alex (M001, likes Science Fiction, Mystery, Andy Weir) has Dune out and returned The Martian
 * and And Then There Were None. Sam (M002, likes Fantasy and YA Fantasy, max 400 pages) has The
 * Hobbit out and returned Mistborn.
 */
class FeedbackServiceTest {

    private DataStore store;
    private FeedbackService feedback;
    private CatalogService catalog;
    private Member alex;
    private Member sam;

    @BeforeEach
    void setUp() {
        ServiceTestSupport.MutableClock clock = ServiceTestSupport.clock();
        store = ServiceTestSupport.seededStore(clock);
        feedback = new FeedbackService(store.feedback(), store.members(), store.loans(), clock);
        catalog = new CatalogService(store.books(), store.genres());
        alex = store.members().findById("M001").orElseThrow();
        sam = store.members().findById("M002").orElseThrow();
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private Book book(String isbn) {
        return store.books().findById(isbn).orElseThrow();
    }

    @Test
    void onlyBorrowedBooksCanBeRated() {
        LibraryException e = assertThrows(LibraryException.class,
                () -> feedback.recordFeedback(alex, book("9781000000001"), Rating.LIKED, ""));
        assertEquals("You can only rate books you have borrowed. \"The Hobbit\" is not in your borrowing history.",
                e.getMessage());
    }

    @Test
    void reRatingReplacesTheEarlierRating() {
        feedback.recordFeedback(alex, book("9781000000009"), Rating.LIKED, "great");
        feedback.recordFeedback(alex, book("9781000000009"), Rating.NEUTRAL, "on reflection, fine");
        assertEquals(Rating.NEUTRAL, feedback.feedbackFor(alex, book("9781000000009")).orElseThrow().getRating());
        assertEquals(1, feedback.feedbackFor(alex).size());
    }

    @Test
    void trackingOffRefusesRatingsAndIgnoresOldOnes() {
        feedback.recordFeedback(sam, book("9781000000004"), Rating.LIKED, "");
        feedback.setTracking(sam, false);
        assertThrows(LibraryException.class, () -> feedback.recordFeedback(sam, book("9781000000004"), Rating.LIKED, ""));
        assertFalse(feedback.tasteProfile(sam).likedAuthors().contains("Brandon Sanderson"));
        assertFalse(store.members().findById("M002").orElseThrow().getPreferences().isTrackingEnabled());
    }

    @Test
    void tasteProfileCombinesPreferencesLikesAndExclusions() {
        feedback.recordFeedback(sam, book("9781000000004"), Rating.LIKED, "");
        feedback.recordFeedback(sam, book("9781000000001"), Rating.DISLIKED, "");
        TasteProfile profile = feedback.tasteProfile(sam);
        assertEquals(List.of("Fantasy", "YA Fantasy"), profile.likedGenres());
        assertEquals(List.of("Brandon Sanderson"), profile.likedAuthors());
        assertTrue(profile.dislikedIsbns().contains("9781000000001"));
        assertTrue(profile.excludedIsbns().containsAll(List.of("9781000000001", "9781000000004")));
        assertEquals(400, profile.maxPages());
    }

    @Test
    void recommendationsFavourLikedAuthorsAndSkipBorrowedBooks() {
        List<Book> picks = catalog.recommendCandidates(alex, feedback.tasteProfile(alex), 5);
        assertEquals("Project Hail Mary", picks.get(0).getTitle());
        List<String> titles = picks.stream().map(Book::getTitle).toList();
        assertFalse(titles.contains("Dune"));
        assertFalse(titles.contains("The Martian"));
        assertFalse(titles.contains("And Then There Were None"));
    }

    @Test
    void recommendationsRespectPreferredLength() {
        List<Book> picks = catalog.recommendCandidates(sam, feedback.tasteProfile(sam), 20);
        assertFalse(picks.isEmpty());
        assertTrue(picks.stream().allMatch(b -> b.getPageCount() <= 400));
        assertFalse(picks.stream().map(Book::getTitle).toList().contains("Mistborn: The Final Empire"));
    }

    @Test
    void noPreferencesMeansNoRecommendations() {
        Member jo = new MemberService(store.members(), store.genres())
                .register("Jo", "jo@example.com", libraryagent.domain.MemberType.STANDARD);
        assertTrue(catalog.recommendCandidates(jo, feedback.tasteProfile(jo), 5).isEmpty());
    }
}
