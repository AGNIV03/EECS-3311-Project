package libraryagent.agent.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import libraryagent.agent.AgentContext;
import libraryagent.agent.llm.ToolSpec;
import libraryagent.command.CommandResult;
import libraryagent.command.RenewLoanCommand;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Loan;
import libraryagent.domain.Member;
import libraryagent.service.CirculationService;

/** Renews one of the logged-in member's loans; the library's renewal rules decide. */
public class RenewLoanTool extends AbstractTool {

    private final CirculationService circulation;

    public RenewLoanTool(CirculationService circulation) {
        this.circulation = circulation;
    }

    @Override
    public String name() {
        return "renewLoan";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "Renew a book the logged-in member has on loan, extending its due date by 21 days. Refused if "
                        + "the loan is overdue, was already renewed twice, or another member is waiting for the "
                        + "book; explain the reason if so. The user can undo this.",
                properties("isbn", stringProperty("ISBN of the borrowed book to renew.")),
                List.of("isbn"));
    }

    @Override
    protected List<String> validate(Map<String, Object> args) {
        List<String> errors = new ArrayList<>();
        requireString(args, "isbn", errors);
        return errors;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> args, AgentContext context) {
        Member member = context.requireMember();
        String isbn = string(args, "isbn");
        Loan loan = circulation.loansFor(member).stream()
                .filter(l -> l.getBook().getIsbn().equals(isbn))
                .findFirst()
                .orElseThrow(() -> new LibraryException(
                        "The member does not have a book with ISBN " + isbn + " on loan."));
        CommandResult result = context.getHistory().run(new RenewLoanCommand(circulation, loan));
        if (!result.success()) {
            return ToolResult.error(result.message());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("renewed", loan.getBook().getTitle());
        data.put("newDueDate", loan.getDueDate());
        data.put("renewalsUsed", loan.getRenewals() + " of " + Loan.MAX_RENEWALS);
        return ToolResult.ok(data, List.of(loan.getBook()));
    }
}
