package libraryagent.config;

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

    AppConfig(Map<String, String> fileValues, Map<String, String> environment) {
        this.apiKey = read("ANTHROPIC_API_KEY", fileValues, environment, "");
        String requestedEffort = read("LLM_EFFORT", fileValues, environment, DEFAULT_EFFORT).toLowerCase(Locale.ROOT);
        this.effort = EFFORTS.contains(requestedEffort) ? requestedEffort : DEFAULT_EFFORT;
        this.maxSteps = parsePositiveInt(read("AGENT_MAX_STEPS", fileValues, environment, "8"), 8);
        this.dbPath = Path.of(read("DB_PATH", fileValues, environment, "data/library.db"));
    }

    /** Loads {@code .env} from the working directory, overridden by the process environment. */
    public static AppConfig load() {
        return new AppConfig(DotEnv.load(Path.of(".env")), System.getenv());
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
