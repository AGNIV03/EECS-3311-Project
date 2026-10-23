package libraryagent.domain.copy;

/**
 * State pattern: what a physical copy may do depends on its current state. Each state
 * either performs the transition (via {@link BookCopy#setState}) or refuses it with a
 * {@link libraryagent.domain.LibraryException} explaining why.
 */
public interface CopyState {

    /** Holds the copy for a member (a hold became ready, or a free copy was reserved). */
    void reserve(BookCopy copy, String memberId);

    /** Lends the copy to a member. */
    void checkOut(BookCopy copy, String memberId);

    /** Accepts the copy back from a loan. */
    void checkIn(BookCopy copy);

    /** Flags a lent copy as overdue. */
    void markOverdue(BookCopy copy);

    /** Releases a held copy (hold cancelled or expired) so it is available again. */
    void release(BookCopy copy);

    /** Display and storage name, e.g. "Available". */
    String name();
}
