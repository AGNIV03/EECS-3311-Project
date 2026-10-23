package libraryagent.ui.gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import libraryagent.config.AppConfig;

/** JavaFX entry point for the Library Agent GUI. */
public class LibraryApp extends Application {

    @Override
    public void start(Stage stage) {
        AppConfig config = AppConfig.load();
        String status = config.llmEnabled()
                ? "AI assistant enabled (" + config.model() + ")"
                : "AI assistant disabled: no API key configured";
        stage.setTitle("Library Agent");
        stage.setScene(new Scene(new StackPane(new Label("Library Agent\n" + status)), 900, 600));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
