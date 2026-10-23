package libraryagent.ui.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import libraryagent.config.AppConfig;

/** Command-line entry point for the Library Agent. */
public class CliApp {

    private final BufferedReader in;
    private final PrintStream out;
    private final AppConfig config;

    public CliApp(BufferedReader in, PrintStream out, AppConfig config) {
        this.in = in;
        this.out = out;
        this.config = config;
    }

    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        new CliApp(in, System.out, AppConfig.load()).repl();
    }

    public void repl() throws IOException {
        out.println("Library Agent CLI. Type 'help' for commands, 'exit' to quit.");
        if (!config.llmEnabled()) {
            out.println("(AI assistant disabled: no API key configured)");
        }
        String line;
        while (true) {
            out.print("> ");
            out.flush();
            line = in.readLine();
            if (line == null || line.strip().equalsIgnoreCase("exit")) {
                break;
            }
            if (line.strip().equalsIgnoreCase("help")) {
                out.println("Commands: help, exit");
            }
        }
        out.println("Goodbye.");
    }
}
