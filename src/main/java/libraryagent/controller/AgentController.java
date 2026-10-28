package libraryagent.controller;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import libraryagent.agent.AgentResponse;
import libraryagent.agent.Planner;
import libraryagent.agent.llm.TokenUsage;
import libraryagent.command.AddToReadingListCommand;
import libraryagent.command.CheckOutCommand;
import libraryagent.command.CommandResult;
import libraryagent.command.MoveInReadingListCommand;
import libraryagent.command.RemoveFromReadingListCommand;
import libraryagent.command.RenewLoanCommand;
import libraryagent.command.ReserveBookCommand;
import libraryagent.command.ReturnBookCommand;
import libraryagent.domain.Book;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Loan;
import libraryagent.domain.Member;
import libraryagent.domain.MemberType;
import libraryagent.domain.ReadingList;
import libraryagent.domain.ReadingPreferences;
import libraryagent.domain.Reservation;
import libraryagent.notification.LibraryEventListener;
import libraryagent.service.Availability;
import libraryagent.service.BookQuery;
import libraryagent.service.CirculationService;

/**
 * Facade: the single entry point used by both the GUI and the CLI. It checks who is allowed
 * to do what, routes undoable actions through the session's command history, and hides the
 * individual services from the views.
 */
public class AgentController {

    private final LibraryServices services;
    private final SessionContext session;
    private final Planner planner;

    /** A controller without the AI assistant (no API key configured). */
    public AgentController(LibraryServices services, SessionContext session) {
        this(services, session, null);
    }

    public AgentController(LibraryServices services, SessionContext session, Planner planner) {
        this.services = services;
        this.session = session;
        this.planner = planner;
    }

    // ---- AI assistant --------------------------------------------------------------------

    public boolean assistantAvailable() {
        return planner != null;
    }

    /** Tokens the AI assistant has used since the app started. */
    public TokenUsage assistantUsage() {
        return planner == null ? TokenUsage.ZERO : planner.usage();
    }

    /** Sends a plain-language message to the agent for the current session. */
    public AgentResponse chat(String message) {
        if (planner == null) {
            return AgentResponse.failure(
                    "The AI assistant is turned off because no API key is configured. You can still use every "
                            + "feature through the menus or commands.");
        }
        if (message == null || message.isBlank()) {
            return AgentResponse.failure("Please type a message.");
        }
        return planner.run(session.getSessionId(), session.getCurrentMember().orElse(null),
                session::getHistory, session::login, message.strip());
    }

    // ---- Session -------------------------------------------------------------------------

    /** Logs a member in by ID and releases any ready holds that have expired. */
    public Member login(String memberId) {
        Member member = services.members().requireMember(memberId);
        session.login(member);
        resetConversation();
        services.circulation().expireReadyHolds();
        return member;
    }

    public void logout() {
        session.logout();
        resetConversation();
    }

    private void resetConversation() {
        if (planner != null) {
            planner.resetConversation(session.getSessionId());
        }
    }

    public Optional<Member> currentMember() {
        return session.getCurrentMember();
    }

    public SessionContext session() {
        return session;
    }

    public void subscribe(LibraryEventListener listener) {
        services.notifications().subscribe(listener);
    }

    // ---- Catalogue -----------------------------------------------------------------------

    public List<Book> search(BookQuery query) {
        return services.catalog().search(query);
    }

    /** Plain keyword search, as typed into a search bar; works without the AI assistant. */
    public List<Book> searchKeywords(String text) {
        return services.catalog().search(BookQuery.ofKeywords(text));
    }

    public Book bookDetails(String isbn) {
        return services.catalog().requireBook(isbn);
    }

    public Availability availability(Book book) {
        return services.catalog().availability(book);
    }

    public List<String> subgenres() {
        return services.catalog().subgenreNames();
    }

    /** Librarians only: adds a new title with copies, filed under the given subgenres. */
    public Book addBook(Book book, int copies, List<String> subgenres) {
        requireLibrarian("add books");
        return services.catalog().addBook(book, copies, subgenres);
    }

    // ---- Members -------------------------------------------------------------------------

    /** Registers a new member; no login is needed. */
    public Member register(String name, String email, MemberType type) {
        return services.members().register(name, email, type);
    }

    public void updatePreferences(String memberId, List<String> genres, List<String> authors, int maxPages) {
        Member member = requireSelfOrLibrarian(memberId);
        services.members().updatePreferences(member,
                new ReadingPreferences(genres, authors, maxPages, member.getPreferences().isTrackingEnabled()));
    }

    // ---- Circulation (undoable actions go through the session's command history) -------

    public CommandResult reserve(String memberId, String isbn) {
        return attempt(() -> {
            Member member = requireSelfOrLibrarian(memberId);
            Book book = services.catalog().requireBook(isbn);
            return session.getHistory().run(new ReserveBookCommand(services.circulation(), member, book));
        });
    }

