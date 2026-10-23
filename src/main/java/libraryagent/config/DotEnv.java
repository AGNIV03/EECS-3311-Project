package libraryagent.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal reader for a {@code .env} file: {@code KEY=VALUE} lines, {@code #} comments,
 * optional single or double quotes around the value.
 */
public final class DotEnv {

    private DotEnv() {
    }

    /** Reads the file if it exists; a missing file yields an empty map. */
    public static Map<String, String> load(Path file) {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            return parse(Files.readAllLines(file));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
    }

    static Map<String, String> parse(List<String> lines) {
        Map<String, String> values = new HashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).strip();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = line.substring(eq + 1).strip();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        return values;
    }
}
