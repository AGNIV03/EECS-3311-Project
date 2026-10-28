package libraryagent.ui.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import libraryagent.agent.AgentResponse;
import libraryagent.config.AppConfig;
import libraryagent.controller.AccountSummary;
import libraryagent.controller.AgentController;
import libraryagent.domain.Book;
import libraryagent.domain.BookFeedback;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Loan;
import libraryagent.domain.Member;
import libraryagent.domain.MemberType;
import libraryagent.domain.Rating;

/**
 * The web version's JSON API. Every endpoint calls the same {@link AgentController} methods the
 * CLI uses. Requests from one browser run one at a time; library data is accessed under a shared
 * lock, except that chat holds the lock only while tools run, not while waiting for the model.
 */
final class ApiHandler implements HttpHandler {

    static final String COOKIE = "LA_SESSION";
    static final int MAX_BODY_BYTES = 16 * 1024;
    static final int MAX_MESSAGE_CHARS = 2000;
    private static final Logger LOG = Logger.getLogger(ApiHandler.class.getName());

    private final WebSessions sessions;
    private final Lock dataLock;
    private final AppConfig config;
    private final RateLimiter chatLimiter;
    private final RateLimiter registerLimiter;
    private final ObjectMapper json = new ObjectMapper();

    ApiHandler(WebSessions sessions, Lock dataLock, AppConfig config, RateLimiter chatLimiter,
               RateLimiter registerLimiter) {
        this.sessions = sessions;
        this.dataLock = dataLock;
        this.config = config;
        this.chatLimiter = chatLimiter;
        this.registerLimiter = registerLimiter;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        int status = 200;
        Object payload;
        try {
            WebSession session = sessions.getOrCreate(cookieToken(exchange));
            exchange.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + session.token
                    + "; Path=/; HttpOnly; SameSite=Strict" + (config.cookieSecure() ? "; Secure" : ""));
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            Map<String, Object> body = method.equals("POST") ? readBody(exchange) : Map.of();
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            session.lock.lock();
            try {
                payload = route(method, path, body, query, session, clientIp(exchange));
            } finally {
                session.lock.unlock();
            }
        } catch (ApiError e) {
            status = e.status;
            payload = Map.of("error", e.getMessage());
        } catch (LibraryException e) {
            status = 400;
            payload = Map.of("error", e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "API request failed", e);
            status = 500;
            payload = Map.of("error", "Something went wrong on the server.");
        }
        byte[] bytes = json.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private Object route(String method, String path, Map<String, Object> body, Map<String, String> query,
                         WebSession s, String ip) {
        AgentController c = s.controller;
        switch (method + " " + path) {
            case "GET /api/health":
                return Map.of("status", "ok");
            case "GET /api/me":
                return locked(() -> me(s));
            case "POST /api/login":
                return locked(() -> login(s, body));
            case "POST /api/logout":
                return locked(() -> {
                    c.logout();
                    return me(s);
                });
            case "POST /api/register":
                return register(s, body, ip);
            case "POST /api/chat":
                return chat(s, body, ip);
            case "GET /api/notifications":
                return Map.of("notices", s.drainNotices());
            case "GET /api/search":
                return locked(() -> Map.of("books", WebViews.books(c.searchKeywords(param(query, "q")), c)));
            case "GET /api/genres":
                return locked(() -> Map.of("subgenres", c.subgenres()));
            case "POST /api/reserve":
                return locked(() -> WebViews.result(c.reserve(memberId(c), text(body, "isbn"))));
            case "GET /api/account":
                return locked(() -> {
                    AccountSummary summary = c.accountSummary(memberId(c));
                    return WebViews.account(summary, c);
                });
            case "GET /api/history":
                return locked(() -> history(c));
            case "GET /api/lists":
                return locked(() -> Map.of("lists", c.readingLists().stream()
                        .map(l -> WebViews.readingList(l, c)).toList()));
            case "POST /api/lists":
                return locked(() -> WebViews.result(c.createList(text(body, "name"))));
            case "POST /api/lists/add":
                return locked(() -> WebViews.result(c.addToList(text(body, "list"), text(body, "isbn"))));
            case "POST /api/lists/remove":
                return locked(() -> WebViews.result(c.removeFromList(text(body, "list"), text(body, "isbn"))));
            case "POST /api/lists/move":
                return locked(() -> WebViews.result(c.moveInList(text(body, "list"), text(body, "isbn"),
                        number(body, "position"))));
            case "POST /api/rate":
                return locked(() -> {
                    BookFeedback f = c.rateBook(memberId(c), text(body, "isbn"), Rating.parse(text(body, "rating")),
                            optionalText(body, "comment"));
                    return Map.of("success", true, "message", "Saved: you " + f.getRating().name().toLowerCase()
                            + " \"" + f.getBook().getTitle() + "\".");
                });
            case "POST /api/tracking":
                return locked(() -> {
                    c.setTracking(memberId(c), Boolean.TRUE.equals(body.get("on")));
                    return me(s);
                });
            case "GET /api/recommendations":
                return locked(() -> Map.of("books", WebViews.books(c.recommendations(memberId(c), 5), c)));
            case "POST /api/undo":
                return locked(() -> Map.of("message", c.undoLast()));
            case "POST /api/desk/checkout":
                return locked(() -> WebViews.result(c.checkOut(text(body, "memberId"), text(body, "copyId"))));
            case "POST /api/desk/checkin":
                return locked(() -> WebViews.result(c.checkIn(text(body, "copyId"))));
            case "GET /api/desk/overdue":
                return locked(() -> {
                    List<Loan> overdue = c.findOverdue();
                    var today = c.accountSummary(memberId(c)).today();
                    return Map.of("loans", overdue.stream().map(l -> WebViews.loan(l, today)).toList());
                });
            case "POST /api/desk/books":
                return locked(() -> {
                    Book added = c.addBook(draft(body), number(body, "copies"), strings(body, "genres"));
                    return Map.of("success", true, "book", WebViews.bookDetail(added, c.availability(added)));
                });
            case "POST /api/desk/suggest-genres":
                // The model call happens outside the data lock; it reads only the genre list.
                return Map.of("genres", c.suggestGenres(draft(body)));
            default:
                break;
        }
        if (method.equals("GET") && path.startsWith("/api/books/")) {
            String isbn = decode(path.substring("/api/books/".length()));
            return locked(() -> {
                Book book = c.bookDetails(isbn);
                return WebViews.bookDetail(book, c.availability(book));
            });
        }
        if (method.equals("POST") && path.startsWith("/api/reservations/") && path.endsWith("/cancel")) {
            String id = decode(path.substring("/api/reservations/".length(), path.length() - "/cancel".length()));
            return locked(() -> WebViews.result(c.cancelReservation(id)));
        }
        if (method.equals("POST") && path.startsWith("/api/loans/") && path.endsWith("/renew")) {
            String id = decode(path.substring("/api/loans/".length(), path.length() - "/renew".length()));
            return locked(() -> WebViews.result(c.renew(id)));
        }
        throw new ApiError(404, "No such API endpoint: " + method + " " + path);
    }

