package libraryagent.command;

import libraryagent.domain.Loan;
import libraryagent.domain.Member;
import libraryagent.domain.copy.BookCopy;
import libraryagent.service.CirculationService;

/** Lends a copy to a member. Undo removes the loan and puts the copy back. */
public class CheckOutCommand implements Command {

    private final CirculationService circulation;
    private final Member member;
    private final BookCopy copy;
    private Loan loan;

    public CheckOutCommand(CirculationService circulation, Member member, BookCopy copy) {
        this.circulation = circulation;
        this.member = member;
        this.copy = copy;
    }

    @Override
    public CommandResult execute() {
        loan = circulation.checkOut(member, copy);
        return CommandResult.ok("Checked out \"" + copy.getBook().getTitle() + "\" (copy " + copy.getCopyId()
                + ") to " + member.getName() + ". Due " + loan.getDueDate() + ".");
    }

    @Override
    public void undo() {
        circulation.undoCheckOut(loan);
    }

    @Override
    public String describe() {
        return "checkout of \"" + copy.getBook().getTitle() + "\" to " + member.getName();
    }

    public Loan getLoan() {
        return loan;
    }
}
