package libraryagent.command;

import java.math.BigDecimal;
import libraryagent.domain.Loan;
import libraryagent.domain.copy.BookCopy;
import libraryagent.service.CirculationService;

/**
 * Checks a copy in. Undo reopens the loan, unless the copy has already been held for the
 * next member in the queue (they were notified, so the return stands).
 */
public class ReturnBookCommand implements Command {

    private final CirculationService circulation;
    private final String copyId;
    private Loan loan;
    private boolean wasOverdue;

    public ReturnBookCommand(CirculationService circulation, String copyId) {
        this.circulation = circulation;
        this.copyId = copyId;
    }

    @Override
    public CommandResult execute() {
        BookCopy copy = circulation.requireCopy(copyId);
        wasOverdue = "Overdue".equals(copy.getState().name());
        loan = circulation.checkIn(copyId);
        StringBuilder message = new StringBuilder("Returned \"" + loan.getBook().getTitle() + "\" (copy "
                + copy.getCopyId() + ") from " + loan.getMember().getName() + ".");
        if (loan.getFee().compareTo(BigDecimal.ZERO) > 0) {
            message.append(" Late fee: $").append(loan.getFee().toPlainString()).append('.');
        }
        if ("Reserved".equals(copy.getState().name())) {
            message.append(" The copy is now held for the next member in the queue.");
        }
        return CommandResult.ok(message.toString());
    }

    @Override
    public void undo() {
        circulation.undoCheckIn(loan, wasOverdue);
    }

    @Override
    public String describe() {
        return "return of \"" + (loan == null ? copyId : loan.getBook().getTitle()) + "\"";
    }

    public Loan getLoan() {
        return loan;
    }
}
