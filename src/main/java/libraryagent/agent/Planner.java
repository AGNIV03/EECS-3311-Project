package libraryagent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import libraryagent.agent.llm.LLMClient;
import libraryagent.agent.llm.LlmException;
import libraryagent.agent.llm.LlmResponse;
import libraryagent.agent.llm.Message;
import libraryagent.agent.llm.MessagePart;
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

    /** Answers one user message, running tools as the model requests. */
    public AgentResponse run(String sessionId, Member member, CommandHistory history, String message) {
        String userText = composer.userTurn(message, memory.lastResults(sessionId));
        Message userMessage = Message.user(userText);
        List<Message> turn = new ArrayList<>(List.of(userMessage));
        List<Message> conversation = new ArrayList<>(memory.history(sessionId));
        conversation.add(userMessage);
        AgentContext context = new AgentContext(sessionId, member, history, conversation,
                memory.lastResults(sessionId));

        for (int step = 0; step < maxSteps; step++) {
            LlmResponse response;
            try {
                response = llm.complete(composer.buildRequest(context, tools.descriptions()));
            } catch (LlmException e) {
                // Nothing is saved, so the conversation stays valid for the next attempt.
                return new AgentResponse(e.getMessage(), List.of(), context.getToolsUsed(), true);
            }
            Message reply = response.toMessage();
            context.addMessage(reply);
            turn.add(reply);

            AgentDecision decision = parser.parse(response);
            if (decision instanceof AgentDecision.FinalAnswer answer) {
                return finish(sessionId, context, turn, message, answer.text());
            }
            List<MessagePart.ToolResult> results = new ArrayList<>();
            for (ToolCall call : ((AgentDecision.ToolCalls) decision).calls()) {
                ToolResult result = tools.execute(call, context);
                context.addToolResult(call.toolName(), result);
                results.add(new MessagePart.ToolResult(call.id(), toJson(result), !result.success()));
            }
            Message resultMessage = Message.toolResults(results);
            context.addMessage(resultMessage);
            turn.add(resultMessage);
        }
        String summary = context.getToolsUsed().isEmpty() ? "nothing yet."
                : "I ran " + String.join(", ", context.getToolsUsed()) + ".";
        String text = STEP_LIMIT_TEXT + summary + " Please try a simpler request.";
        // Close the turn with an assistant message so the history keeps alternating roles.
        turn.add(Message.assistant(List.of(new MessagePart.Text(text)), null));
        return finish(sessionId, context, turn, message, text);
    }

    /** Forgets the conversation (used when a different member logs in). */
    public void resetConversation(String sessionId) {
        memory.clear(sessionId);
    }

    public List<String> toolNames() {
        return tools.names();
    }

    private AgentResponse finish(String sessionId, AgentContext context, List<Message> turn, String message,
                                 String text) {
        List<Book> shown = booksMentioned(text, context.getToolBooks());
        memory.appendTurn(sessionId, turn, message, text);
        if (!shown.isEmpty()) {
            memory.setLastResults(sessionId, shown);
        }
        return new AgentResponse(text, shown, context.getToolsUsed(), false);
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
