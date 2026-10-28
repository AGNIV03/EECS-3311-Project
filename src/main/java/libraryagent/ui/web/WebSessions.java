package libraryagent.ui.web;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import libraryagent.agent.GenreTagger;
import libraryagent.agent.Planner;
import libraryagent.controller.AgentController;
import libraryagent.controller.LibraryServices;
import libraryagent.controller.SessionContext;

/** Creates, finds and expires browser sessions, identified by a random cookie token. */
final class WebSessions {

    static final Duration IDLE_TIMEOUT = Duration.ofHours(2);
    static final int MAX_SESSIONS = 1000;

    private final Map<String, WebSession> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final LibraryServices services;
    private final Planner planner;
    private final GenreTagger tagger;
    private final Clock clock;

    WebSessions(LibraryServices services, Planner planner, GenreTagger tagger, Clock clock) {
        this.services = services;
        this.planner = planner;
        this.tagger = tagger;
        this.clock = clock;
    }

    /** The session for this cookie token, or a new session when the token is unknown or missing. */
    WebSession getOrCreate(String token) {
        Instant now = clock.instant();
        if (token != null) {
            WebSession existing = sessions.get(token);
            if (existing != null) {
                existing.lastSeen = now;
                return existing;
            }
        }
        if (sessions.size() >= MAX_SESSIONS) {
            sessions.values().stream().min(Comparator.comparing(s -> s.lastSeen)).ifPresent(this::remove);
        }
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String newToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        SessionContext context = new SessionContext();
        WebSession session = new WebSession(newToken, context,
                new AgentController(services, context, planner, tagger), now);
        services.notifications().subscribe(session);
        sessions.put(newToken, session);
        return session;
    }

    /** Removes sessions idle for longer than {@link #IDLE_TIMEOUT}. */
    void sweep() {
        Instant cutoff = clock.instant().minus(IDLE_TIMEOUT);
        sessions.values().stream().filter(s -> s.lastSeen.isBefore(cutoff)).toList().forEach(this::remove);
    }

    int size() {
        return sessions.size();
    }

    LibraryServices services() {
        return services;
    }

    private void remove(WebSession session) {
        sessions.remove(session.token);
        services.notifications().unsubscribe(session);
        if (planner != null) {
            planner.resetConversation(session.context.getSessionId());
        }
    }
}
