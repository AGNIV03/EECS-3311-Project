package libraryagent.domain.copy;

import libraryagent.domain.LibraryException;

/** On the shelf: can be reserved or lent to anyone. */
public final class AvailableState implements CopyState {

    public static final AvailableState INSTANCE = new AvailableState();

    private AvailableState() {
    }

    @Override
    public void reserve(BookCopy copy, String memberId) {
        copy.setReservedFor(memberId);
        copy.setState(ReservedState.INSTANCE);
    }

    @Override
    public void checkOut(BookCopy copy, String memberId) {
        copy.setState(OnLoanState.INSTANCE);
    }

    @Override
    public void checkIn(BookCopy copy) {
        throw new LibraryException("Copy " + copy.getCopyId() + " is not on loan.");
    }

    @Override
    public void markOverdue(BookCopy copy) {
        throw new LibraryException("Copy " + copy.getCopyId() + " is not on loan, so it cannot be overdue.");
    }

    @Override
    public void release(BookCopy copy) {
        throw new LibraryException("Copy " + copy.getCopyId() + " is not being held for anyone.");
    }

    @Override
    public String name() {
        return "Available";
    }
}
