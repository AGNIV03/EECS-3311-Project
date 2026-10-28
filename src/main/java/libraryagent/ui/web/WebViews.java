package libraryagent.ui.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import libraryagent.agent.AgentResponse;
import libraryagent.command.CommandResult;
import libraryagent.controller.AccountSummary;
import libraryagent.controller.AgentController;
import libraryagent.controller.HoldStatus;
import libraryagent.domain.Book;
import libraryagent.domain.Loan;
import libraryagent.domain.Member;
import libraryagent.domain.ReadingList;
import libraryagent.domain.Reservation;
import libraryagent.domain.ReservationStatus;
import libraryagent.service.Availability;

/** JSON-ready views of domain objects for the web API. */
final class WebViews {

    private WebViews() {
    }

    static Map<String, Object> member(Member m) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("memberId", m.getMemberId());
        view.put("name", m.getName());
        view.put("type", m.getType().name().toLowerCase());
        view.put("librarian", m.isLibrarian());
        view.put("borrowLimit", m.getBorrowLimit());
        view.put("favoriteGenres", m.getPreferences().getFavoriteGenres());
        view.put("favoriteAuthors", m.getPreferences().getFavoriteAuthors());
        view.put("maxPages", m.getPreferences().getMaxPages());
        view.put("trackingEnabled", m.getPreferences().isTrackingEnabled());
        return view;
    }

    static Map<String, Object> book(Book b, Availability a) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("isbn", b.getIsbn());
        view.put("title", b.getTitle());
        view.put("author", b.getAuthor());
        view.put("pages", b.getPageCount());
        view.put("genres", b.getGenres());
        view.put("availableCopies", a.availableCopies());
        view.put("totalCopies", a.totalCopies());
        view.put("waiting", a.queueLength());
        return view;
    }

    static Map<String, Object> bookDetail(Book b, Availability a) {
        Map<String, Object> view = book(b, a);
        view.put("description", b.getDescription());
        view.put("copies", b.getCopies().stream()
                .map(c -> Map.of("copyId", c.getCopyId(), "status", c.getState().name()))
                .toList());
        return view;
    }

    static List<Map<String, Object>> books(List<Book> books, AgentController c) {
        return books.stream().map(b -> book(b, c.availability(b))).toList();
    }

    static Map<String, Object> loan(Loan l, LocalDate today) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("loanId", l.getLoanId());
        view.put("title", l.getBook().getTitle());
        view.put("isbn", l.getBook().getIsbn());
        view.put("copyId", l.getCopy().getCopyId());
        view.put("memberId", l.getMember().getMemberId());
        view.put("memberName", l.getMember().getName());
        view.put("checkoutDate", l.getCheckoutDate().toString());
        view.put("dueDate", l.getDueDate().toString());
        view.put("returnDate", l.getReturnDate() == null ? null : l.getReturnDate().toString());
        view.put("renewals", l.getRenewals());
        view.put("maxRenewals", Loan.MAX_RENEWALS);
        view.put("overdue", l.isOverdue(today));
        view.put("fee", money(l.isOpen()
                ? l.calculateFee(l.getMember().getType().feePolicy(), today)
                : l.getFee()));
        return view;
    }

    static Map<String, Object> hold(HoldStatus h) {
        Reservation r = h.reservation();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("reservationId", r.getReservationId());
        view.put("title", r.getBook().getTitle());
        view.put("isbn", r.getBook().getIsbn());
        view.put("status", r.getStatus() == ReservationStatus.READY ? "ready" : "waiting");
        view.put("queuePosition", h.queuePosition());
        view.put("copyId", r.getCopy() == null ? null : r.getCopy().getCopyId());
        view.put("pickupBy", r.pickupDeadline() == null ? null : r.pickupDeadline().toString());
        return view;
    }

    static Map<String, Object> readingList(ReadingList list, AgentController c) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", list.getName());
        view.put("books", books(list.getBooks(), c));
        return view;
    }

    static Map<String, Object> account(AccountSummary s, AgentController c) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("member", member(s.member()));
        view.put("loans", s.loans().stream().map(l -> loan(l, s.today())).toList());
        view.put("holds", s.holds().stream().map(WebViews::hold).toList());
        view.put("feesOwed", money(s.feesOwed()));
        view.put("readingLists", s.readingLists().stream().map(l -> readingList(l, c)).toList());
        return view;
    }

    static Map<String, Object> result(CommandResult r) {
        return Map.of("success", r.success(), "message", r.message());
    }

    static Map<String, Object> agent(AgentResponse r, AgentController c) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("text", r.text());
        view.put("books", books(r.books(), c));
        view.put("tools", r.toolsUsed());
        view.put("error", r.error());
        return view;
    }

    static String money(BigDecimal amount) {
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
