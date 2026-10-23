package libraryagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
import libraryagent.persistence.DataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Seed state used here: Sam (M002, student) has the only copy of The Hobbit, due in 11 days;
 * Alex (M001, standard) has Dune out and 9 days overdue.
 */
class CirculationServiceTest {

    private static final String HOBBIT = "9781000000001";
    private static final String DUNE = "9781000000006";
    private static final String PERSUASION = "9781000000018";

    private ServiceTestSupport.MutableClock clock;
    private DataStore store;
    private CirculationService circulation;
    private final List<LibraryEvent> events = new ArrayList<>();
    private Member alex;
    private Member sam;

    @BeforeEach
    void setUp() {
        clock = ServiceTestSupport.clock();
        store = ServiceTestSupport.seededStore(clock);
        NotificationService notifications = new NotificationService();
        notifications.subscribe(events::add);
        circulation = new CirculationService(store.books(), store.loans(), store.reservations(),
                notifications, clock);
        alex = store.members().findById("M001").orElseThrow();
        sam = store.members().findById("M002").orElseThrow();
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private Book book(String isbn) {
        return store.books().findById(isbn).orElseThrow();
    }

    private Loan openLoan(Member member, String isbn) {
        return circulation.loansFor(member).stream()
                .filter(l -> l.getBook().getIsbn().equals(isbn)).findFirst().orElseThrow();
    }

    // ---- Reservations --------------------------------------------------------------------

    @Test
    void reservingAnAvailableBookHoldsACopyImmediately() {
        Reservation r = circulation.reserve(alex, book(PERSUASION));
        assertEquals(ReservationStatus.READY, r.getStatus());
        assertEquals("Reserved", r.getCopy().getState().name());
        assertEquals("M001", r.getCopy().getReservedFor());
    }

    @Test
    void reservingAnUnavailableBookJoinsTheQueue() {
        Reservation r = circulation.reserve(alex, book(HOBBIT));
        assertEquals(ReservationStatus.WAITING, r.getStatus());
        assertEquals(1, circulation.queuePosition(r));
    }

    @Test
    void duplicateReservationsAndReservingYourOwnLoanAreRejected() {
        circulation.reserve(alex, book(HOBBIT));
        assertThrows(LibraryException.class, () -> circulation.reserve(alex, book(HOBBIT)));
        LibraryException e = assertThrows(LibraryException.class, () -> circulation.reserve(sam, book(HOBBIT)));
        assertEquals("You already have \"The Hobbit\" on loan.", e.getMessage());
    }

    @Test
    void activeHoldLimitIsEnforced() {
        List<Book> all = store.books().findAll();
        int made = 0;
        for (Book b : all) {
            if (made == CirculationService.MAX_ACTIVE_HOLDS) {
                break;
            }
            if (!b.getIsbn().equals(DUNE)) {
                circulation.reserve(alex, b);
                made++;
            }
        }
        LibraryException e = assertThrows(LibraryException.class,
                () -> circulation.reserve(alex, book("9781000000040")));
        assertEquals("You have reached the limit of 5 active reservations.", e.getMessage());
    }

    @Test
    void cancellingAReadyHoldPassesTheCopyToTheNextMember() {
        Reservation alexHold = circulation.reserve(alex, book(PERSUASION));
        Member jo = new MemberService(store.members(), store.genres())
                .register("Jo", "jo@example.com", libraryagent.domain.MemberType.STANDARD);
        Reservation joHold = circulation.reserve(jo, book(PERSUASION));
        assertEquals(ReservationStatus.WAITING, joHold.getStatus());

        circulation.cancelReservation(alexHold);

        assertEquals(ReservationStatus.CANCELLED, alexHold.getStatus());
        assertEquals(ReservationStatus.READY, joHold.getStatus());
        assertEquals(jo.getMemberId(), joHold.getCopy().getReservedFor());
        assertEquals(EventType.HOLD_READY, events.get(0).type());
        assertEquals(jo.getMemberId(), events.get(0).memberId());
    }

    @Test
    void cancellingAWaitingHoldMovesOthersUp() {
        Reservation alexHold = circulation.reserve(alex, book(HOBBIT));
        Member jo = new MemberService(store.members(), store.genres())
                .register("Jo", "jo@example.com", libraryagent.domain.MemberType.STANDARD);
        Reservation joHold = circulation.reserve(jo, book(HOBBIT));
        assertEquals(2, circulation.queuePosition(joHold));
        circulation.cancelReservation(alexHold);
        assertEquals(1, circulation.queuePosition(joHold));
    }

    @Test
    void readyHoldsExpireAfterThreeDays() {
        Reservation r = circulation.reserve(alex, book(PERSUASION));
        clock.advanceDays(3);
        assertTrue(circulation.expireReadyHolds().isEmpty());
        clock.advanceDays(1);
        assertEquals(List.of(r), circulation.expireReadyHolds());
        assertEquals(ReservationStatus.EXPIRED, r.getStatus());
        assertTrue(r.getCopy().isAvailable());
    }

    // ---- Checkout ------------------------------------------------------------------------

    @Test
    void checkoutCreatesLoanDueInThreeWeeks() {
        BookCopy copy = book(PERSUASION).availableCopies().get(0);
        Loan loan = circulation.checkOut(alex, copy);
        assertEquals(ServiceTestSupport.START.plusDays(21), loan.getDueDate());
        assertEquals("On Loan", copy.getState().name());
    }

    @Test
    void borrowingLimitIsEnforced() {
        List<BookCopy> free = store.books().findAll().stream()
                .filter(b -> !b.availableCopies().isEmpty())
                .map(b -> b.availableCopies().get(0))
                .toList();
        for (int i = 0; i < 4; i++) {
            circulation.checkOut(alex, free.get(i));
        }
        LibraryException e = assertThrows(LibraryException.class, () -> circulation.checkOut(alex, free.get(4)));
        assertEquals("Alex Morgan has reached the borrowing limit of 5 loans.", e.getMessage());
    }

    @Test
    void copyHeldForAnotherMemberCannotBeLent() {
        Reservation r = circulation.reserve(alex, book(PERSUASION));
        LibraryException e = assertThrows(LibraryException.class, () -> circulation.checkOut(sam, r.getCopy()));
        assertEquals("Copy " + r.getCopy().getCopyId() + " is being held for another member.", e.getMessage());
    }

    @Test
    void collectingAHoldFulfilsTheReservation() {
        Reservation r = circulation.reserve(alex, book(PERSUASION));
        circulation.checkOut(alex, r.getCopy());
        assertEquals(ReservationStatus.FULFILLED, r.getStatus());
        assertTrue(circulation.holdsFor(alex).isEmpty());
    }

    @Test
    void undoCheckoutRestoresTheHold() {
        Reservation r = circulation.reserve(alex, book(PERSUASION));
        Loan loan = circulation.checkOut(alex, r.getCopy());
        circulation.undoCheckOut(loan);
        assertEquals(ReservationStatus.READY, r.getStatus());
        assertEquals("Reserved", r.getCopy().getState().name());
        assertTrue(store.loans().findById(loan.getLoanId()).isEmpty());
    }

    // ---- Return --------------------------------------------------------------------------

    @Test
    void returningAnOverdueBookChargesTheMembersPolicy() {
        Loan dune = openLoan(alex, DUNE);
        Loan closed = circulation.checkIn(dune.getCopy().getCopyId());
        assertEquals(new BigDecimal("2.25"), closed.getFee());
        assertFalse(closed.isOpen());
        assertTrue(dune.getCopy().isAvailable());
    }

    @Test
    void returningABookWithAQueueHoldsItForTheNextMemberAndNotifiesThem() {
        Reservation alexHold = circulation.reserve(alex, book(HOBBIT));
        BookCopy copy = openLoan(sam, HOBBIT).getCopy();

        circulation.checkIn(copy.getCopyId());

        assertEquals("Reserved", copy.getState().name());
        assertEquals(ReservationStatus.READY, alexHold.getStatus());
        assertEquals(1, events.size());
        assertEquals(EventType.HOLD_READY, events.get(0).type());
        assertEquals("M001", events.get(0).memberId());
        assertTrue(events.get(0).message().contains("The Hobbit"));
    }

    @Test
    void returningACopyThatIsNotOnLoanIsRejected() {
        String freeCopy = book(PERSUASION).availableCopies().get(0).getCopyId();
        LibraryException e = assertThrows(LibraryException.class, () -> circulation.checkIn(freeCopy));
        assertEquals("Copy " + freeCopy + " is not on loan.", e.getMessage());
        assertThrows(LibraryException.class, () -> circulation.checkIn("C9999"));
    }

    @Test
    void undoReturnReopensLoanUnlessHoldWasPromoted() {
        Loan dune = openLoan(alex, DUNE);
        circulation.checkIn(dune.getCopy().getCopyId());
        circulation.undoCheckIn(dune, true);
        assertTrue(dune.isOpen());
        assertEquals("Overdue", dune.getCopy().getState().name());

        circulation.reserve(alex, book(HOBBIT));
        Loan hobbit = openLoan(sam, HOBBIT);
        circulation.checkIn(hobbit.getCopy().getCopyId());
        LibraryException e = assertThrows(LibraryException.class, () -> circulation.undoCheckIn(hobbit, false));
        assertTrue(e.getMessage().contains("already notified"));
    }

    // ---- Renewal and overdue ---------------------------------------------------------------

    @Test
    void renewalExtendsDueDate() {
        Loan hobbit = openLoan(sam, HOBBIT);
        var before = hobbit.getDueDate();
        circulation.renew(hobbit);
        assertEquals(before.plusDays(21), hobbit.getDueDate());
        circulation.undoRenew(hobbit, before);
        assertEquals(before, hobbit.getDueDate());
    }

    @Test
    void renewalIsRefusedWhenSomeoneIsWaiting() {
        circulation.reserve(alex, book(HOBBIT));
        LibraryException e = assertThrows(LibraryException.class, () -> circulation.renew(openLoan(sam, HOBBIT)));
        assertEquals("Another member is waiting for \"The Hobbit\", so it cannot be renewed.", e.getMessage());
    }

    @Test
    void renewalIsRefusedWhenOverdue() {
        LibraryException e = assertThrows(LibraryException.class, () -> circulation.renew(openLoan(alex, DUNE)));
        assertTrue(e.getMessage().contains("overdue"));
    }

    @Test
    void findOverdueFlagsNewlyLateLoansAndNotifiesOnce() {
        assertEquals(1, circulation.findOverdue().size());
        assertTrue(events.isEmpty(), "seeded overdue copy was already flagged");

        clock.advanceDays(12);
        List<Loan> overdue = circulation.findOverdue();
        assertEquals(2, overdue.size());
        assertEquals(DUNE, overdue.get(0).getBook().getIsbn());
        assertEquals(1, events.size());
        assertEquals(EventType.OVERDUE, events.get(0).type());
        assertEquals("M002", events.get(0).memberId());

        circulation.findOverdue();
        assertEquals(1, events.size());
    }

    @Test
    void feesOwedIncludesAccruingFees() {
        assertEquals(new BigDecimal("2.25"), circulation.feesOwed(alex));
        assertEquals(new BigDecimal("0.00"), circulation.feesOwed(sam).setScale(2));
    }
}
