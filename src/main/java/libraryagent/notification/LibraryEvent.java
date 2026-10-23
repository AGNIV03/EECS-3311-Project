package libraryagent.notification;

/** Something a specific member should be told about. */
public record LibraryEvent(EventType type, String memberId, String message) {
}
