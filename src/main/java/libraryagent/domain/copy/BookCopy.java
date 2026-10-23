package libraryagent.domain.copy;

import java.util.List;
import java.util.Objects;
import libraryagent.domain.Book;

/**
 * A physical copy of a book. Every operation is delegated to the current {@link CopyState},
 * which decides whether it is allowed (State pattern context).
 */
public class BookCopy {

    private static final List<CopyState> STATES = List.of(
            AvailableState.INSTANCE, ReservedState.INSTANCE, OnLoanState.INSTANCE, OverdueState.INSTANCE);

    private final String copyId;
    private final Book book;
    private CopyState state = AvailableState.INSTANCE;
    /** Member the copy is being held for while in the Reserved state. */
    private String reservedFor;

    public BookCopy(String copyId, Book book) {
        this.copyId = Objects.requireNonNull(copyId);
        this.book = Objects.requireNonNull(book);
    }

    /** Rebuilds a copy from storage with its saved state. */
    public static BookCopy restore(String copyId, Book book, String stateName, String reservedFor) {
        BookCopy copy = new BookCopy(copyId, book);
        copy.state = STATES.stream()
                .filter(s -> s.name().equals(stateName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown copy state: " + stateName));
        copy.reservedFor = reservedFor;
        return copy;
    }

    public void reserve(String memberId) {
        state.reserve(this, memberId);
    }

    public void checkOut(String memberId) {
        state.checkOut(this, memberId);
    }

    public void checkIn() {
        state.checkIn(this);
    }

    public void markOverdue() {
        state.markOverdue(this);
    }

    public void release() {
        state.release(this);
    }

    public boolean isAvailable() {
        return state == AvailableState.INSTANCE;
    }

    public String getCopyId() {
        return copyId;
    }

    public Book getBook() {
        return book;
    }

    public CopyState getState() {
        return state;
    }

    public String getReservedFor() {
        return reservedFor;
    }

    void setState(CopyState state) {
        this.state = Objects.requireNonNull(state);
    }

    void setReservedFor(String memberId) {
        this.reservedFor = memberId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BookCopy other && copyId.equals(other.copyId);
    }

    @Override
    public int hashCode() {
        return copyId.hashCode();
    }

    @Override
    public String toString() {
        return copyId + " (" + state.name() + ")";
    }
}
