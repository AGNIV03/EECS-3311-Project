package libraryagent.config;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Application settings. Values come from {@code .env} in the working directory, and real
 * environment variables override them. The API key is never logged or displayed.
 */
public final class AppConfig {

    /** The only model the application uses. Deliberately not configurable. */
    public static final String MODEL = "claude-sonnet-5-5";
    public static final String DEFAULT_EFFORT = "medium";
    private static final Set<String> EFFORTS = Set.of("low", "medium", "high", "xhigh", "max");
    private static final String KEY_PLACEHOLDER = "YOUR_LOCAL_API_KEY_HERE";

    private final String apiKey;
    private final String effort;
    private final int maxSteps;
    private final Path dbPath;
    private final int webPort;
    private final BigDecimal dailyBudgetUsd;
    private final int chatPerMinute;
    private final int chatPerHour;
    private final boolean trustProxy;
    private final boolean cookieSecure;
    private final String librarianPasscode;

    AppConfig(Map<String, String> fileValues, Map<String, String> environment) {
        this.apiKey = read("ANTHROPIC_API_KEY", fileValues, environment, "");
        String requestedEffort = read("LLM_EFFORT", fileValues, environment, DEFAULT_EFFORT).toLowerCase(Locale.ROOT);
        this.effort = EFFORTS.contains(requestedEffort) ? requestedEffort : DEFAULT_EFFORT;
        this.maxSteps = parsePositiveInt(read("AGENT_MAX_STEPS", fileValues, environment, "8"), 8);
        this.dbPath = Path.of(read("DB_PATH", fileValues, environment, "data/library.db"));
        this.webPort = parsePositiveInt(read("WEB_PORT", fileValues, environment, "8080"), 8080);
        this.dailyBudgetUsd = parseMoney(read("DAILY_BUDGET_USD", fileValues, environment, "2.00"));
        this.chatPerMinute = parsePositiveInt(read("CHAT_LIMIT_PER_MINUTE", fileValues, environment, "6"), 6);
        this.chatPerHour = parsePositiveInt(read("CHAT_LIMIT_PER_HOUR", fileValues, environment, "40"), 40);
        this.trustProxy = Boolean.parseBoolean(read("TRUST_PROXY", fileValues, environment, "false"));
        this.cookieSecure = Boolean.parseBoolean(read("COOKIE_SECURE", fileValues, environment, "false"));
        this.librarianPasscode = read("LIBRARIAN_PASSCODE", fileValues, environment, "");
    }

    /** Loads {@code .env} from the working directory, overridden by the process environment. */
    public static AppConfig load() {
        return new AppConfig(DotEnv.load(Path.of(".env")), System.getenv());
    }

    /** A configuration from explicit values only (no file or environment); used by tests. */
    public static AppConfig fromValues(Map<String, String> values) {
        return new AppConfig(values, Map.of());
    }

    /** Port the web version listens on. */
    public int webPort() {
        return webPort;
    }

    /** Estimated AI spend allowed per day in the web version; further chats are refused. */
    public BigDecimal dailyBudgetUsd() {
        return dailyBudgetUsd;
    }

    public int chatPerMinute() {
        return chatPerMinute;
    }

    public int chatPerHour() {
        return chatPerHour;
    }

    /** Use X-Forwarded-For for the client address (only behind a trusted reverse proxy). */
    public boolean trustProxy() {
        return trustProxy;
    }

    /** Mark session cookies Secure (when served over HTTPS). */
    public boolean cookieSecure() {
        return cookieSecure;
    }

    /** Passcode for librarian sign-in on the web version; empty disables librarian sign-in there. */
    public Optional<String> librarianPasscode() {
        return librarianPasscode.isBlank() ? Optional.empty() : Optional.of(librarianPasscode);
    }

    private static BigDecimal parseMoney(String value) {
        try {
            BigDecimal parsed = new BigDecimal(value);
            return parsed.signum() >= 0 ? parsed : new BigDecimal("2.00");
        } catch (NumberFormatException e) {
            return new BigDecimal("2.00");
        }
    }

    /** The API key, or empty when none is configured (AI features are then disabled). */
    public Optional<String> apiKey() {
        if (apiKey.isBlank() || apiKey.equals(KEY_PLACEHOLDER)) {
            return Optional.empty();
        }
        return Optional.of(apiKey);
    }

    public boolean llmEnabled() {
        return apiKey().isPresent();
    }

    public String model() {
        return MODEL;
    }

    /** Reasoning effort sent with each request: low, medium, high, xhigh or max. */
    public String effort() {
        return effort;
    }

    public int maxSteps() {
        return maxSteps;
    }

    public Path dbPath() {
        return dbPath;
    }

    private static String read(String key, Map<String, String> fileValues,
                               Map<String, String> environment, String fallback) {
        String fromEnv = environment.get(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.strip();
        }
        String fromFile = fileValues.get(key);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.strip();
        }
        return fallback;
    }

    private static int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
