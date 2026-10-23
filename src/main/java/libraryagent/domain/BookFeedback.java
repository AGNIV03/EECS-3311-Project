package libraryagent.domain;

import java.time.LocalDate;
import java.util.Objects;

/** Whether a member liked a book they borrowed. One per member and book; re-rating replaces it. */
public class BookFeedback {

    private final Member member;
    private final Book book;
    private final Rating rating;
    private final String comment;
    private final LocalDate date;

    public BookFeedback(Member member, Book book, Rating rating, String comment, LocalDate date) {
        this.member = Objects.requireNonNull(member);
        this.book = Objects.requireNonNull(book);
        this.rating = Objects.requireNonNull(rating);
        this.comment = comment == null ? "" : comment;
        this.date = Objects.requireNonNull(date);
    }

    public Member getMember() {
        return member;
    }

    public Book getBook() {
        return book;
    }

    public Rating getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public LocalDate getDate() {
        return date;
    }
}
