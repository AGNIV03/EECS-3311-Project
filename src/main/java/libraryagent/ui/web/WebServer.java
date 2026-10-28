package libraryagent.ui.web;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import libraryagent.agent.AgentFactory;
import libraryagent.agent.GenreTagger;
import libraryagent.agent.Planner;
import libraryagent.agent.llm.BudgetedLLMClient;
import libraryagent.agent.llm.ClaudeClient;
import libraryagent.agent.llm.LLMClient;
import libraryagent.config.AppConfig;
import libraryagent.controller.LibraryServices;

/**
 * The web version: a JSON API ({@code /api/...}) plus the browser interface, served by the JDK's
 * built-in HTTP server. Many visitors share one library; each browser gets its own session.
 */
public final class WebServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(WebServer.class.getName());

    private final HttpServer http;
    private final ExecutorService workers;
    private final ScheduledExecutorService housekeeping;

    private WebServer(HttpServer http, ExecutorService workers, ScheduledExecutorService housekeeping) {
        this.http = http;
        this.workers = workers;
        this.housekeeping = housekeeping;
    }

    public static void main(String[] args) {
        AppConfig config = AppConfig.load();
        Clock clock = Clock.systemDefaultZone();
        LibraryServices services = LibraryServices.open(config.dbPath(), clock);
        LLMClient llm = config.apiKey()
                .map(key -> (LLMClient) new BudgetedLLMClient(new ClaudeClient(key, config.effort()),
                        config.dailyBudgetUsd(), clock))
                .orElse(null);
        WebServer server = start(config, services, llm, config.webPort(), clock);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            services.close();
        }));
        LOG.info("Library Agent web version listening on port " + server.port()
                + (llm == null ? " (AI assistant off: no API key)" : " (daily AI budget $" + config.dailyBudgetUsd() + ")"));
    }

    /**
     * Starts the server.
     *
     * @param llm the model client (already budget-limited), or null to run without the assistant
     * @param port the port to listen on; 0 picks a free port (tests)
     */
    public static WebServer start(AppConfig config, LibraryServices services, LLMClient llm, int port, Clock clock) {
        ReentrantLock dataLock = new ReentrantLock();
        Planner planner = llm == null ? null : AgentFactory.createPlanner(services, llm, config.maxSteps(), dataLock);
        GenreTagger tagger = llm == null ? null : AgentFactory.createTagger(services, llm);
        WebSessions sessions = new WebSessions(services, planner, tagger, clock);
        RateLimiter chatLimiter = new RateLimiter(config.chatPerMinute(), config.chatPerHour(), clock);
        RateLimiter registerLimiter = new RateLimiter(3, 10, clock);
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
            http.createContext("/api/", new ApiHandler(sessions, dataLock, config, chatLimiter, registerLimiter));
            http.createContext("/", new StaticHandler());
            ExecutorService workers = Executors.newFixedThreadPool(16);
            http.setExecutor(workers);
            http.start();
            ScheduledExecutorService housekeeping = Executors.newSingleThreadScheduledExecutor();
            housekeeping.scheduleAtFixedRate(() -> {
                sessions.sweep();
                chatLimiter.sweep();
                registerLimiter.sweep();
            }, 10, 10, TimeUnit.MINUTES);
            return new WebServer(http, workers, housekeeping);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the web server on port " + port, e);
        }
    }

    public int port() {
        return http.getAddress().getPort();
    }

    @Override
    public void close() {
        http.stop(1);
        workers.shutdown();
        housekeeping.shutdown();
    }
}
