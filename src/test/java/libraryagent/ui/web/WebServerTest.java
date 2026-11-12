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
import java.util.List;
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
    void searchSupportsGenreAndLengthAndListsEverythingWithoutParameters() throws Exception {
        HttpClient b = browser();
        assertEquals(40, get(b, "/api/search").body().get("books").size());
        JsonNode sciFi = get(b, "/api/search?genre=Science%20Fiction&maxPages=300").body().get("books");
        assertTrue(sciFi.size() > 0);
        sciFi.forEach(book -> assertTrue(book.get("pages").asInt() <= 300));
        assertEquals("maxPages must be a number.", get(b, "/api/search?maxPages=long").body().get("error").asText());
        assertTrue(get(b, "/api/genres").body().get("all").toString().contains("Fiction"));
    }

    @Test
    void healthCheckDoesNotCreateASession() throws Exception {
        HttpResponse<String> health = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri("/api/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
        assertTrue(health.headers().firstValue("Set-Cookie").isEmpty());
    }

    @Test
    void librarianPasscodeGuessesAreThrottled() throws Exception {
        HttpClient b = browser();
        Map<String, Object> guess = Map.of("memberId", "LIB001", "passcode", "wrong");
        for (int i = 0; i < 10; i++) {
            assertEquals(403, post(b, "/api/login", guess).status());
        }
        assertEquals(429, post(b, "/api/login", guess).status());
        assertEquals(429, post(b, "/api/login", Map.of("memberId", "LIB001", "passcode", "shelf-42")).status());
    }

    @Test
    void registrationWithABadPreferenceCreatesNoAccount() throws Exception {
        HttpClient b = browser();
        Map<String, Object> form = Map.of("name", "Pat Lee", "email", "pat@example.com", "memberType", "standard",
                "favoriteGenres", List.of("Cooking"), "favoriteAuthors", List.of(), "maxPages", 0);
        assertEquals(400, post(b, "/api/register", form).status());
        assertTrue(get(b, "/api/me").body().get("member").isNull());

        Map<String, Object> fixed = Map.of("name", "Pat Lee", "email", "pat@example.com", "memberType", "standard",
                "favoriteGenres", List.of("Fantasy"), "favoriteAuthors", List.of(), "maxPages", 0);
        Reply created = post(b, "/api/register", fixed);
        assertEquals(200, created.status());
        assertEquals("Pat Lee", created.body().get("member").get("name").asText());
    }

    @Test
    void interfaceFilesAreServedWithAContentSecurityPolicy() throws Exception {
        HttpResponse<String> script = browser().send(HttpRequest.newBuilder(uri("/app.js")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, script.statusCode());
        assertTrue(script.headers().firstValue("Content-Type").orElse("").startsWith("text/javascript"));
        assertTrue(script.headers().firstValue("Content-Security-Policy").orElse("").contains("default-src 'self'"));
        assertTrue(script.body().contains("textContent"));
    }

    @Test
    void loginSearchReserveAndAccount() throws Exception {
        HttpClient b = browser();
        assertTrue(get(b, "/api/me").body().get("member").isNull());
        assertEquals("Alex Morgan", post(b, "/api/login", Map.of("memberId", "M001", "email", "alex.morgan@example.com")).body()
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
    void memberSignInNeedsTheAccountsEmail() throws Exception {
        HttpClient b = browser();
        assertEquals(403, post(b, "/api/login", Map.of("memberId", "M001")).status());
        assertEquals(403, post(b, "/api/login", Map.of("memberId", "M001", "email", "sam.patel@example.com"))
                .status());
        assertTrue(get(b, "/api/me").body().get("member").isNull());
        assertEquals(200, post(b, "/api/login", Map.of("memberId", "M001", "email", "alex.morgan@example.com"))
                .status());
    }

    @Test
    void sessionsAreIsolatedPerBrowser() throws Exception {
        HttpClient alex = browser();
        HttpClient guest = browser();
        post(alex, "/api/login", Map.of("memberId", "M001", "email", "Alex.Morgan@Example.com"));
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

    private int chatFrom(String header, String address) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/api/chat"))
                        .header("Content-Type", "application/json").header(header, address)
                        .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"hi\"}")).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void behindCloudflareVisitorsAreToldApartByTheConfiguredHeader() throws Exception {
        tearDown();
        start(Map.of("CHAT_LIMIT_PER_MINUTE", "1", "TRUST_PROXY", "true", "CLIENT_IP_HEADER", "CF-Connecting-IP"),
                new BigDecimal("1.00"));
        llm.reply(FakeLLMClient.text("one")).reply(FakeLLMClient.text("two"));

        assertEquals(200, chatFrom("CF-Connecting-IP", "203.0.113.5"));
        assertEquals(200, chatFrom("CF-Connecting-IP", "203.0.113.6"));
        assertEquals(429, chatFrom("CF-Connecting-IP", "203.0.113.5"));
    }

    @Test
    void forwardedForIsIgnoredWhenAnotherHeaderIsConfigured() throws Exception {
        tearDown();
        start(Map.of("CHAT_LIMIT_PER_MINUTE", "1", "TRUST_PROXY", "true", "CLIENT_IP_HEADER", "CF-Connecting-IP"),
                new BigDecimal("1.00"));
        llm.reply(FakeLLMClient.text("one"));

        assertEquals(200, chatFrom("X-Forwarded-For", "198.51.100.1"));
        assertEquals(429, chatFrom("X-Forwarded-For", "198.51.100.2"));
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
        assertEquals("That member ID and email don't match an account.",
                post(b, "/api/login", Map.of("memberId", "M999", "email", "x@example.com")).body()
                        .get("error").asText());
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
