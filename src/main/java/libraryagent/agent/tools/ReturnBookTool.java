package libraryagent.agent.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import libraryagent.agent.AgentContext;
import libraryagent.agent.llm.ToolSpec;
import libraryagent.command.CommandResult;
import libraryagent.command.ReturnBookCommand;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Loan;
import libraryagent.domain.Member;
import libraryagent.service.CirculationService;

/** Self-service return: checks in one of the logged-in member's own open loans. */
public class ReturnBookTool extends AbstractTool {

    private final CirculationService circulation;

    public ReturnBookTool(CirculationService circulation) {
        this.circulation = circulation;
    }

    @Override
    public String name() {
        return "returnBook";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "Return (check in) a book the logged-in member currently has on loan, at the self-service "
                        + "kiosk. Only the member's own open loans can be returned; use accountSummary to find "
                        + "their ISBNs. Reports any late fee. The user can undo this unless the copy goes to the "
                        + "next member waiting for it.",
                properties("isbn", stringProperty("ISBN of the borrowed book being returned.")),
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
                        "The member does not have a book with ISBN " + isbn + " on loan, so it cannot be returned."));
        CommandResult result = context.getHistory().run(
                new ReturnBookCommand(circulation, loan.getCopy().getCopyId()));
        if (!result.success()) {
            return ToolResult.error(result.message());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("returned", loan.getBook().getTitle());
        data.put("lateFee", ToolViews.money(loan.getFee()));
        data.put("message", result.message());
        return ToolResult.ok(data, List.of(loan.getBook()));
    }
}
