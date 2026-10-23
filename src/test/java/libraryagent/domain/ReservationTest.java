package libraryagent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import libraryagent.domain.copy.BookCopy;
import org.junit.jupiter.api.Test;

class ReservationTest {

    private final Book book = new Book("978-1", "Dune", "Frank Herbert", 600, "");
    private final Member ana = member("M1");
    private final Member ben = member("M2");
    private final Member cai = member("M3");
    private final LocalDateTime t0 = LocalDateTime.of(2026, 10, 1, 9, 0);

    private static Member member(String id) {
        return new Member(id, id, id + "@x.com", MemberType.STANDARD, false, new ReadingPreferences());
    }

    @Test
    void holdQueueIsFirstComeFirstServed() {
        HoldQueue queue = new HoldQueue();
        Reservation r1 = new Reservation("R1", ana, book, t0);
        Reservation r2 = new Reservation("R2", ben, book, t0.plusMinutes(1));
        Reservation r3 = new Reservation("R3", cai, book, t0.plusMinutes(2));
        queue.enqueue(r1);
        queue.enqueue(r2);
        queue.enqueue(r3);
        assertEquals(2, queue.positionOf(ben));
        assertEquals(r1, queue.nextInLine());
        assertEquals(1, queue.positionOf(ben));
        assertEquals(0, queue.positionOf(ana));
    }

    @Test
    void cancellingMovesLaterMembersUp() {
        HoldQueue queue = new HoldQueue();
        Reservation r1 = new Reservation("R1", ana, book, t0);
        Reservation r2 = new Reservation("R2", ben, book, t0);
        queue.enqueue(r1);
        queue.enqueue(r2);
        assertTrue(queue.remove(r1));
        assertEquals(1, queue.positionOf(ben));
        assertEquals(List.of(r2), queue.toList());
    }

    @Test
    void emptyQueueReturnsNull() {
        assertNull(new HoldQueue().nextInLine());
    }

    @Test
    void readyHoldExpiresAfterPickupWindow() {
        BookCopy copy = new BookCopy("C1", book);
        Reservation r = new Reservation("R1", ana, book, t0);
        r.markReady(copy, t0);
        assertEquals(LocalDate.of(2026, 10, 4), r.pickupDeadline());
        assertFalse(r.isPickupOverdue(LocalDate.of(2026, 10, 4)));
        assertTrue(r.isPickupOverdue(LocalDate.of(2026, 10, 5)));
        r.expire();
        assertEquals(ReservationStatus.EXPIRED, r.getStatus());
        assertFalse(r.isActive());
    }

    @Test
    void statusTransitionsAreEnforced() {
        Reservation r = new Reservation("R1", ana, book, t0);
        assertThrows(LibraryException.class, r::fulfill);
        r.markReady(new BookCopy("C1", book), t0);
        r.fulfill();
        assertThrows(LibraryException.class, r::cancel);
        r.reopen();
        assertEquals(ReservationStatus.READY, r.getStatus());
        r.cancel();
        assertEquals(ReservationStatus.CANCELLED, r.getStatus());
    }

    @Test
    void onlyWaitingReservationsCanBeQueued() {
        Reservation r = new Reservation("R1", ana, book, t0);
        r.markReady(new BookCopy("C1", book), t0);
        assertThrows(IllegalArgumentException.class, () -> new HoldQueue().enqueue(r));
    }
}
