package libraryagent.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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

class RecommendationToolsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T14:00:00Z"), ZoneId.of("America/Toronto"));

    private LibraryServices services;
    private ToolManager tools;
    private CommandHistory history;

    @BeforeEach
    void setUp() {
        services = LibraryServices.inMemory(CLOCK);
        history = new CommandHistory();
        tools = new ToolManager();
        tools.register(new RecordFeedbackTool(services.feedback(), services.catalog()));
        tools.register(new RecommendBooksTool(services.feedback(), services.catalog()));
        tools.register(new CreateCuratedListTool(services.readingLists(), services.feedback(), services.catalog()));
    }

    @AfterEach
    void tearDown() {
        services.close();
    }

    private AgentContext as(String memberId) {
        Member member = services.members().requireMember(memberId);
        return new AgentContext("s", member, () -> history, m -> { }, List.of(), List.of(), Set.of());
    }

    private ToolResult call(String tool, Map<String, Object> args, AgentContext context) {
        return tools.execute(new ToolCall("id", tool, args), context);
    }

    @Test
    void recordFeedbackSavesRatingsForBorrowedBooksOnly() {
        AgentContext alex = as("M001");
        ToolResult ok = call("recordFeedback", Map.of("isbn", "9781000000009", "rating", "liked", "comment",
                "loved it"), alex);
        assertEquals("liked", ((Map<?, ?>) ok.data()).get("rating"));
        ToolResult notBorrowed = call("recordFeedback", Map.of("isbn", "9781000000001", "rating", "liked",
                "comment", ""), alex);
        assertTrue(notBorrowed.error().startsWith("You can only rate books you have borrowed."));
        assertTrue(call("recordFeedback", Map.of("isbn", "9781000000009", "rating", "meh", "comment", ""), alex)
                .error().contains("rating must be liked, neutral or disliked"));
    }

    @Test
    void recommendationsAreRealCandidatesAndAreRemembered() {
        AgentContext alex = as("M001");
        ToolResult result = call("recommendBooks", Map.of("count", 3, "theme", "", "maxPages", 0), alex);
        assertEquals(3, result.books().size());
        assertEquals("Project Hail Mary", result.books().get(0).getTitle());
        Map<?, ?> first = (Map<?, ?>) ((List<?>) ((Map<?, ?>) result.data()).get("candidates")).get(0);
        assertEquals("Andy Weir", first.get("matchedAuthor"));
        assertTrue(alex.wasRecommended("9781000000008"));
    }

    @Test
    void themeAndLengthNarrowCandidates() {
        AgentContext alex = as("M001");
        ToolResult result = call("recommendBooks", Map.of("count", 10, "theme", "Mystery", "maxPages", 300), alex);
        assertFalse(result.books().isEmpty());
        assertTrue(result.books().stream().allMatch(b -> b.getPageCount() <= 300));
        assertTrue(result.books().stream().allMatch(b -> b.getGenres().contains("Mystery")));
        assertFalse(result.books().stream().map(Book::getTitle).toList().contains("And Then There Were None"));
    }

    @Test
    void curatedListOnlyAcceptsRecommendedBooks() {
        AgentContext alex = as("M001");
        ToolResult rejected = call("createCuratedList",
                Map.of("listName", "Winter break", "isbns", List.of("9781000000017")), alex);
        assertTrue(rejected.error().startsWith("These ISBNs did not come from recommendBooks"));

        ToolResult recommended = call("recommendBooks", Map.of("count", 2, "theme", "", "maxPages", 0), alex);
        List<String> isbns = recommended.books().stream().map(Book::getIsbn).toList();
        ToolResult created = call("createCuratedList", Map.of("listName", "Winter break", "isbns", isbns), alex);
        assertTrue(created.success(), String.valueOf(created.error()));
        assertEquals(2, services.readingLists()
                .requireList(services.members().requireMember("M001"), "Winter break").getBooks().size());
        history.undoLast();
        assertEquals(1, services.readingLists()
                .requireList(services.members().requireMember("M001"), "Winter break").getBooks().size());
    }

    @Test
    void curatedListRejectsDislikedBooks() {
        AgentContext sam = new AgentContext("s", services.members().requireMember("M002"), () -> history, m -> { },
                List.of(), List.of(), Set.of("9781000000004"));
        call("recordFeedback", Map.of("isbn", "9781000000004", "rating", "disliked", "comment", ""), sam);
        ToolResult result = call("createCuratedList",
                Map.of("listName", "Faves", "isbns", List.of("9781000000004")), sam);
        assertTrue(result.error().startsWith("The member rated these books disliked"));
    }

    @Test
    void newMemberWithoutPreferencesGetsAHint() {
        Member jo = services.members().register("Jo", "jo@example.com", libraryagent.domain.MemberType.STANDARD);
        AgentContext ctx = new AgentContext("s", jo, () -> history, m -> { }, List.of(), List.of(), Set.of());
        ToolResult result = call("recommendBooks", Map.of("count", 5, "theme", "", "maxPages", 0), ctx);
        assertTrue(((Map<?, ?>) result.data()).get("hint").toString().contains("Ask what they enjoy"));
    }
}
