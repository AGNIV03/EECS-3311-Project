package libraryagent.ui.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import libraryagent.agent.llm.BudgetedLLMClient;
import libraryagent.agent.llm.FakeLLMClient;
import libraryagent.config.AppConfig;
import libraryagent.controller.LibraryServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Starts the real web server on a free port and calls it over HTTP. */
class WebServerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T14:00:00Z"), ZoneId.of("America/Toronto"));
    private final ObjectMapper json = new ObjectMapper();

    private LibraryServices services;
    private FakeLLMClient llm;
    private WebServer server;

    private void start(Map<String, String> settings, BigDecimal budget) {
        services = LibraryServices.inMemory(CLOCK);
        llm = new FakeLLMClient();
        server = WebServer.start(AppConfig.fromValues(settings), services,
                new BudgetedLLMClient(llm, budget, CLOCK), 0, CLOCK);
    }

    @BeforeEach
    void setUp() {
        start(Map.of("CHAT_LIMIT_PER_MINUTE", "2", "LIBRARIAN_PASSCODE", "shelf-42"), new BigDecimal("1.00"));
    }

    @AfterEach
    void tearDown() {
        server.close();
        services.close();
    }

    /** A browser with its own cookie jar. */
    private HttpClient browser() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    }

    private record Reply(int status, JsonNode body) {
    }

    private Reply get(HttpClient client, String path) throws Exception {
        HttpResponse<String> r = client.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return new Reply(r.statusCode(), r.body().startsWith("{") ? json.readTree(r.body()) : null);
    }

    private Reply post(HttpClient client, String path, Map<String, Object> body) throws Exception {
        HttpResponse<String> r = client.send(HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
        return new Reply(r.statusCode(), json.readTree(r.body()));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + server.port() + path);
    }

    @Test
    void healthAndStaticPage() throws Exception {
        HttpClient b = browser();
        assertEquals("ok", get(b, "/api/health").body().get("status").asText());
        HttpResponse<String> page = b.send(HttpRequest.newBuilder(uri("/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("<title>Library Agent</title>"));
        assertEquals(404, get(b, "/../secret").status());
        assertEquals(404, get(b, "/api/nope").status());
    }

    @Test
    void loginSearchReserveAndAccount() throws Exception {
        HttpClient b = browser();
        assertTrue(get(b, "/api/me").body().get("member").isNull());
        assertEquals("Alex Morgan", post(b, "/api/login", Map.of("memberId", "M001")).body()
                .get("member").get("name").asText());
        JsonNode books = get(b, "/api/search?q=hobbit").body().get("books");
        assertEquals("The Hobbit", books.get(0).get("title").asText());

        JsonNode reserved = post(b, "/api/reserve", Map.of("isbn", "9781000000001")).body();
        assertTrue(reserved.get("success").asBoolean());
        JsonNode account = get(b, "/api/account").body();
        assertEquals("waiting", account.get("holds").get(0).get("status").asText());
        assertEquals("$2.25", account.get("feesOwed").asText());
        assertEquals("Undone: reservation of \"The Hobbit\".", post(b, "/api/undo", Map.of()).body()
                .get("message").asText());
    }

    @Test
    void sessionsAreIsolatedPerBrowser() throws Exception {
        HttpClient alex = browser();
        HttpClient guest = browser();
        post(alex, "/api/login", Map.of("memberId", "M001"));
        assertFalse(get(alex, "/api/me").body().get("member").isNull());
        assertTrue(get(guest, "/api/me").body().get("member").isNull());
        assertEquals("Please log in first.", get(guest, "/api/account").body().get("error").asText());
    }

    @Test
    void librarianSignInNeedsThePasscode() throws Exception {
        HttpClient b = browser();
        Reply wrong = post(b, "/api/login", Map.of("memberId", "LIB001", "passcode", "guess"));
        assertEquals(403, wrong.status());
        Reply right = post(b, "/api/login", Map.of("memberId", "LIB001", "passcode", "shelf-42"));
        assertTrue(right.body().get("member").get("librarian").asBoolean());
        assertEquals(1, get(b, "/api/desk/overdue").body().get("loans").size());
    }

    @Test
    void librarianSignInIsOffWithoutAPasscode() throws Exception {
        tearDown();
        start(Map.of(), new BigDecimal("1.00"));
        assertEquals("Librarian sign-in is disabled on this site.",
                post(browser(), "/api/login", Map.of("memberId", "LIB001")).body().get("error").asText());
    }

    @Test
    void chatIsRateLimitedPerVisitor() throws Exception {
        HttpClient b = browser();
        llm.reply(FakeLLMClient.text("Hello!")).reply(FakeLLMClient.text("Hi again!"));
        assertEquals("Hello!", post(b, "/api/chat", Map.of("message", "hi")).body().get("text").asText());
        post(b, "/api/chat", Map.of("message", "hi"));
        Reply limited = post(b, "/api/chat", Map.of("message", "hi"));
        assertEquals(429, limited.status());
    }

    @Test
    void chatStopsWhenTheDailyBudgetIsSpent() throws Exception {
        tearDown();
        start(Map.of(), BigDecimal.ZERO);
        JsonNode reply = post(browser(), "/api/chat", Map.of("message", "hi")).body();
        assertTrue(reply.get("error").asBoolean());
        assertTrue(reply.get("text").asText().contains("used up today's budget"));
    }

    @Test
    void badRequestsGetClearErrors() throws Exception {
        HttpClient b = browser();
        assertEquals("message is required.", post(b, "/api/chat", Map.of()).body().get("error").asText());
        assertEquals(400, post(b, "/api/chat", Map.of("message", "x".repeat(2001))).status());
        assertEquals("No member with ID M999.",
                post(b, "/api/login", Map.of("memberId", "M999")).body().get("error").asText());
    }

    @Test
    void registrationCreatesAccountAndSignsIn() throws Exception {
        HttpClient b = browser();
        JsonNode me = post(b, "/api/register", Map.of("name", "Jo Lee", "email", "jo@example.com",
                "memberType", "student", "favoriteGenres", java.util.List.of("Fantasy"))).body();
        assertEquals("M003", me.get("member").get("memberId").asText());
        assertEquals("Fantasy", me.get("member").get("favoriteGenres").get(0).asText());
    }
}
