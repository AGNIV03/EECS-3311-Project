package libraryagent.agent.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import libraryagent.agent.AgentContext;
import libraryagent.agent.llm.ToolSpec;
import libraryagent.command.CommandResult;
import libraryagent.command.ReserveBookCommand;
import libraryagent.domain.Book;
import libraryagent.domain.Member;
import libraryagent.domain.Reservation;
import libraryagent.domain.ReservationStatus;
import libraryagent.service.Availability;
import libraryagent.service.CatalogService;
import libraryagent.service.CirculationService;

/**
 * Reserves a book for the logged-in member through the undo history. When no copy is free it
 * only joins the hold queue if {@code joinQueue} is true, so the agent must ask the user first.
 */
public class ReserveBookTool extends AbstractTool {

    private final CatalogService catalog;
    private final CirculationService circulation;

    public ReserveBookTool(CatalogService catalog, CirculationService circulation) {
        this.catalog = catalog;
        this.circulation = circulation;
    }

    @Override
    public String name() {
        return "reserveBook";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "Reserve a book for the logged-in member. If a copy is free it is held for them for 3 days. If "
                        + "no copy is free, nothing happens unless joinQueue is true, in which case they join the "
                        + "hold queue. Only set joinQueue after the user agreed to wait. The user can undo this.",
                properties(
                        "isbn", stringProperty("ISBN of the book to reserve."),
                        "joinQueue", Map.of("type", "boolean",
                                "description", "Join the hold queue if no copy is free (only with the user's consent).")),
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
        Book book = catalog.requireBook(string(args, "isbn"));
        Availability availability = catalog.availability(book);
        if (!availability.isAvailable() && !Boolean.TRUE.equals(args.get("joinQueue"))) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reserved", false);
            data.put("title", book.getTitle());
            data.put("reason", "No copies are free; " + availability.queueLength() + " member(s) are waiting. "
                    + "Ask the user whether to join the hold queue, then call again with joinQueue=true.");
            return ToolResult.ok(data, List.of(book));
        }
        ReserveBookCommand command = new ReserveBookCommand(circulation, member, book);
        CommandResult result = context.getHistory().run(command);
        if (!result.success()) {
            return ToolResult.error(result.message());
        }
        Reservation r = command.getReservation();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reserved", true);
        data.put("title", book.getTitle());
        data.put("status", r.getStatus() == ReservationStatus.READY ? "ready for pickup" : "waiting in queue");
        if (r.getStatus() == ReservationStatus.READY) {
            data.put("heldUntil", r.pickupDeadline());
        } else {
            data.put("queuePosition", circulation.queuePosition(r));
        }
        data.put("message", result.message());
        return ToolResult.ok(data, List.of(book));
    }
}
