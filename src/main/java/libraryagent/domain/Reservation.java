package libraryagent.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import libraryagent.domain.copy.BookCopy;

/** A member's request for a book: waits in the hold queue until a copy is held for them. */
public class Reservation {

    /** Days a ready hold stays on the shelf before it expires. */
    public static final int PICKUP_DAYS = 3;

    private final String reservationId;
    private final Member member;
    private final Book book;
    private final LocalDateTime createdAt;
    private ReservationStatus status;
    private LocalDateTime readyAt;
    private BookCopy copy;

    public Reservation(String reservationId, Member member, Book book, LocalDateTime createdAt) {
        this(reservationId, member, book, createdAt, ReservationStatus.WAITING, null, null);
    }

    /** Rebuilds a reservation from storage. */
    public Reservation(String reservationId, Member member, Book book, LocalDateTime createdAt,
                       ReservationStatus status, LocalDateTime readyAt, BookCopy copy) {
        this.reservationId = Objects.requireNonNull(reservationId);
        this.member = Objects.requireNonNull(member);
        this.book = Objects.requireNonNull(book);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.status = Objects.requireNonNull(status);
        this.readyAt = readyAt;
        this.copy = copy;
    }

    /** A copy is now held for this member. */
    public void markReady(BookCopy heldCopy, LocalDateTime at) {
        requireStatus(ReservationStatus.WAITING, "become ready");
        this.copy = Objects.requireNonNull(heldCopy);
        this.readyAt = Objects.requireNonNull(at);
        this.status = ReservationStatus.READY;
    }

    /** The member collected the held copy. */
    public void fulfill() {
        requireStatus(ReservationStatus.READY, "be collected");
        status = ReservationStatus.FULFILLED;
    }

    /** Undoes {@link #fulfill()} when the checkout that collected it is undone. */
    public void reopen() {
        requireStatus(ReservationStatus.FULFILLED, "be reopened");
        status = ReservationStatus.READY;
    }

    public void cancel() {
        if (!isActive()) {
            throw new LibraryException("This reservation is already " + status.name().toLowerCase() + ".");
        }
        status = ReservationStatus.CANCELLED;
    }

    public void expire() {
        requireStatus(ReservationStatus.READY, "expire");
        status = ReservationStatus.EXPIRED;
    }

    /** True while the reservation is waiting or ready. */
    public boolean isActive() {
        return status == ReservationStatus.WAITING || status == ReservationStatus.READY;
    }

    /** True when a ready hold has been on the shelf longer than {@link #PICKUP_DAYS}. */
    public boolean isPickupOverdue(LocalDate today) {
        return status == ReservationStatus.READY
                && today.isAfter(readyAt.toLocalDate().plusDays(PICKUP_DAYS));
    }

    /** Last day the member can collect a ready hold, or null if it is not ready. */
    public LocalDate pickupDeadline() {
        return readyAt == null ? null : readyAt.toLocalDate().plusDays(PICKUP_DAYS);
    }

    private void requireStatus(ReservationStatus expected, String action) {
        if (status != expected) {
            throw new LibraryException("A " + status.name().toLowerCase() + " reservation cannot " + action + ".");
        }
    }

    public String getReservationId() {
        return reservationId;
    }

    public Member getMember() {
        return member;
    }

    public Book getBook() {
        return book;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public LocalDateTime getReadyAt() {
        return readyAt;
    }

    public BookCopy getCopy() {
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Reservation other && reservationId.equals(other.reservationId);
    }

    @Override
    public int hashCode() {
        return reservationId.hashCode();
    }
}
