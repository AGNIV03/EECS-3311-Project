package libraryagent.domain.copy;

import libraryagent.domain.LibraryException;

/** Lent out and not yet due. */
public final class OnLoanState implements CopyState {

    public static final OnLoanState INSTANCE = new OnLoanState();

    private OnLoanState() {
    }

    @Override
    public void reserve(BookCopy copy, String memberId) {
        throw new LibraryException("Copy " + copy.getCopyId() + " is on loan; join the hold queue instead.");
    }

    @Override
    public void checkOut(BookCopy copy, String memberId) {
        throw new LibraryException("Copy " + copy.getCopyId() + " is already on loan.");
    }

    @Override
    public void checkIn(BookCopy copy) {
        copy.setState(AvailableState.INSTANCE);
    }

    @Override
    public void markOverdue(BookCopy copy) {
        copy.setState(OverdueState.INSTANCE);
    }

    @Override
    public void release(BookCopy copy) {
        throw new LibraryException("Copy " + copy.getCopyId() + " is on loan, not on the hold shelf.");
    }

    @Override
    public String name() {
        return "On Loan";
    }
}
