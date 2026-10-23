package libraryagent.domain.copy;

import libraryagent.domain.LibraryException;

/** Lent out and past its due date: it can only be returned. */
public final class OverdueState implements CopyState {

    public static final OverdueState INSTANCE = new OverdueState();

    private OverdueState() {
    }

    @Override
    public void reserve(BookCopy copy, String memberId) {
        throw new LibraryException("Copy " + copy.getCopyId() + " is overdue; join the hold queue instead.");
    }

    @Override
    public void checkOut(BookCopy copy, String memberId) {
        throw new LibraryException("Copy " + copy.getCopyId() + " is already on loan (overdue).");
    }

    @Override
    public void checkIn(BookCopy copy) {
        copy.setState(AvailableState.INSTANCE);
    }

    @Override
    public void markOverdue(BookCopy copy) {
        // Already overdue; nothing changes.
    }

    @Override
    public void release(BookCopy copy) {
        throw new LibraryException("Copy " + copy.getCopyId() + " is on loan, not on the hold shelf.");
    }

    @Override
    public String name() {
        return "Overdue";
    }
}
