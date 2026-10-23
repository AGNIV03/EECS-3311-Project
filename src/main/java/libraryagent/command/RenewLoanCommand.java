package libraryagent.command;

import java.time.LocalDate;
import libraryagent.domain.Loan;
import libraryagent.service.CirculationService;

/** Renews a loan. Undo restores the previous due date. */
public class RenewLoanCommand implements Command {

    private final CirculationService circulation;
    private final Loan loan;
    private LocalDate previousDueDate;

    public RenewLoanCommand(CirculationService circulation, Loan loan) {
        this.circulation = circulation;
        this.loan = loan;
    }

    @Override
    public CommandResult execute() {
        previousDueDate = loan.getDueDate();
        circulation.renew(loan);
        return CommandResult.ok("Renewed \"" + loan.getBook().getTitle() + "\". New due date: " + loan.getDueDate()
                + " (renewal " + loan.getRenewals() + " of " + Loan.MAX_RENEWALS + ").");
    }

    @Override
    public void undo() {
        circulation.undoRenew(loan, previousDueDate);
    }

    @Override
    public String describe() {
        return "renewal of \"" + loan.getBook().getTitle() + "\"";
    }

    public Loan getLoan() {
        return loan;
    }
}
