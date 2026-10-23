package libraryagent.domain.copy;

import libraryagent.domain.LibraryException;

/** Held for one member: only that member may check it out. */
public final class ReservedState implements CopyState {

    public static final ReservedState INSTANCE = new ReservedState();

    private ReservedState() {
    }

    @Override
    public void reserve(BookCopy copy, String memberId) {
        throw new LibraryException("Copy " + copy.getCopyId() + " is already being held for a member.");
    }

    @Override
    public void checkOut(BookCopy copy, String memberId) {
        if (!memberId.equals(copy.getReservedFor())) {
            throw new LibraryException("Copy " + copy.getCopyId() + " is being held for another member.");
        }
        copy.setReservedFor(null);
        copy.setState(OnLoanState.INSTANCE);
    }

    @Override
    public void checkIn(BookCopy copy) {
        throw new LibraryException("Copy " + copy.getCopyId() + " is not on loan; it is on the hold shelf.");
    }

    @Override
    public void markOverdue(BookCopy copy) {
        throw new LibraryException("Copy " + copy.getCopyId() + " is not on loan, so it cannot be overdue.");
    }

    @Override
    public void release(BookCopy copy) {
        copy.setReservedFor(null);
        copy.setState(AvailableState.INSTANCE);
    }

    @Override
    public String name() {
        return "Reserved";
    }
}
