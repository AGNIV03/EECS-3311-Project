package libraryagent.ui.cli;

import java.math.BigDecimal;
import java.util.List;
import libraryagent.controller.AccountSummary;
import libraryagent.controller.HoldStatus;
import libraryagent.domain.Book;
import libraryagent.domain.Loan;
import libraryagent.domain.ReadingList;
import libraryagent.domain.Reservation;
import libraryagent.domain.ReservationStatus;
import libraryagent.domain.copy.BookCopy;
import libraryagent.service.Availability;

/** Formats domain objects as plain text for the terminal. */
final class CliPresenter {

    private CliPresenter() {
    }

    static String bookLine(int number, Book book, Availability availability) {
        String status = availability.isAvailable()
                ? availability.availableCopies() + " of " + availability.totalCopies() + " available"
                : "all " + availability.totalCopies() + " out" + (availability.queueLength() > 0
                        ? ", " + availability.queueLength() + " waiting" : "");
        return String.format("%2d. %s | %s by %s, %d pages | %s",
                number, book.getIsbn(), book.getTitle(), book.getAuthor(), book.getPageCount(), status);
    }

    static String bookDetails(Book book, Availability availability) {
        StringBuilder sb = new StringBuilder();
        sb.append(book.getTitle()).append(" by ").append(book.getAuthor()).append('\n');
        sb.append("  ISBN: ").append(book.getIsbn()).append(" | ").append(book.getPageCount()).append(" pages")
                .append(" | Genres: ").append(String.join(", ", book.getGenres())).append('\n');
        sb.append("  ").append(book.getDescription()).append('\n');
        sb.append("  Copies:");
        for (BookCopy copy : book.getCopies()) {
            sb.append(' ').append(copy.getCopyId()).append(" (").append(copy.getState().name()).append(')');
        }
        if (availability.queueLength() > 0) {
            sb.append("\n  Hold queue: ").append(availability.queueLength()).append(" waiting");
        }
        return sb.toString();
    }

    static String loanLine(Loan loan, java.time.LocalDate today) {
        String status = loan.isOverdue(today) ? "OVERDUE since " + loan.getDueDate() : "due " + loan.getDueDate();
        return "  " + loan.getLoanId() + " | " + loan.getBook().getTitle() + " (copy " + loan.getCopy().getCopyId()
                + ") | " + status + " | renewals " + loan.getRenewals() + "/" + Loan.MAX_RENEWALS;
    }

    static String holdLine(HoldStatus hold) {
        Reservation r = hold.reservation();
        String status = r.getStatus() == ReservationStatus.READY
                ? "READY: copy " + r.getCopy().getCopyId() + " held until " + r.pickupDeadline()
                : "waiting, position " + hold.queuePosition();
        return "  " + r.getReservationId() + " | " + r.getBook().getTitle() + " | " + status;
    }

    static String account(AccountSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Account: ").append(summary.member().getName()).append(" (").append(summary.member().getMemberId())
                .append(", ").append(summary.member().getType().name().toLowerCase())
                .append(", limit ").append(summary.member().getBorrowLimit()).append(" loans)\n");
        sb.append("Loans (").append(summary.loans().size()).append("):\n");
        if (summary.loans().isEmpty()) {
            sb.append("  none\n");
        }
        summary.loans().forEach(l -> sb.append(loanLine(l, summary.today())).append('\n'));
        sb.append("Holds (").append(summary.holds().size()).append("):\n");
        if (summary.holds().isEmpty()) {
            sb.append("  none\n");
        }
        summary.holds().forEach(h -> sb.append(holdLine(h)).append('\n'));
        sb.append("Fees owed: $").append(money(summary.feesOwed())).append('\n');
        sb.append("Reading lists: ");
        sb.append(summary.readingLists().isEmpty() ? "none"
                : String.join(", ", summary.readingLists().stream()
                        .map(l -> l.getName() + " (" + l.getBooks().size() + ")").toList()));
        return sb.toString();
    }

    static String readingList(ReadingList list) {
        StringBuilder sb = new StringBuilder(list.getName()).append(':');
        List<Book> books = list.getBooks();
        if (books.isEmpty()) {
            sb.append(" (empty)");
        }
        for (int i = 0; i < books.size(); i++) {
            sb.append("\n  ").append(i + 1).append(". ").append(books.get(i).getTitle())
                    .append(" (").append(books.get(i).getIsbn()).append(')');
        }
        return sb.toString();
    }

    static String money(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    static final String HELP = """
            Commands (anything else is sent to the AI assistant):
              login <memberId>              log in (e.g. login M001)
              logout | whoami
              register                      create an account (asks for your details)
              search <keywords>             keyword search of the catalogue
              book <isbn>                   details and copies of a book
              genres                        list the subgenres
              reserve <isbn>                reserve a book or join its hold queue
              cancel <reservationId>        cancel a reservation
              renew <loanId>                renew a loan
              account                       loans, holds, fees and reading lists
              history                       every book you have borrowed
              lists                         your reading lists
              list create "<name>"          create a reading list
              list show "<name>"            show a reading list
              list add "<name>" <isbn>      add a book to a list
              list remove "<name>" <isbn>   remove a book from a list
              list move "<name>" <isbn> <n> move a book to position n
              undo                          undo your last action
              rate <isbn> liked|neutral|disliked [comment]
                                            rate a book you borrowed
              tracking on|off               turn reading-feedback tracking on or off
              recommend                     books picked from your preferences and ratings
              help | exit
            Librarian commands:
              checkout <memberId> <copyId>  lend a copy
              return <copyId>               check a copy in
              overdue                       list overdue loans and notify borrowers
              addbook                       add a book (asks for its details)""";
}
