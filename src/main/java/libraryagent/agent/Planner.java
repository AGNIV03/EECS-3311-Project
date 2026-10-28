package libraryagent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import libraryagent.agent.llm.LLMClient;
import libraryagent.agent.llm.LlmException;
import libraryagent.agent.llm.LlmResponse;
import libraryagent.agent.llm.Message;
import libraryagent.agent.llm.MessagePart;
import libraryagent.agent.llm.TokenUsage;
import libraryagent.agent.tools.ToolManager;
import libraryagent.agent.tools.ToolResult;
import libraryagent.command.CommandHistory;
import libraryagent.domain.Book;
import libraryagent.domain.Member;

/**
 * The agent loop. Each step sends the conversation and tool list to the model; tool calls are
 * run and their results fed back, until the model gives a final answer or the step limit is hit.
 */
public class Planner {

    static final String STEP_LIMIT_TEXT =
            "I couldn't finish that within the number of steps I'm allowed. Here's what I managed so far: ";
    /** A call that has failed this many times with identical arguments is not run again this turn. */
    static final int MAX_IDENTICAL_FAILURES = 2;
    static final String REPEATED_FAILURE_TEXT = "This exact call has already failed twice in this turn and was "
            + "not run again. Change the arguments, try a different tool, or explain the problem to the user.";

    private final LLMClient llm;
    private final ToolManager tools;
    private final PromptComposer composer;
    private final ResponseParser parser = new ResponseParser();
    private final MemoryManager memory;
    private final int maxSteps;
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public Planner(LLMClient llm, ToolManager tools, PromptComposer composer, MemoryManager memory, int maxSteps) {
        this.llm = llm;
        this.tools = tools;
        this.composer = composer;
        this.memory = memory;
        this.maxSteps = maxSteps;
    }

    /** Answers one user message with a fixed undo history and no login hook (used by tests). */
    public AgentResponse run(String sessionId, Member member, CommandHistory history, String message) {
        return run(sessionId, member, () -> history, m -> { }, message);
    }

    /**
     * Answers one user message, running tools as the model requests.
     *
     * @param history supplies the session's current undo history (logging in replaces it)
     * @param onLogin called when a tool logs a member in, e.g. after registering by chat
     */
    public AgentResponse run(String sessionId, Member member, Supplier<CommandHistory> history,
                             Consumer<Member> onLogin, String message) {
        Message userMessage = Message.user(composer.userTurn(message, memory.lastResults(sessionId)));
        Message sessionNote = Message.system(composer.sessionNote(member));
        List<Message> turn = new ArrayList<>(List.of(userMessage, sessionNote));
        List<Message> conversation = new ArrayList<>(memory.history(sessionId));
        conversation.addAll(turn);
        AgentContext context = new AgentContext(sessionId, member, history, onLogin, conversation,
                memory.lastResults(sessionId), memory.recommended(sessionId));
        List<ToolTrace> trace = new ArrayList<>();
        Map<String, Integer> failures = new HashMap<>();

        for (int step = 0; step < maxSteps; step++) {
            LlmResponse response;
            try {
                response = llm.complete(composer.buildRequest(context, tools.descriptions()));
            } catch (LlmException e) {
                return failed(sessionId, context, turn, message, trace, e);
            }
            Message reply = response.toMessage();
            add(context, turn, reply);

            AgentDecision decision = parser.parse(response);
            if (decision instanceof AgentDecision.FinalAnswer answer) {
                return finish(sessionId, context, turn, message, answer.text(), trace);
            }
            Member before = context.getMember().orElse(null);
            List<MessagePart.ToolResult> results = new ArrayList<>();
            for (ToolCall call : ((AgentDecision.ToolCalls) decision).calls()) {
                String signature = call.toolName() + call.args();
                ToolResult result = failures.getOrDefault(signature, 0) >= MAX_IDENTICAL_FAILURES
                        ? ToolResult.error(REPEATED_FAILURE_TEXT)
                        : tools.execute(call, context);
                if (!result.success()) {
                    failures.merge(signature, 1, Integer::sum);
                }
                context.addToolResult(call.toolName(), result);
                trace.add(new ToolTrace(call.toolName(), call.args(), result.success(), result.error()));
                results.add(new MessagePart.ToolResult(call.id(), toJson(result), !result.success()));
            }
            add(context, turn, Message.toolResults(results));
            Member after = context.getMember().orElse(null);
            if (!Objects.equals(before, after)) {
                // Someone logged in during this turn (e.g. registered by chat): tell the model.
                add(context, turn, Message.system(composer.sessionNote(after)));
            }
        }
        String summary = context.getToolsUsed().isEmpty() ? "nothing yet."
                : "I ran " + String.join(", ", context.getToolsUsed()) + ".";
        String text = STEP_LIMIT_TEXT + summary + " Please try a simpler request.";
        // Close the turn with an assistant message so the history keeps alternating roles.
        turn.add(Message.assistant(List.of(new MessagePart.Text(text)), null));
        return finish(sessionId, context, turn, message, text, trace);
    }

    /** Forgets the conversation (used when a different member logs in). */
    public void resetConversation(String sessionId) {
        memory.clear(sessionId);
    }

    public List<String> toolNames() {
        return tools.names();
    }

    /** Tokens used by the language model so far. */
    public TokenUsage usage() {
        return llm.totalUsage();
    }

    private static void add(AgentContext context, List<Message> turn, Message message) {
        context.addMessage(message);
        turn.add(message);
    }

    private AgentResponse finish(String sessionId, AgentContext context, List<Message> turn, String message,
                                 String text, List<ToolTrace> trace) {
        List<Book> shown = booksMentioned(text, context.getToolBooks());
        memory.appendTurn(sessionId, turn, message, text);
        if (!shown.isEmpty()) {
            memory.setLastResults(sessionId, shown);
        }
        memory.setRecommended(sessionId, context.getRecommended());
        return new AgentResponse(text, shown, context.getToolsUsed(), trace, false);
    }

    /**
     * The model could not be reached. If tools had already acted this turn, the turn is saved
     * with a closing note so the conversation matches what happened; otherwise nothing is saved.
     */
    private AgentResponse failed(String sessionId, AgentContext context, List<Message> turn, String message,
                                 List<ToolTrace> trace, LlmException e) {
        if (!trace.isEmpty()) {
            String note = "(The AI assistant stopped partway through: " + e.getMessage() + ")";
            turn.add(Message.assistant(List.of(new MessagePart.Text(note)), null));
            memory.appendTurn(sessionId, turn, message, note);
            memory.setRecommended(sessionId, context.getRecommended());
        }
        return new AgentResponse(e.getMessage(), List.of(), context.getToolsUsed(), trace, true);
    }

    /**
     * Grounding: the books displayed with an answer are the books returned by tools in this turn
     * that the answer actually names, in the order the answer names them.
     */
    static List<Book> booksMentioned(String text, List<Book> toolBooks) {
        String lower = text.toLowerCase(Locale.ROOT);
        Map<Book, Integer> positions = new LinkedHashMap<>();
        for (Book book : toolBooks) {
            int at = lower.indexOf(book.getTitle().toLowerCase(Locale.ROOT));
            if (at < 0) {
                at = lower.indexOf(book.getIsbn());
            }
            if (at >= 0) {
                positions.put(book, at);
            }
        }
        return positions.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private String toJson(ToolResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", result.success());
        if (result.success()) {
            payload.put("result", result.data());
        } else {
            payload.put("error", result.error());
        }
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"ok\":false,\"error\":\"The tool result could not be encoded.\"}";
        }
    }
}
