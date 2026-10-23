package libraryagent.notification;

/** Kinds of library events members are notified about. */
public enum EventType {
    /** A reserved book is on the hold shelf for the member. */
    HOLD_READY,
    /** A borrowed book is past its due date. */
    OVERDUE
}
