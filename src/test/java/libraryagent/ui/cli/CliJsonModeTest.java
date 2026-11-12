package libraryagent.ui.cli;

import static libraryagent.agent.llm.FakeLLMClient.call;
import static libraryagent.agent.llm.FakeLLMClient.text;
import static libraryagent.agent.llm.FakeLLMClient.toolCalls;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import libraryagent.agent.AgentFactory;
import libraryagent.agent.Planner;
import libraryagent.agent.llm.FakeLLMClient;
import libraryagent.controller.AgentController;
import libraryagent.controller.LibraryServices;
import libraryagent.controller.SessionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Drives the headless JSON mode with scripted input and a scripted model. */
class CliJsonModeTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T14:00:00Z"), ZoneId.of("America/Toronto"));
    private static final ObjectMapper JSON = new ObjectMapper();

    private LibraryServices services;
    private FakeLLMClient llm;

    @BeforeEach
    void setUp() {
        services = LibraryServices.inMemory(CLOCK);
        llm = new FakeLLMClient();
    }

    @AfterEach
    void tearDown() {
        services.close();
    }

    /** Runs the lines and returns one parsed JSON object per output line. */
    private List<JsonNode> run(boolean withAssistant, String startMember, String... lines) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        Planner planner = withAssistant ? AgentFactory.createPlanner(services, llm, 6) : null;
        AgentController controller = new AgentController(services, new SessionContext(), planner, null);
        BufferedReader in = new BufferedReader(new StringReader(String.join("\n", lines) + "\n"));
        CliJsonMode mode = new CliJsonMode(in, out, controller, withAssistant);
        controller.subscribe(new CliNotifier(mode.captureStream(), controller));
        if (startMember != null) {
            mode.runSilently(new CliAction("login", List.of(startMember)));
        }
        mode.run();
        List<JsonNode> results = new ArrayList<>();
        for (String line : buffer.toString(StandardCharsets.UTF_8).lines().toList()) {
            if (!line.isBlank()) {
                results.add(JSON.readTree(line));
            }
        }
        return results;
    }

    @Test
    void chatLineReportsReplyToolsAndBooks() throws IOException {
        llm.reply(toolCalls(call("t1", "searchCatalog", Map.of("title", "Hobbit"))))
                .reply(text("We have The Hobbit, but its only copy is out."));

        List<JsonNode> results = run(true, "M001", "do you have the hobbit?");

        assertEquals(1, results.size());
        JsonNode result = results.get(0);
        assertEquals("do you have the hobbit?", result.get("input").asText());
        assertEquals("chat", result.get("kind").asText());
        assertEquals("M001", result.get("member").asText());
        assertEquals("We have The Hobbit, but its only copy is out.", result.get("reply").asText());
        assertFalse(result.get("error").asBoolean());
        assertEquals("searchCatalog", result.get("tools").get(0).get("name").asText());
        assertEquals("Hobbit", result.get("tools").get(0).get("args").get("title").asText());
        assertTrue(result.get("tools").get(0).get("success").asBoolean());
        assertEquals("9781000000001", result.get("books").get(0).get("isbn").asText());
        assertFalse(result.get("output").asText().contains("thinking"));
    }

    @Test
    void failedToolCallIsReportedWithItsError() throws IOException {
        llm.reply(toolCalls(call("t1", "searchCatalog", Map.of("genre", "Cooking"))))
                .reply(text("We don't have a Cooking section."));

        JsonNode tool = run(true, "M001", "any cooking books for me?").get(0).get("tools").get(0);

        assertFalse(tool.get("success").asBoolean());
        assertFalse(tool.get("error").asText().isBlank());
    }

    @Test
    void commandLinesGiveOneObjectEachWithoutPromptsOrBanner() throws IOException {
        List<JsonNode> results = run(false, null, "login M001", "", "reserve 9781000000001", "undo", "exit", "account");

        assertEquals(3, results.size());
        assertEquals("login", results.get(0).get("kind").asText());
        assertEquals("M001", results.get(0).get("member").asText());
        assertTrue(results.get(0).get("output").asText().startsWith("Welcome, Alex Morgan."));
        assertEquals("reserve", results.get(1).get("kind").asText());
        assertTrue(results.get(1).get("output").asText().contains("The Hobbit"));
        assertFalse(results.get(1).has("reply"));
        assertEquals("undo", results.get(2).get("kind").asText());
    }

    @Test
    void startMemberLoginIsNotReportedAndRuleViolationsAreOutput() throws IOException {
        List<JsonNode> results = run(false, "M001", "renew L9999");

        assertEquals(1, results.size());
        assertEquals("M001", results.get(0).get("member").asText());
        assertFalse(results.get(0).get("output").asText().isBlank());
        assertFalse(results.get(0).get("output").asText().contains("Welcome"));
    }

    @Test
    void chatWithoutAssistantSaysSoAndNotLoggedInMemberIsNull() throws IOException {
        JsonNode result = run(false, null, "what should I read next?").get(0);

        assertEquals("chat", result.get("kind").asText());
        assertTrue(result.get("member").isNull());
        assertTrue(result.get("output").asText().contains("turned off"));
        assertFalse(result.has("reply"));
    }
}
