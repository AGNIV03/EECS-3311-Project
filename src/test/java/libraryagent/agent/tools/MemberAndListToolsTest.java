package libraryagent.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import libraryagent.agent.AgentContext;
import libraryagent.agent.ToolCall;
import libraryagent.command.CommandHistory;
import libraryagent.controller.LibraryServices;
import libraryagent.domain.Book;
import libraryagent.domain.Member;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemberAndListToolsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T14:00:00Z"), ZoneId.of("America/Toronto"));

    private LibraryServices services;
    private ToolManager tools;
    private CommandHistory history;
    private final List<Member> logins = new ArrayList<>();

    @BeforeEach
    void setUp() {
        services = LibraryServices.inMemory(CLOCK);
        history = new CommandHistory();
        tools = new ToolManager();
        tools.register(new RegisterMemberTool(services.members()));
        tools.register(new ManageReadingListTool(services.readingLists(), services.catalog()));
    }

    @AfterEach
    void tearDown() {
        services.close();
    }

    private AgentContext as(String memberId) {
        Member member = memberId == null ? null : services.members().requireMember(memberId);
        return new AgentContext("s", member, () -> history, logins::add, List.of(), List.of(), Set.of());
    }

    private ToolResult call(String tool, Map<String, Object> args, AgentContext context) {
        return tools.execute(new ToolCall("id", tool, args), context);
    }

    // ---- registerMember ------------------------------------------------------------------

    @Test
    void registrationCreatesAccountSavesPreferencesAndLogsIn() {
        AgentContext guest = as(null);
        ToolResult result = call("registerMember", Map.of("name", "Jo Lee", "email", "jo@example.com",
                "memberType", "student", "favoriteGenres", List.of("fantasy", "Mystery"),
                "favoriteAuthors", List.of("Le Guin"), "maxPages", 350), guest);

        assertTrue(result.success(), String.valueOf(result.error()));
        Map<?, ?> data = (Map<?, ?>) result.data();
        assertEquals("M003", data.get("memberId"));
        assertEquals(8, data.get("borrowLimit"));
        assertEquals(List.of("Fantasy", "Mystery"), data.get("favouriteGenres"));
        assertEquals(true, data.get("loggedIn"));
        assertEquals("M003", logins.get(0).getMemberId());
        assertEquals("M003", guest.requireMember().getMemberId());
        assertEquals(350, services.members().requireMember("M003").getPreferences().getMaxPages());
    }

    @Test
    void unknownGenreLeavesNoHalfCreatedAccount() {
        ToolResult result = call("registerMember", Map.of("name", "Jo", "email", "jo@example.com",
                "memberType", "standard", "favoriteGenres", List.of("cyberpunk")), as(null));
        assertFalse(result.success());
        assertTrue(result.error().startsWith("\"cyberpunk\" is not a genre in the catalogue."));
        assertEquals(3, services.members().allMembers().size());
    }

    @Test
    void registrationValidatesAndRejectsDuplicatesAndLoggedInMembers() {
        assertTrue(call("registerMember", Map.of("name", "Jo"), as(null)).error()
                .contains("email is required; memberType is required"));
        assertTrue(call("registerMember", Map.of("name", "A", "email", "alex.morgan@example.com",
                "memberType", "standard"), as(null)).error().contains("already exists"));
        assertTrue(call("registerMember", Map.of("name", "B", "email", "b@example.com",
                "memberType", "standard"), as("M001")).error().startsWith("You are already logged in as Alex Morgan"));
    }

    @Test
    void librarianCanRegisterSomeoneWithoutSwitchingAccounts() {
        AgentContext librarian = as("LIB001");
        ToolResult result = call("registerMember", Map.of("name", "Kim", "email", "kim@example.com",
                "memberType", "standard"), librarian);
        assertTrue(result.success());
        assertEquals(false, ((Map<?, ?>) result.data()).get("loggedIn"));
        assertTrue(logins.isEmpty());
        assertEquals("LIB001", librarian.requireMember().getMemberId());
    }

    // ---- manageReadingList ---------------------------------------------------------------

    @Test
    void createAddMoveRemoveAndUndo() {
        AgentContext alex = as("M001");
        assertTrue(call("manageReadingList", Map.of("action", "create", "listName", "Winter"), alex).success());
        call("manageReadingList", Map.of("action", "add", "listName", "winter", "isbn", "9781000000002"), alex);
        call("manageReadingList", Map.of("action", "add", "listName", "Winter", "isbn", "9781000000005"), alex);
        ToolResult moved = call("manageReadingList",
                Map.of("action", "move", "listName", "Winter", "isbn", "9781000000005", "position", 1), alex);
        assertEquals("Moved \"Piranesi\" to position 1 in Winter.", ((Map<?, ?>) moved.data()).get("message"));

        ToolResult shown = call("manageReadingList", Map.of("action", "show", "listName", "Winter"), alex);
        assertEquals(List.of("Piranesi", "A Wizard of Earthsea"), shown.books().stream().map(Book::getTitle).toList());

        history.undoLast();
        assertEquals("A Wizard of Earthsea",
                services.readingLists().requireList(services.members().requireMember("M001"), "Winter")
                        .getBooks().get(0).getTitle());
    }

    @Test
    void missingListAndDuplicatesAreReported() {
        AgentContext alex = as("M001");
        assertEquals("You don't have a reading list called \"Summer\".",
                call("manageReadingList", Map.of("action", "add", "listName", "Summer", "isbn", "9781000000002"),
                        alex).error());
        call("manageReadingList", Map.of("action", "create", "listName", "Summer"), alex);
        call("manageReadingList", Map.of("action", "add", "listName", "Summer", "isbn", "9781000000002"), alex);
        assertEquals("\"A Wizard of Earthsea\" is already on Summer.",
                call("manageReadingList", Map.of("action", "add", "listName", "Summer", "isbn", "9781000000002"),
                        alex).error());
    }

    @Test
    void listArgumentsAreValidated() {
        AgentContext alex = as("M001");
        assertTrue(call("manageReadingList", Map.of("action", "delete"), alex).error()
                .contains("action must be create, show, add, remove or move"));
        assertTrue(call("manageReadingList", Map.of("action", "move", "listName", "X", "isbn", "1"), alex).error()
                .contains("position must be 1 or more for move"));
        assertTrue(call("manageReadingList", Map.of("action", "create"), alex).error()
                .contains("listName is required"));
        assertTrue(call("manageReadingList", Map.of("action", "show", "listName", "*"), as(null)).error()
                .startsWith("The user is not logged in."));
    }

    @Test
    void showWithoutNameListsAllLists() {
        AgentContext alex = as("M001");
        call("manageReadingList", Map.of("action", "create", "listName", "A"), alex);
        call("manageReadingList", Map.of("action", "create", "listName", "B"), alex);
        ToolResult all = call("manageReadingList", Map.of("action", "show", "listName", "*"), alex);
        assertEquals(2, ((List<?>) ((Map<?, ?>) all.data()).get("lists")).size());
    }
}
