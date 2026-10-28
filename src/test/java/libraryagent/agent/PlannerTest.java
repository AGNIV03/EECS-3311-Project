package libraryagent.agent;

import static libraryagent.agent.llm.FakeLLMClient.call;
import static libraryagent.agent.llm.FakeLLMClient.text;
import static libraryagent.agent.llm.FakeLLMClient.toolCalls;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import libraryagent.agent.llm.FakeLLMClient;
import libraryagent.agent.llm.LlmException;
import libraryagent.agent.llm.LlmRequest;
import libraryagent.agent.llm.Message;
import libraryagent.agent.llm.MessagePart;
import libraryagent.agent.llm.ToolSpec;
import libraryagent.agent.tools.Tool;
import libraryagent.agent.tools.ToolManager;
import libraryagent.agent.tools.ToolResult;
import libraryagent.command.CommandHistory;
import libraryagent.domain.Book;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Member;
import libraryagent.domain.MemberType;
import libraryagent.domain.ReadingPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlannerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T14:00:00Z"), ZoneId.of("America/Toronto"));
    private static final String SESSION = "s1";

    private final Book hobbit = new Book("9781000000001", "The Hobbit", "J.R.R. Tolkien", 310, "");
    private final Book dune = new Book("9781000000006", "Dune", "Frank Herbert", 617, "");
    private final Member alex = new Member("M001", "Alex Morgan", "a@x.com", MemberType.STANDARD, false,
            new ReadingPreferences());

    private FakeLLMClient llm;
    private MemoryManager memory;
    private Planner planner;

    /** Returns both books, or fails with a rule violation when asked to. */
    private final Tool lookup = new Tool() {
        @Override
        public String name() {
            return "lookup";
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("lookup", "Test tool", Map.of("q", Map.of("type", "string")), List.of("q"));
        }

        @Override
        public ToolResult execute(Map<String, Object> args, AgentContext context) {
            if ("fail".equals(args.get("q"))) {
                throw new LibraryException("That is not allowed.");
            }
            return ToolResult.ok(Map.of("count", 2), List.of(hobbit, dune));
        }
    };

    @BeforeEach
    void setUp() {
        llm = new FakeLLMClient();
        memory = new MemoryManager();
        ToolManager tools = new ToolManager();
        tools.register(lookup);
        planner = new Planner(llm, tools, new PromptComposer(CLOCK), memory, 4);
    }

    private AgentResponse ask(String message) {
        return planner.run(SESSION, alex, new CommandHistory(), message);
    }

    @Test
    void directAnswerNeedsNoTools() {
        llm.reply(text("Hello! How can I help?"));
        AgentResponse response = ask("hi");
        assertEquals("Hello! How can I help?", response.text());
        assertTrue(response.toolsUsed().isEmpty());
        assertFalse(response.error());
    }

    @Test
    void toolResultsAreFedBackWithMatchingIds() {
        llm.reply(toolCalls(call("t1", "lookup", Map.of("q", "fantasy"))))
                .reply(text("I found The Hobbit."));
        AgentResponse response = ask("find fantasy");

        assertEquals(List.of("lookup"), response.toolsUsed());
        LlmRequest second = llm.requests().get(1);
        Message results = second.messages().get(second.messages().size() - 1);
        MessagePart.ToolResult result = (MessagePart.ToolResult) results.parts().get(0);
        assertEquals("t1", result.toolUseId());
        assertFalse(result.isError());
        assertTrue(result.content().startsWith("{\"ok\":true,\"result\":{\"count\":2}"), result.content());
    }

    @Test
    void parallelToolCallsReturnAllResultsInOneMessage() {
        llm.reply(toolCalls(call("a", "lookup", Map.of("q", "x")), call("b", "lookup", Map.of("q", "y"))))
                .reply(text("Done."));
        ask("two lookups");
        List<Message> sent = llm.requests().get(1).messages();
        assertEquals(2, sent.get(sent.size() - 1).parts().size());
    }

    @Test
    void ruleViolationsAndUnknownToolsBecomeErrorResults() {
        llm.reply(toolCalls(call("a", "lookup", Map.of("q", "fail")), call("b", "nope", Map.of())))
                .reply(text("Sorry, that isn't allowed."));
        ask("do something");
        List<MessagePart> parts = llm.requests().get(1).messages().get(3).parts();
        MessagePart.ToolResult denied = (MessagePart.ToolResult) parts.get(0);
        MessagePart.ToolResult unknown = (MessagePart.ToolResult) parts.get(1);
        assertTrue(denied.isError());
        assertEquals("{\"ok\":false,\"error\":\"That is not allowed.\"}", denied.content());
        assertTrue(unknown.content().contains("There is no tool called \\\"nope\\\""));
    }

    @Test
    void onlyToolBooksNamedInTheAnswerAreShownInMentionOrder() {
        llm.reply(toolCalls(call("t1", "lookup", Map.of("q", "x"))))
                .reply(text("Try Dune, or The Hobbit. Also Piranesi is lovely."));
        AgentResponse response = ask("suggest something");
        assertEquals(List.of(dune, hobbit), response.books());
    }

    @Test
    void bookNamesWithoutToolResultsAreNeverShown() {
        llm.reply(text("You might like The Hobbit."));
        assertTrue(ask("suggest").books().isEmpty());
    }

    @Test
    void previousResultsAreAttachedToTheNextMessage() {
        llm.reply(toolCalls(call("t1", "lookup", Map.of("q", "x"))))
                .reply(text("1. The Hobbit 2. Dune"))
                .reply(text("Reserving Dune."));
        ask("find books");
        ask("reserve the second one");

        LlmRequest third = llm.requests().get(2);
        Message lastUser = third.messages().get(third.messages().size() - 2);
        assertTrue(lastUser.text().startsWith("reserve the second one\n\n[Books shown in my previous answer]"));
        assertTrue(lastUser.text().contains("2. Dune by Frank Herbert (ISBN 9781000000006)"));
        assertEquals(7, third.messages().size(), "turn 1 (5 messages) plus the new message and session note");
    }

    @Test
    void llmFailureReturnsFriendlyErrorAndKeepsHistoryUnchanged() {
        llm.fail(new LlmException(LlmException.Kind.UNAVAILABLE, "The AI assistant is temporarily unavailable.", null));
        AgentResponse response = ask("hello");
        assertTrue(response.error());
        assertEquals("The AI assistant is temporarily unavailable.", response.text());
        assertTrue(memory.history(SESSION).isEmpty());
    }

    @Test
    void stepLimitStopsTheLoopAndClosesTheTurn() {
        for (int i = 0; i < 4; i++) {
            llm.reply(toolCalls(call("t" + i, "lookup", Map.of("q", "x"))));
        }
        AgentResponse response = ask("loop forever");
        assertTrue(response.text().startsWith(Planner.STEP_LIMIT_TEXT));
        assertTrue(llm.exhausted());
        List<Message> history = memory.history(SESSION);
        assertEquals(Message.Role.ASSISTANT, history.get(history.size() - 1).role());
    }

    @Test
    void identicalFailingCallsAreStoppedAfterTwoFailures() {
        for (int i = 0; i < 3; i++) {
            llm.reply(toolCalls(call("t" + i, "lookup", Map.of("q", "fail"))));
        }
        llm.reply(text("Sorry, I couldn't do that."));
        AgentResponse response = ask("keep failing");
        assertEquals("Sorry, I couldn't do that.", response.text());
        assertEquals(Planner.REPEATED_FAILURE_TEXT, response.trace().get(2).error());
        assertEquals("That is not allowed.", response.trace().get(1).error());
    }

    @Test
    void refusalGetsAPoliteAnswer() {
        llm.reply(FakeLLMClient.refusal());
        assertEquals(ResponseParser.REFUSAL_TEXT, ask("something unsafe").text());
    }

    @Test
    void systemPromptIsStaticAndSessionDetailsArriveAsANote() {
        llm.reply(text("ok")).reply(text("ok again"));
        ask("hi");
        ask("hello");
        LlmRequest first = llm.requests().get(0);
        assertEquals(first.system(), llm.requests().get(1).system(), "system prompt must never change");
        assertTrue(first.system().contains("Session details (who is logged in, today's date) arrive as system notes"));
        Message note = first.messages().get(1);
        assertEquals(Message.Role.SYSTEM, note.role());
        assertEquals("Session: The user is Alex Morgan (member ID M001, standard member). Today's date: 2026-10-28.",
                note.text());
        assertEquals("lookup", first.tools().get(0).name());
    }

    @Test
    void loginDuringATurnAddsANewSessionNote() {
        ToolManager tools = new ToolManager();
        tools.register(new Tool() {
            @Override
            public String name() {
                return "signIn";
            }

            @Override
            public ToolSpec spec() {
                return new ToolSpec("signIn", "Test tool", Map.of(), List.of());
            }

            @Override
            public ToolResult execute(Map<String, Object> args, AgentContext context) {
                context.login(alex);
                return ToolResult.ok(Map.of("memberId", "M001"));
            }
        });
        List<Member> logins = new java.util.ArrayList<>();
        Planner guestPlanner = new Planner(llm, tools, new PromptComposer(CLOCK), memory, 4);
        llm.reply(toolCalls(call("t1", "signIn", Map.of()))).reply(text("Welcome!"));

        guestPlanner.run(SESSION, null, CommandHistory::new, logins::add, "sign me up");

        assertEquals(List.of(alex), logins);
        List<Message> sent = llm.requests().get(1).messages();
        assertEquals("Session: The user is not logged in. Today's date: 2026-10-28.", sent.get(1).text());
        Message last = sent.get(sent.size() - 1);
        assertEquals(Message.Role.SYSTEM, last.role());
        assertTrue(last.text().startsWith("Session: The user is Alex Morgan"));
    }

    @Test
    void failureAfterToolsRanSavesTheTurnWithANote() {
        llm.reply(toolCalls(call("t1", "lookup", Map.of("q", "x"))))
                .fail(new LlmException(LlmException.Kind.UNAVAILABLE, "Service down.", null));
        AgentResponse response = ask("find");
        assertTrue(response.error());
        assertEquals(1, response.trace().size());
        List<Message> history = memory.history(SESSION);
        assertEquals(Message.Role.ASSISTANT, history.get(history.size() - 1).role());
        assertTrue(history.get(history.size() - 1).text().contains("Service down."));
    }

    @Test
    void resetForgetsConversationAndResults() {
        llm.reply(toolCalls(call("t1", "lookup", Map.of("q", "x")))).reply(text("The Hobbit")).reply(text("hi"));
        ask("find");
        planner.resetConversation(SESSION);
        ask("hello again");
        assertEquals(2, llm.requests().get(2).messages().size());
        assertEquals("hello again", llm.requests().get(2).messages().get(0).text());
    }
}
