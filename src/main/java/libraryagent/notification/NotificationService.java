package libraryagent.notification;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observer pattern subject: services publish events here without knowing which views
 * display them; the GUI and CLI subscribe their listeners.
 */
public class NotificationService {

    private final List<LibraryEventListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(LibraryEventListener listener) {
        listeners.add(listener);
    }

    public void unsubscribe(LibraryEventListener listener) {
        listeners.remove(listener);
    }

    public void publish(LibraryEvent event) {
        for (LibraryEventListener listener : listeners) {
            listener.onEvent(event);
        }
    }
}