    /** Cancels a waiting or ready reservation. Not undoable: a cancelled place in the queue is gone. */
    public CommandResult cancelReservation(String reservationId) {
        return attempt(() -> {
            Reservation r = services.circulation().requireReservation(reservationId);
            requireSelfOrLibrarian(r.getMember().getMemberId());
            services.circulation().cancelReservation(r);
            return CommandResult.ok("Cancelled your reservation of \"" + r.getBook().getTitle() + "\".");
        });
    }

    /** Librarians only: lends a copy at the desk. */
    public CommandResult checkOut(String memberId, String copyId) {
        return attempt(() -> {
            requireLibrarian("check out books");
            Member member = services.members().requireMember(memberId);
            CirculationService circulation = services.circulation();
            return session.getHistory().run(new CheckOutCommand(circulation, member, circulation.requireCopy(copyId)));
        });
    }

    /** Librarians only: checks a copy in at the desk. */
    public CommandResult checkIn(String copyId) {
        return attempt(() -> {
            requireLibrarian("check in books");
            return session.getHistory().run(new ReturnBookCommand(services.circulation(), copyId));
        });
    }

    public CommandResult renew(String loanId) {
        return attempt(() -> {
            Loan loan = services.circulation().requireLoan(loanId);
            requireSelfOrLibrarian(loan.getMember().getMemberId());
            return session.getHistory().run(new RenewLoanCommand(services.circulation(), loan));
        });
    }

    /** Librarians only: overdue loans, flagging newly late copies and notifying borrowers. */
    public List<Loan> findOverdue() {
        requireLibrarian("view overdue loans");
        return services.circulation().findOverdue();
    }

    public AccountSummary accountSummary(String memberId) {
        Member member = requireSelfOrLibrarian(memberId);
        CirculationService circulation = services.circulation();
        List<HoldStatus> holds = circulation.holdsFor(member).stream()
                .map(r -> new HoldStatus(r, circulation.queuePosition(r)))
                .toList();
        return new AccountSummary(member, circulation.loansFor(member), holds, circulation.feesOwed(member),
                services.readingLists().listsFor(member), circulation.today());
    }

    /** Every loan the member has had, most recent first. */
    public List<Loan> loanHistory(String memberId) {
        return services.circulation().loanHistory(requireSelfOrLibrarian(memberId));
    }

    // ---- Reading lists (for the logged-in member) ----------------------------------------

    public List<ReadingList> readingLists() {
        return services.readingLists().listsFor(requireLogin());
    }

    public ReadingList readingList(String name) {
        return services.readingLists().requireList(requireLogin(), name);
    }

    public CommandResult createList(String name) {
        return attempt(() -> {
            ReadingList list = services.readingLists().createList(requireLogin(), name);
            return CommandResult.ok("Created the reading list \"" + list.getName() + "\".");
        });
    }

    public CommandResult addToList(String listName, String isbn) {
        return attempt(() -> {
            ReadingList list = readingList(listName);
            Book book = services.catalog().requireBook(isbn);
            return session.getHistory().run(new AddToReadingListCommand(services.readingLists(), list, book));
        });
    }

    public CommandResult removeFromList(String listName, String isbn) {
        return attempt(() -> {
            ReadingList list = readingList(listName);
            Book book = services.catalog().requireBook(isbn);
            return session.getHistory().run(new RemoveFromReadingListCommand(services.readingLists(), list, book));
        });
    }

    /** Moves a book to a 1-based position, as shown to users. */
    public CommandResult moveInList(String listName, String isbn, int position) {
        return attempt(() -> {
            ReadingList list = readingList(listName);
            Book book = services.catalog().requireBook(isbn);
            return session.getHistory().run(
                    new MoveInReadingListCommand(services.readingLists(), list, book, position - 1));
        });
    }

    // ---- Undo ----------------------------------------------------------------------------

    /** Undoes the most recent action in this session. */
    public String undoLast() {
        return session.getHistory().undoLast();
    }

    // ---- Permission helpers --------------------------------------------------------------

    private Member requireLogin() {
        return session.getCurrentMember()
                .orElseThrow(() -> new LibraryException("Please log in first."));
    }

    private void requireLibrarian(String action) {
        if (!requireLogin().isLibrarian()) {
            throw new LibraryException("Only librarians can " + action + ".");
        }
    }

    /** The member a request is about, if the current user is that member or a librarian. */
    private Member requireSelfOrLibrarian(String memberId) {
        Member current = requireLogin();
        if (current.getMemberId().equalsIgnoreCase(memberId.strip())) {
            return current;
        }
        if (!current.isLibrarian()) {
            throw new LibraryException("You can only do that for your own account.");
        }
        return services.members().requireMember(memberId);
    }

    private static CommandResult attempt(Supplier<CommandResult> action) {
        try {
            return action.get();
        } catch (LibraryException e) {
            return CommandResult.failure(e.getMessage());
        }
    }
}