    // ---- Endpoints with extra rules ------------------------------------------------------

    private Map<String, Object> me(WebSession s) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("member", s.controller.currentMember().map(WebViews::member).orElse(null));
        view.put("assistantAvailable", s.controller.assistantAvailable());
        view.put("librarianSignIn", config.librarianPasscode().isPresent());
        return view;
    }

    private Map<String, Object> login(WebSession s, Map<String, Object> body) {
        String memberId = text(body, "memberId");
        Member member = sessions.services().members().requireMember(memberId);
        if (member.isLibrarian()) {
            String expected = config.librarianPasscode()
                    .orElseThrow(() -> new ApiError(403, "Librarian sign-in is disabled on this site."));
            String given = optionalText(body, "passcode");
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    given.getBytes(StandardCharsets.UTF_8))) {
                throw new ApiError(403, "Wrong librarian passcode.");
            }
        }
        s.controller.login(member.getMemberId());
        return me(s);
    }

    private Map<String, Object> register(WebSession s, Map<String, Object> body, String ip) {
        if (!registerLimiter.tryAcquire(ip)) {
            throw new ApiError(429, "Too many sign-ups from your network. Please try again later.");
        }
        return locked(() -> {
            AgentController c = s.controller;
            Member member = c.register(text(body, "name"), text(body, "email"),
                    MemberType.parse(text(body, "memberType")));
            c.login(member.getMemberId());
            Object pages = body.get("maxPages");
            c.updatePreferences(member.getMemberId(), strings(body, "favoriteGenres"),
                    strings(body, "favoriteAuthors"), pages instanceof Number n ? n.intValue() : 0);
            return me(s);
        });
    }

    private Map<String, Object> chat(WebSession s, Map<String, Object> body, String ip) {
        String message = text(body, "message");
        if (message.length() > MAX_MESSAGE_CHARS) {
            throw new ApiError(400, "Messages can be at most " + MAX_MESSAGE_CHARS + " characters.");
        }
        if (!s.controller.assistantAvailable()) {
            throw new ApiError(503, "The AI assistant is not configured on this server.");
        }
        if (!chatLimiter.tryAcquire(ip)) {
            throw new ApiError(429, "You're sending messages too quickly. Please wait a minute and try again.");
        }
        // No data lock here: tools take it themselves, so other visitors aren't blocked while
        // this session waits for the model.
        AgentResponse response = s.controller.chat(message);
        return locked(() -> WebViews.agent(response, s.controller));
    }

    private Map<String, Object> history(AgentController c) {
        String memberId = memberId(c);
        var today = c.accountSummary(memberId).today();
        List<Map<String, Object>> loans = new ArrayList<>();
        for (Loan loan : c.loanHistory(memberId)) {
            Map<String, Object> view = WebViews.loan(loan, today);
            view.put("rating", c.ratingFor(memberId, loan.getBook().getIsbn())
                    .map(f -> f.getRating().name().toLowerCase()).orElse(null));
            loans.add(view);
        }
        return Map.of("loans", loans);
    }

    // ---- Helpers -------------------------------------------------------------------------

    private <T> T locked(Supplier<T> action) {
        dataLock.lock();
        try {
            return action.get();
        } finally {
            dataLock.unlock();
        }
    }

    private static String memberId(AgentController c) {
        return c.currentMember().orElseThrow(() -> new LibraryException("Please log in first.")).getMemberId();
    }

    private static Book draft(Map<String, Object> body) {
        return new Book(text(body, "isbn"), text(body, "title"), text(body, "author"), number(body, "pages"),
                optionalText(body, "description"));
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new ApiError(400, key + " is required.");
        }
        return value.toString().strip();
    }

    private static String optionalText(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : value.toString().strip();
    }

    private static int number(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(text(body, key));
        } catch (NumberFormatException e) {
            throw new ApiError(400, key + " must be a number.");
        }
    }

    private static List<String> strings(Map<String, Object> body, String key) {
        Object value = body.get(key);
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            list.forEach(v -> {
                if (v != null && !v.toString().isBlank()) {
                    result.add(v.toString().strip());
                }
            });
        }
        return result;
    }

    private static String param(Map<String, String> query, String key) {
        String value = query.get(key);
        if (value == null || value.isBlank()) {
            throw new ApiError(400, key + " is required.");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new ApiError(413, "Request body too large.");
            }
            if (bytes.length == 0) {
                return Map.of();
            }
            try {
                Object parsed = json.readValue(bytes, Object.class);
                if (parsed instanceof Map<?, ?> map) {
                    return (Map<String, Object>) map;
                }
            } catch (IOException e) {
                // Fall through to the error below.
            }
            throw new ApiError(400, "Request body must be a JSON object.");
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> params = new HashMap<>();
        if (raw == null) {
            return params;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                params.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
            }
        }
        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String cookieToken(HttpExchange exchange) {
        for (String header : exchange.getRequestHeaders().getOrDefault("Cookie", List.of())) {
            for (String part : header.split(";")) {
                String[] kv = part.strip().split("=", 2);
                if (kv.length == 2 && kv[0].equals(COOKIE)) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    private String clientIp(HttpExchange exchange) {
        if (config.trustProxy()) {
            String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].strip();
            }
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }
}
