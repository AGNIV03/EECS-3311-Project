package libraryagent.agent.tools;

import java.util.List;
import libraryagent.domain.Book;

/**
 * What a tool returns to the agent.
 *
 * @param data  JSON-serialisable result shown to the model (maps, lists, records)
 * @param books catalogue books this result refers to; only these can be displayed as results
 */
public record ToolResult(boolean success, Object data, String error, List<Book> books) {

    public ToolResult {
        books = books == null ? List.of() : List.copyOf(books);
    }

    public static ToolResult ok(Object data) {
        return new ToolResult(true, data, null, List.of());
    }

    public static ToolResult ok(Object data, List<Book> books) {
        return new ToolResult(true, data, null, books);
    }

    public static ToolResult error(String message) {
        return new ToolResult(false, null, message, List.of());
    }
}
