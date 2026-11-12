package libraryagent.ui.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import libraryagent.agent.AgentResponse;
import libraryagent.agent.ToolTrace;
import libraryagent.controller.AgentController;
import libraryagent.domain.Book;

/**
 * Headless mode for scripts and automated agent tests ({@code --json}). Each input line is handled
 * exactly as in the interactive CLI (a command, or plain language for the assistant), and exactly
 * one JSON object is written per line:
 *
 * <pre>{"input", "kind", "member", "output"}</pre>
 *
 * and, when the line went to the assistant, also {@code "reply"}, {@code "error"},
 * {@code "tools"} (name, args, success, error) and {@code "books"} (isbn, title, author).
 * No prompts or banners are printed, so the output is one JSON document per line.
 */
public final class CliJsonMode {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final BufferedReader in;
    private final PrintStream out;
    private final AgentController controller;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    private final CliCommandParser parser = new CliCommandParser();
    private final CliApp app;

    public CliJsonMode(BufferedReader in, PrintStream out, AgentController controller, boolean llmEnabled) {
        this.in = in;
        this.out = out;
        this.controller = controller;
        this.app = new CliApp(in, capture, controller, llmEnabled);
        this.app.setQuiet(true);
    }

    /** Where text for the current line is collected; notices should be sent here too. */
    public PrintStream captureStream() {
        return capture;
    }

    /** Runs one command before the loop (e.g. the {@code --member} login) without reporting it. */
    public void runSilently(CliAction action) throws IOException {
        app.dispatch(action);
        buffer.reset();
    }

    public void run() throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            CliAction action = parser.parse(line);
            if (action.kind().equals("exit")) {
                break;
            }
            if (action.kind().equals("empty")) {
                continue;
            }
            buffer.reset();
            app.dispatch(action);
            capture.flush();
            out.println(JSON.writeValueAsString(describe(line.strip(), action, app.takeLastChat())));
            out.flush();
        }
    }

    private Map<String, Object> describe(String input, CliAction action, AgentResponse chat) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("input", input);
        result.put("kind", action.kind());
        result.put("member", controller.currentMember().map(m -> m.getMemberId()).orElse(null));
        result.put("output", buffer.toString(StandardCharsets.UTF_8).lines()
                .collect(java.util.stream.Collectors.joining("\n")).strip());
        if (chat != null) {
            result.put("reply", chat.text());
            result.put("error", chat.error());
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ToolTrace call : chat.trace()) {
                Map<String, Object> tool = new LinkedHashMap<>();
                tool.put("name", call.tool());
                tool.put("args", call.args());
                tool.put("success", call.success());
                tool.put("error", call.error());
                tools.add(tool);
            }
            result.put("tools", tools);
            List<Map<String, Object>> books = new ArrayList<>();
            for (Book book : chat.books()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("isbn", book.getIsbn());
                entry.put("title", book.getTitle());
                entry.put("author", book.getAuthor());
                books.add(entry);
            }
            result.put("books", books);
        }
        return result;
    }
}
