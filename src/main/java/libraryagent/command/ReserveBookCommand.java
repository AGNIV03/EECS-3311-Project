package libraryagent.command;

import libraryagent.domain.Book;
import libraryagent.domain.Member;
import libraryagent.domain.Reservation;
import libraryagent.domain.ReservationStatus;
import libraryagent.service.CirculationService;

/** Reserves a book: holds a free copy or joins the hold queue. Undo cancels the reservation. */
public class ReserveBookCommand implements Command {

    private final CirculationService circulation;
    private final Member member;
    private final Book book;
    private Reservation reservation;

    public ReserveBookCommand(CirculationService circulation, Member member, Book book) {
        this.circulation = circulation;
        this.member = member;
        this.book = book;
    }

    @Override
    public CommandResult execute() {
        reservation = circulation.reserve(member, book);
        if (reservation.getStatus() == ReservationStatus.READY) {
            return CommandResult.ok("Reserved \"" + book.getTitle() + "\". Copy " + reservation.getCopy().getCopyId()
                    + " is on the hold shelf for you until " + reservation.pickupDeadline() + ".");
        }
        return CommandResult.ok("No copies of \"" + book.getTitle() + "\" are free right now. You are number "
                + circulation.queuePosition(reservation) + " in the hold queue.");
    }

    @Override
    public void undo() {
        circulation.cancelReservation(reservation);
    }

    @Override
    public String describe() {
        return "reservation of \"" + book.getTitle() + "\"";
    }

    public Reservation getReservation() {
        return reservation;
    }
}
