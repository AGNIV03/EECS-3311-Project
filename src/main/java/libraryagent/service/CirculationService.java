package libraryagent.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import libraryagent.domain.Book;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Loan;
import libraryagent.domain.Member;
import libraryagent.domain.Reservation;
import libraryagent.domain.ReservationStatus;
import libraryagent.domain.copy.BookCopy;
import libraryagent.notification.EventType;
import libraryagent.notification.LibraryEvent;
import libraryagent.notification.NotificationService;
import libraryagent.persistence.BookRepository;
import libraryagent.persistence.LoanRepository;
import libraryagent.persistence.ReservationRepository;

/**
 * Reservations, checkouts, returns, renewals and overdue processing. Enforces the library's
 * rules; copy-level legality is decided by each copy's state.
 */
public class CirculationService {

    /** Maximum waiting-or-ready reservations per member. */
    public static final int MAX_ACTIVE_HOLDS = 5;

    private final BookRepository books;
    private final LoanRepository loans;
    private final ReservationRepository reservations;
    private final NotificationService notifications;
    private final Clock clock;

    public CirculationService(BookRepository books, LoanRepository loans, ReservationRepository reservations,
                              NotificationService notifications, Clock clock) {
        this.books = books;
        this.loans = loans;
        this.reservations = reservations;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ---- Reservations -------------------------------------------------------------------

    /**
     * Holds a free copy for the member (status READY), or adds them to the hold queue
     * (status WAITING) when every copy is out.
     */
    public Reservation reserve(Member member, Book book) {
        expireReadyHolds();
        if (reservations.findActive(member, book).isPresent()) {
            throw new LibraryException("You already have a reservation for \"" + book.getTitle() + "\".");
        }
        if (openLoans(member).stream().anyMatch(l -> l.getBook().equals(book))) {
            throw new LibraryException("You already have \"" + book.getTitle() + "\" on loan.");
        }
        if (holdsFor(member).size() >= MAX_ACTIVE_HOLDS) {
            throw new LibraryException("You have reached the limit of " + MAX_ACTIVE_HOLDS
                    + " active reservations.");
        }
        Reservation reservation = new Reservation(reservations.nextId(), member, book, now());
        List<BookCopy> free = book.availableCopies();
        if (free.isEmpty()) {
            book.getHoldQueue().enqueue(reservation);
        } else {
            BookCopy copy = free.get(0);
            copy.reserve(member.getMemberId());
            reservation.markReady(copy, now());
            books.saveCopy(copy);
        }
        reservations.save(reservation);
        return reservation;
    }

    /** Cancels a waiting or ready reservation; a released copy goes to the next member in line. */
    public void cancelReservation(Reservation reservation) {
        ReservationStatus before = reservation.getStatus();
        reservation.cancel();
        if (before == ReservationStatus.WAITING) {
            reservation.getBook().getHoldQueue().remove(reservation);
        } else {
            BookCopy copy = reservation.getCopy();
            copy.release();
            promoteNextHold(copy);
            books.saveCopy(copy);
        }
        reservations.save(reservation);
    }

    /** Expires ready holds not collected within the pickup window and passes the copy on. */
    public List<Reservation> expireReadyHolds() {
        LocalDate today = today();
        List<Reservation> expired = new ArrayList<>();
        for (Reservation r : reservations.findByStatus(ReservationStatus.READY)) {
            if (r.isPickupOverdue(today)) {
                r.expire();
                BookCopy copy = r.getCopy();
                copy.release();
                promoteNextHold(copy);
                books.saveCopy(copy);
                reservations.save(r);
                expired.add(r);
            }
        }
        return expired;
    }

    // ---- Checkout and return ------------------------------------------------------------

    /** Lends a copy to a member, enforcing the borrowing limit. */
    public Loan checkOut(Member member, BookCopy copy) {
        expireReadyHolds();
        if (openLoans(member).size() >= member.getBorrowLimit()) {
            throw new LibraryException(member.getName() + " has reached the borrowing limit of "
                    + member.getBorrowLimit() + " loans.");
        }
        Optional<Reservation> own = reservations.findActive(member, copy.getBook());
        copy.checkOut(member.getMemberId());
        own.ifPresent(r -> settleReservationOnCheckout(r, copy));
        Loan loan = new Loan(loans.nextId(), copy, member, today());
        books.saveCopy(copy);
        loans.save(loan);
        return loan;
    }

    private void settleReservationOnCheckout(Reservation r, BookCopy lentCopy) {
        if (r.getStatus() == ReservationStatus.READY) {
            r.fulfill();
            BookCopy held = r.getCopy();
            if (!held.equals(lentCopy)) {
                // The member took a different copy; release the one that was held for them.
                held.release();
                promoteNextHold(held);
                books.saveCopy(held);
            }
        } else {
            r.getBook().getHoldQueue().remove(r);
            r.cancel();
        }
        reservations.save(r);
    }

    /** Reverses a checkout made moments ago (used by undo). */
    public void undoCheckOut(Loan loan) {
        BookCopy copy = loan.getCopy();
        if (!loan.isOpen() || loan.getRenewals() > 0 || !"On Loan".equals(copy.getState().name())) {
            throw new LibraryException("The checkout of \"" + loan.getBook().getTitle()
                    + "\" can no longer be undone.");
        }
        copy.checkIn();
        Optional<Reservation> fulfilled = reservations.findByMember(loan.getMember()).stream()
                .filter(r -> r.getStatus() == ReservationStatus.FULFILLED && copy.equals(r.getCopy()))
                .reduce((first, second) -> second);
        if (fulfilled.isPresent()) {
            fulfilled.get().reopen();
            copy.reserve(loan.getMember().getMemberId());
            reservations.save(fulfilled.get());
        } else {
            promoteNextHold(copy);
        }
        books.saveCopy(copy);
        loans.delete(loan);
    }

    /**
     * Accepts a returned copy: closes the loan with any late fee, then holds the copy for the
     * next member in the queue and notifies them.
     */
    public Loan checkIn(String copyId) {
        BookCopy copy = requireCopy(copyId);
        Loan loan = loans.findOpenByCopy(copy)
                .orElseThrow(() -> new LibraryException("Copy " + copy.getCopyId() + " is not on loan."));
        LocalDate today = today();
        BigDecimal fee = loan.calculateFee(loan.getMember().getType().feePolicy(), today);
        copy.checkIn();
        loan.close(today, fee);
        promoteNextHold(copy);
        books.saveCopy(copy);
        loans.save(loan);
        return loan;
    }

    /** Reopens a loan whose return is being undone; refused once the copy was passed on. */
    public void undoCheckIn(Loan loan, boolean wasOverdue) {
        BookCopy copy = loan.getCopy();
        if (!copy.isAvailable()) {
            throw new LibraryException("The return of \"" + loan.getBook().getTitle()
                    + "\" can no longer be undone: the copy is now " + copy.getState().name().toLowerCase()
                    + (copy.getReservedFor() != null ? " for a waiting member who was already notified." : "."));
        }
        copy.checkOut(loan.getMember().getMemberId());
        if (wasOverdue) {
            copy.markOverdue();
        }
        loan.reopen();
        books.saveCopy(copy);
        loans.save(loan);
    }

    // ---- Renewals and overdue loans -----------------------------------------------------

    /** Extends a loan unless it is overdue, renewed twice already, or someone is waiting. */
    public Loan renew(Loan loan) {
        String title = loan.getBook().getTitle();
        if (!loan.isOpen()) {
            throw new LibraryException("\"" + title + "\" has already been returned.");
        }
        if (loan.isOverdue(today()) || "Overdue".equals(loan.getCopy().getState().name())) {
            throw new LibraryException("\"" + title + "\" is overdue and cannot be renewed; please return it.");
        }
        if (!loan.getBook().getHoldQueue().isEmpty()) {
            throw new LibraryException("Another member is waiting for \"" + title + "\", so it cannot be renewed.");
        }
        loan.renew();
        loans.save(loan);
        return loan;
    }

    public void undoRenew(Loan loan, LocalDate previousDueDate) {
        loan.undoRenew(previousDueDate);
        loans.save(loan);
    }

    /**
     * Open loans past their due date, oldest due first. Copies not yet flagged are moved to
     * the Overdue state and their borrowers notified once.
     */
    public List<Loan> findOverdue() {
        LocalDate today = today();
        List<Loan> overdue = new ArrayList<>();
        for (Loan loan : loans.findOpen()) {
            if (!loan.isOverdue(today)) {
                continue;
            }
            overdue.add(loan);
            BookCopy copy = loan.getCopy();
            if (!"Overdue".equals(copy.getState().name())) {
                copy.markOverdue();
                books.saveCopy(copy);
                notifications.publish(new LibraryEvent(EventType.OVERDUE, loan.getMember().getMemberId(),
                        "\"" + loan.getBook().getTitle() + "\" was due on " + loan.getDueDate()
                                + ". Please return it to avoid further fees."));
            }
        }
        overdue.sort(Comparator.comparing(Loan::getDueDate));
        return overdue;
    }

    // ---- Queries -------------------------------------------------------------------------

    public List<Loan> loansFor(Member member) {
        return openLoans(member);
    }

    /** Every loan the member has had, most recent first. */
    public List<Loan> loanHistory(Member member) {
        return loans.findByMember(member).stream()
                .sorted(Comparator.comparing(Loan::getCheckoutDate).reversed())
                .toList();
    }

    /** Waiting and ready reservations. */
    public List<Reservation> holdsFor(Member member) {
        return reservations.findByMember(member).stream().filter(Reservation::isActive).toList();
    }

    /** 1-based position in the hold queue, or 0 if the reservation is not waiting. */
    public int queuePosition(Reservation reservation) {
        return reservation.getStatus() == ReservationStatus.WAITING
                ? reservation.getBook().getHoldQueue().positionOf(reservation.getMember())
                : 0;
    }

    /** Fees charged on returned loans plus fees accruing on loans still out. */
    public BigDecimal feesOwed(Member member) {
        LocalDate today = today();
        BigDecimal total = BigDecimal.ZERO;
        for (Loan loan : loans.findByMember(member)) {
            total = total.add(loan.isOpen()
                    ? loan.calculateFee(member.getType().feePolicy(), today)
                    : loan.getFee());
        }
        return total;
    }

    public Loan requireLoan(String loanId) {
        return loans.findById(loanId).orElseThrow(() -> new LibraryException("No loan with ID " + loanId + "."));
    }

    public BookCopy requireCopy(String copyId) {
        return books.findCopy(copyId).orElseThrow(() -> new LibraryException("No copy with ID " + copyId + "."));
    }

    public Reservation requireReservation(String reservationId) {
        return reservations.findById(reservationId)
                .orElseThrow(() -> new LibraryException("No reservation with ID " + reservationId + "."));
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    // ---- Helpers -------------------------------------------------------------------------

    private List<Loan> openLoans(Member member) {
        return loans.findByMember(member).stream().filter(Loan::isOpen).toList();
    }

    /** Holds an available copy for the next waiting member, if any, and notifies them. */
    private void promoteNextHold(BookCopy copy) {
        Reservation next = copy.getBook().getHoldQueue().nextInLine();
        if (next == null) {
            return;
        }
        copy.reserve(next.getMember().getMemberId());
        next.markReady(copy, now());
        reservations.save(next);
        notifications.publish(new LibraryEvent(EventType.HOLD_READY, next.getMember().getMemberId(),
                "\"" + copy.getBook().getTitle() + "\" is ready for pickup (copy " + copy.getCopyId()
                        + "). It will be held until " + next.pickupDeadline() + "."));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
