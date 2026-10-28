package libraryagent.ui.gui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import libraryagent.agent.AgentFactory;
import libraryagent.agent.GenreTagger;
import libraryagent.agent.Planner;
import libraryagent.agent.llm.ClaudeClient;
import libraryagent.config.AppConfig;
import libraryagent.controller.AgentController;
import libraryagent.controller.LibraryServices;
import libraryagent.controller.SessionContext;

/**
 * JavaFX entry point. Options: {@code --member <id>} signs in on start, {@code --tab <name>}
 * opens a tab, {@code --reset-db} recreates the database from the sample data.
 */
public class LibraryApp extends Application {

    private LibraryServices services;

    @Override
    public void start(Stage stage) {
        AppConfig config = AppConfig.load();
        List<String> args = getParameters().getRaw();
        if (args.contains("--reset-db")) {
            try {
                Files.deleteIfExists(config.dbPath());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        services = LibraryServices.open(config.dbPath(), Clock.systemDefaultZone());
        ReentrantLock dataLock = new ReentrantLock();
        ClaudeClient llm = config.apiKey().map(key -> new ClaudeClient(key, config.effort())).orElse(null);
        Planner planner = llm == null ? null : AgentFactory.createPlanner(services, llm, config.maxSteps(), dataLock);
        GenreTagger tagger = llm == null ? null : AgentFactory.createTagger(services, llm);
        AgentController controller = new AgentController(services, new SessionContext(), planner, tagger);

        MainWindow window = new MainWindow(controller, new Gui(dataLock));
        Scene scene = new Scene(window, 1180, 760);
        scene.getStylesheets().add(LibraryApp.class.getResource("/gui/app.css").toExternalForm());
        stage.setTitle("Library Agent");
        stage.setScene(scene);
        stage.show();

        int member = args.indexOf("--member");
        if (member >= 0 && member + 1 < args.size()) {
            window.onSignedIn(controller.login(args.get(member + 1)));
        }
        int tab = args.indexOf("--tab");
        if (tab >= 0 && tab + 1 < args.size()) {
            window.selectTab(args.get(tab + 1));
        }
    }

    @Override
    public void stop() {
        if (services != null) {
            services.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
