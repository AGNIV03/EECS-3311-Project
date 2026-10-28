package libraryagent.controller;

import libraryagent.domain.Reservation;

/** A reservation together with the member's place in the hold queue (0 when not waiting). */
public record HoldStatus(Reservation reservation, int queuePosition) {
}
