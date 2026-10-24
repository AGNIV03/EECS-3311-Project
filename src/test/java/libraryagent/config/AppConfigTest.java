package libraryagent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppConfigTest {

    @Test
    void environmentOverridesDotEnvFile() {
        AppConfig config = new AppConfig(
                Map.of("LLM_EFFORT", "low", "AGENT_MAX_STEPS", "5"),
                Map.of("LLM_EFFORT", "high"));
        assertEquals("high", config.effort());
        assertEquals(5, config.maxSteps());
    }

    @Test
    void defaultsApplyWhenNothingConfigured() {
        AppConfig config = new AppConfig(Map.of(), Map.of());
        assertEquals("medium", config.effort());
        assertEquals(8, config.maxSteps());
        assertEquals(Path.of("data/library.db"), config.dbPath());
        assertFalse(config.llmEnabled());
    }

    @Test
    void modelIsAlwaysSonnetAndCannotBeOverridden() {
        AppConfig config = new AppConfig(Map.of("LLM_MODEL", "claude-haiku-5-5"),
                Map.of("LLM_MODEL", "claude-haiku-5-5"));
        assertEquals("claude-sonnet-5-5", config.model());
    }

    @Test
    void unknownEffortFallsBackToMedium() {
        AppConfig config = new AppConfig(Map.of("LLM_EFFORT", "turbo"), Map.of());
        assertEquals("medium", config.effort());
        assertEquals("xhigh", new AppConfig(Map.of("LLM_EFFORT", "XHigh"), Map.of()).effort());
    }

    @Test
    void placeholderKeyCountsAsMissing() {
        AppConfig config = new AppConfig(Map.of("ANTHROPIC_API_KEY", "YOUR_LOCAL_API_KEY_HERE"), Map.of());
        assertFalse(config.llmEnabled());
    }

    @Test
    void realKeyEnablesLlm() {
        AppConfig config = new AppConfig(Map.of(), Map.of("ANTHROPIC_API_KEY", "test-key"));
        assertTrue(config.llmEnabled());
    }

    @Test
    void invalidStepCountFallsBackToDefault() {
        AppConfig config = new AppConfig(Map.of("AGENT_MAX_STEPS", "-3"), Map.of());
        assertEquals(8, config.maxSteps());
    }

    @Test
    void dotEnvParsesCommentsQuotesAndExport() {
        Map<String, String> values = DotEnv.parse(List.of(
                "# comment",
                "",
                "export LLM_EFFORT=\"high\"",
                "DB_PATH='x.db'",
                "BROKEN_LINE"));
        assertEquals("high", values.get("LLM_EFFORT"));
        assertEquals("x.db", values.get("DB_PATH"));
        assertEquals(2, values.size());
    }
}
