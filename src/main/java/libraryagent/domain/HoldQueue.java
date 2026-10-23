package libraryagent.domain;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** First-come, first-served queue of waiting reservations for one book. */
public class HoldQueue {

    private final Deque<Reservation> queue = new ArrayDeque<>();

    public void enqueue(Reservation reservation) {
        if (reservation.getStatus() != ReservationStatus.WAITING) {
            throw new IllegalArgumentException("Only waiting reservations can be queued");
        }
        if (!queue.contains(reservation)) {
            queue.addLast(reservation);
        }
    }

    /** Removes and returns the reservation at the front, or null if nobody is waiting. */
    public Reservation nextInLine() {
        return queue.pollFirst();
    }

    /** 1-based queue position of the member, or 0 if they are not waiting. */
    public int positionOf(Member member) {
        int position = 1;
        for (Reservation r : queue) {
            if (r.getMember().equals(member)) {
                return position;
            }
            position++;
        }
        return 0;
    }

    /** Removes a reservation (cancelled); later members move up. */
    public boolean remove(Reservation reservation) {
        return queue.remove(reservation);
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }

    public List<Reservation> toList() {
        return List.copyOf(queue);
    }
}
