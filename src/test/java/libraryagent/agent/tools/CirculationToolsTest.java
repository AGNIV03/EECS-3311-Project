package libraryagent.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import libraryagent.agent.AgentContext;
import libraryagent.command.CommandHistory;
import libraryagent.controller.LibraryServices;
import libraryagent.domain.Member;
import libraryagent.domain.ReservationStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Seed: Sam (M002) has the only copy of The Hobbit; Alex (M001) has Dune overdue. */
class CirculationToolsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T14:00:00Z"), ZoneId.of("America/Toronto"));
    private static final String HOBBIT = "9781000000001";
    private static final String DUNE = "9781000000006";
    private static final String PERSUASION = "9781000000018";

    private LibraryServices services;
    private CommandHistory history;

    @BeforeEach
    void setUp() {
        services = LibraryServices.inMemory(CLOCK);
        history = new CommandHistory();
    }

    @AfterEach
    void tearDown() {
        services.close();
    }

    private AgentContext as(String memberId) {
        Member member = memberId == null ? null : services.members().requireMember(memberId);
        return new AgentContext("s", member, history, List.of(), List.of());
    }

    private ReserveBookTool reserve() {
        return new ReserveBookTool(services.catalog(), services.circulation());
    }

    @Test
    void reservingAFreeCopyHoldsItAndIsUndoable() {
        ToolResult result = reserve().execute(Map.of("isbn", PERSUASION), as("M001"));
        Map<?, ?> data = (Map<?, ?>) result.data();
        assertEquals(true, data.get("reserved"));
        assertEquals("ready for pickup", data.get("status"));

        ToolResult undo = new UndoLastActionTool().execute(Map.of(), as("M001"));
        assertEquals("Undone: reservation of \"Persuasion\".", ((Map<?, ?>) undo.data()).get("message"));
        assertTrue(services.circulation().holdsFor(services.members().requireMember("M001")).isEmpty());
    }

    @Test
    void unavailableBookIsNotQueuedWithoutConsent() {
        ToolResult result = reserve().execute(Map.of("isbn", HOBBIT), as("M001"));
        Map<?, ?> data = (Map<?, ?>) result.data();
        assertEquals(false, data.get("reserved"));
        assertTrue(data.get("reason").toString().contains("call again with joinQueue=true"));
        assertFalse(history.canUndo());

        ToolResult queued = reserve().execute(Map.of("isbn", HOBBIT, "joinQueue", true), as("M001"));
        assertEquals(1, ((Map<?, ?>) queued.data()).get("queuePosition"));
        assertEquals(ReservationStatus.WAITING, services.circulation()
                .holdsFor(services.members().requireMember("M001")).get(0).getStatus());
    }

    @Test
    void ruleViolationsComeBackAsErrors() {
        ToolResult result = reserve().execute(Map.of("isbn", HOBBIT, "joinQueue", true), as("M002"));
        assertFalse(result.success());
        assertEquals("You already have \"The Hobbit\" on loan.", result.error());
    }

    @Test
    void guestsMustLogInFirst() {
        ToolManager manager = new ToolManager();
        manager.register(reserve());
        ToolResult result = manager.execute(new libraryagent.agent.ToolCall("1", "reserveBook",
                Map.of("isbn", PERSUASION)), as(null));
        assertTrue(result.error().startsWith("The user is not logged in."));
    }

    @Test
    void returnWorksOnlyForOwnLoansAndReportsFee() {
        ReturnBookTool tool = new ReturnBookTool(services.circulation());
        ToolManager manager = new ToolManager();
        manager.register(tool);
        ToolResult notMine = manager.execute(new libraryagent.agent.ToolCall("1", "returnBook",
                Map.of("isbn", DUNE)), as("M002"));
        assertEquals("The member does not have a book with ISBN " + DUNE + " on loan, so it cannot be returned.",
                notMine.error());

        ToolResult mine = tool.execute(Map.of("isbn", DUNE), as("M001"));
        assertEquals("$2.25", ((Map<?, ?>) mine.data()).get("lateFee"));
        assertTrue(services.circulation().loansFor(services.members().requireMember("M001")).isEmpty());
    }

    @Test
    void renewalRulesAreExplained() {
        RenewLoanTool tool = new RenewLoanTool(services.circulation());
        assertTrue(tool.execute(Map.of("isbn", DUNE), as("M001")).error().contains("overdue"));

        ToolResult ok = tool.execute(Map.of("isbn", HOBBIT), as("M002"));
        assertEquals("1 of 2", ((Map<?, ?>) ok.data()).get("renewalsUsed"));
    }

    @Test
    void accountSummaryListsLoansHoldsAndFees() {
        reserve().execute(Map.of("isbn", PERSUASION), as("M001"));
        ToolResult result = new AccountSummaryTool(services.circulation(), services.readingLists())
                .execute(Map.of(), as("M001"));
        Map<?, ?> data = (Map<?, ?>) result.data();
        assertEquals("$2.25", data.get("feesOwed"));
        Map<?, ?> loan = (Map<?, ?>) ((List<?>) data.get("loans")).get(0);
        assertEquals("Dune", loan.get("title"));
        assertEquals(true, loan.get("overdue"));
        Map<?, ?> hold = (Map<?, ?>) ((List<?>) data.get("holds")).get(0);
        assertEquals("ready for pickup", hold.get("status"));
        assertEquals(2, result.books().size());
    }

    @Test
    void undoWithNothingToUndoIsAnError() {
        assertEquals("Nothing to undo.", new UndoLastActionTool().execute(Map.of(), as("M001")).error());
    }
}
