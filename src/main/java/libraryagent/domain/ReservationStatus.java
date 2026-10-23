package libraryagent.domain;

/** Lifecycle of a reservation. */
public enum ReservationStatus {
    /** In the hold queue, no copy yet. */
    WAITING,
    /** A copy is on the hold shelf for the member. */
    READY,
    /** The member checked the copy out. */
    FULFILLED,
    CANCELLED,
    /** A ready hold was not collected in time. */
    EXPIRED
}
