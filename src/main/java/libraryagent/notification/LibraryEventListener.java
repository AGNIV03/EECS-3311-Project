package libraryagent.notification;

/** Observer: receives library events (implemented by the GUI views and the CLI notifier). */
@FunctionalInterface
public interface LibraryEventListener {

    void onEvent(LibraryEvent event);
}
