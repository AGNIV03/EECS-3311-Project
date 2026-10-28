package libraryagent.ui.gui;

import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Shared helpers for the views. Library data is only touched while holding the same lock the
 * agent's tools use, because the agent works on a background thread while the window stays usable.
 */
final class Gui {

    private final Lock dataLock;

    Gui(Lock dataLock) {
        this.dataLock = dataLock;
    }

    /** Runs a controller call that reads or changes library data. */
    <T> T data(Supplier<T> action) {
        dataLock.lock();
        try {
            return action.get();
        } finally {
            dataLock.unlock();
        }
    }

    void dataRun(Runnable action) {
        data(() -> {
            action.run();
            return null;
        });
    }

    /** Runs slow work (the AI assistant) off the UI thread, then reports back on the UI thread. */
    static <T> void background(Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                return work.get();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> onError.accept(task.getException()));
        Thread thread = new Thread(task, "library-agent-worker");
        thread.setDaemon(true);
        thread.start();
    }

    static void error(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        alert.setTitle("Library Agent");
        alert.showAndWait();
    }

    static void info(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        alert.setTitle("Library Agent");
        alert.show();
    }

    static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("heading");
        return label;
    }

    static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        label.setWrapText(true);
        return label;
    }

    /** A read-only text column. */
    static <T> javafx.scene.control.TableColumn<T, String> column(String title,
            java.util.function.Function<T, String> value, double width) {
        javafx.scene.control.TableColumn<T, String> column = new javafx.scene.control.TableColumn<>(title);
        column.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyStringWrapper(
                value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    static Region spacer() {
        Region region = new Region();
        javafx.scene.layout.HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }
}
