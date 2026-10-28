package libraryagent.agent;

import java.util.List;
import libraryagent.domain.Book;

/**
 * The agent's reply to one message.
 *
 * @param books     catalogue books to display with the answer; always books returned by tools
 * @param toolsUsed names of the tools run while answering, in order
 * @param trace     each tool call with its arguments and outcome
 * @param error     true when the reply explains a failure (e.g. the AI service was unreachable)
 */
public record AgentResponse(String text, List<Book> books, List<String> toolsUsed, List<ToolTrace> trace,
                            boolean error) {

    public AgentResponse {
        books = List.copyOf(books);
        toolsUsed = List.copyOf(toolsUsed);
        trace = List.copyOf(trace);
    }

    public static AgentResponse failure(String text) {
        return new AgentResponse(text, List.of(), List.of(), List.of(), true);
    }
}
