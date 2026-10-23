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
                Map.of("LLM_MODEL", "from-file", "AGENT_MAX_STEPS", "5"),
                Map.of("LLM_MODEL", "from-env"));
        assertEquals("from-env", config.model());
        assertEquals(5, config.maxSteps());
    }

    @Test
    void defaultsApplyWhenNothingConfigured() {
        AppConfig config = new AppConfig(Map.of(), Map.of());
        assertEquals(AppConfig.DEFAULT_MODEL, config.model());
        assertEquals("low", config.effort());
        assertEquals(8, config.maxSteps());
        assertEquals(Path.of("data/library.db"), config.dbPath());
        assertFalse(config.llmEnabled());
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
                "export LLM_MODEL=\"claude-sonnet-5-5\"",
                "DB_PATH='x.db'",
                "BROKEN_LINE"));
        assertEquals("claude-sonnet-5-5", values.get("LLM_MODEL"));
        assertEquals("x.db", values.get("DB_PATH"));
        assertEquals(2, values.size());
    }
}
