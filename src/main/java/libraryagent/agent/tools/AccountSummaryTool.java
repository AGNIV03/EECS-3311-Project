package libraryagent.agent.tools;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import libraryagent.agent.AgentContext;
import libraryagent.agent.llm.ToolSpec;
import libraryagent.domain.Book;
import libraryagent.domain.Loan;
import libraryagent.domain.Member;
import libraryagent.domain.Reservation;
import libraryagent.domain.ReservationStatus;
import libraryagent.service.CirculationService;
import libraryagent.service.ReadingListService;

/** The logged-in member's loans, holds, fees and reading lists. */
public class AccountSummaryTool extends AbstractTool {

    private final CirculationService circulation;
    private final ReadingListService readingLists;

    public AccountSummaryTool(CirculationService circulation, ReadingListService readingLists) {
        this.circulation = circulation;
        this.readingLists = readingLists;
    }

    @Override
    public String name() {
        return "accountSummary";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "Get the logged-in member's account: current loans (due dates, overdue status, renewals used, "
                        + "fees accruing), reservations (ready or queue position), total fees owed, reading "
                        + "lists and saved preferences. Use it for questions about their loans, holds or fees, and "
                        + "to find the ISBN of a book they want to return or renew.",
                properties(),
                List.of());
    }

    @Override
    protected List<String> validate(Map<String, Object> args) {
        return List.of();
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> args, AgentContext context) {
        Member member = context.requireMember();
        LocalDate today = circulation.today();
        List<Book> books = new ArrayList<>();

        List<Map<String, Object>> loans = new ArrayList<>();
        for (Loan loan : circulation.loansFor(member)) {
            books.add(loan.getBook());
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("title", loan.getBook().getTitle());
            view.put("isbn", loan.getBook().getIsbn());
            view.put("dueDate", loan.getDueDate());
            view.put("overdue", loan.isOverdue(today));
            view.put("renewalsUsed", loan.getRenewals() + " of " + Loan.MAX_RENEWALS);
            view.put("feeSoFar", ToolViews.money(loan.calculateFee(member.getType().feePolicy(), today)));
            loans.add(view);
        }
        List<Map<String, Object>> holds = new ArrayList<>();
        for (Reservation r : circulation.holdsFor(member)) {
            books.add(r.getBook());
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("title", r.getBook().getTitle());
            view.put("isbn", r.getBook().getIsbn());
            if (r.getStatus() == ReservationStatus.READY) {
                view.put("status", "ready for pickup");
                view.put("heldUntil", r.pickupDeadline());
            } else {
                view.put("status", "waiting");
                view.put("queuePosition", circulation.queuePosition(r));
            }
            holds.add(view);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("member", member.getName() + " (" + member.getMemberId() + ", "
                + member.getType().name().toLowerCase() + ")");
        data.put("borrowLimit", member.getBorrowLimit());
        data.put("loans", loans);
        data.put("holds", holds);
        data.put("feesOwed", ToolViews.money(circulation.feesOwed(member)));
        data.put("readingLists", readingLists.listsFor(member).stream()
                .map(l -> l.getName() + " (" + l.getBooks().size() + " books)").toList());
        data.put("favouriteGenres", member.getPreferences().getFavoriteGenres());
        data.put("favouriteAuthors", member.getPreferences().getFavoriteAuthors());
        return ToolResult.ok(data, books);
    }
}
