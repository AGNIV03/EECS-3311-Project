package libraryagent.agent.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import libraryagent.agent.AgentContext;
import libraryagent.agent.llm.ToolSpec;
import libraryagent.domain.Book;
import libraryagent.domain.Member;
import libraryagent.service.CatalogService;
import libraryagent.service.CirculationService;

/** Reports free copies and queue length for books, plus the current member's own status. */
public class CheckAvailabilityTool extends AbstractTool {

    static final int MAX_BOOKS = 10;

    private final CatalogService catalog;
    private final CirculationService circulation;

    public CheckAvailabilityTool(CatalogService catalog, CirculationService circulation) {
        this.catalog = catalog;
        this.circulation = circulation;
    }

    @Override
    public String name() {
        return "checkAvailability";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "Check whether books can be borrowed now: free copies, total copies and how many members are "
                        + "waiting. For a logged-in member it also says whether they already have the book on loan "
                        + "or reserved, and their queue position. Accepts up to " + MAX_BOOKS + " ISBNs at once.",
                properties("isbns", stringArrayProperty("ISBNs of the books to check.")),
                List.of("isbns"));
    }

    @Override
    protected List<String> validate(Map<String, Object> args) {
        List<String> errors = new ArrayList<>();
        int count = strings(args, "isbns").size();
        if (count == 0) {
            errors.add("isbns must list at least one ISBN");
        } else if (count > MAX_BOOKS) {
            errors.add("isbns can list at most " + MAX_BOOKS + " books");
        }
        return errors;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> args, AgentContext context) {
        List<Map<String, Object>> results = new ArrayList<>();
        List<Book> books = new ArrayList<>();
        Optional<Member> member = context.getMember();
        for (String isbn : strings(args, "isbns")) {
            Optional<Book> found = catalog.findByIsbn(isbn);
            if (found.isEmpty()) {
                results.add(Map.of("isbn", isbn, "error", "No book with this ISBN is in the catalogue."));
                continue;
            }
            Book book = found.get();
            books.add(book);
            Map<String, Object> view = new LinkedHashMap<>(ToolViews.bookSummary(book, catalog.availability(book)));
            member.ifPresent(m -> {
                view.put("youHaveItOnLoan", circulation.loansFor(m).stream().anyMatch(l -> l.getBook().equals(book)));
                circulation.holdsFor(m).stream().filter(r -> r.getBook().equals(book)).findFirst().ifPresent(r -> {
                    view.put("yourReservation", r.getStatus().name().toLowerCase());
                    int position = circulation.queuePosition(r);
                    if (position > 0) {
                        view.put("yourQueuePosition", position);
                    }
                });
            });
            results.add(view);
        }
        return ToolResult.ok(Map.of("books", results), books);
    }
}
