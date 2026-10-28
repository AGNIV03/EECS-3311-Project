package libraryagent.ui.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import libraryagent.command.CommandResult;
import libraryagent.controller.AgentController;
import libraryagent.domain.Book;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Member;
import libraryagent.notification.EventType;
import libraryagent.notification.LibraryEvent;
import libraryagent.notification.LibraryEventListener;
import libraryagent.service.Availability;

/**
 * The main window (MVC view root): toolbar with the signed-in member, notices and Undo; the start
 * screen or the tabs; and a status bar. Every action goes through the {@link AgentController}.
 */
final class MainWindow extends BorderPane implements LibraryEventListener {

    private final AgentController controller;
    private final Gui gui;
    private final Label user = new Label();
    private final Button notices = new Button("Notices");
    private final Button undo = new Button("Undo");
    private final Button signOut = new Button("Sign out");
    private final Label status = new Label("Welcome.");
    private final List<String> pendingNotices = new ArrayList<>();
    private final TabPane tabs = new TabPane();
    private final Tab chatTab = new Tab("Chat");
    private final ChatView chat;
    private final StartView start;

    MainWindow(AgentController controller, Gui gui) {
        this.controller = controller;
        this.gui = gui;
        this.chat = new ChatView(controller, gui, this);
        this.start = new StartView(controller, gui, this);
        chatTab.setContent(chat);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Label name = new Label("📚 Library Agent");
        name.getStyleClass().add("app-name");
        notices.setOnAction(e -> showNotices());
        undo.setOnAction(e -> undo());
        signOut.setOnAction(e -> signOut());
        HBox toolbar = new HBox(name, Gui.spacer(), user, notices, undo, signOut);
        toolbar.getStyleClass().add("toolbar");
        setTop(toolbar);
        HBox statusBar = new HBox(status);
        statusBar.getStyleClass().add("status-bar");
        setBottom(statusBar);

        controller.subscribe(this);
        controller.subscribe(chat);
        showStart();
    }

    // ---- Screens -------------------------------------------------------------------------

    void showStart() {
        setCenter(start);
        updateHeader();
    }

    /** Shows the tabs that fit the current user (guests: Chat and Catalog). */
    void showMain() {
        tabs.getTabs().setAll(chatTab);
        setCenter(tabs);
        updateHeader();
    }

    void onSignedIn(Member member) {
        chat.clear();
        pendingNotices.clear();
        showMain();
        if (member != null) {
            setStatus("Signed in as " + member.getName() + " (" + member.getMemberId() + ").");
        }
    }

    void signOut() {
        gui.dataRun(controller::logout);
        chat.clear();
        pendingNotices.clear();
        showStart();
        setStatus("Signed out.");
    }

    /** Selects a tab by name (chat, catalog, account, librarian). */
    void selectTab(String name) {
        tabs.getTabs().stream().filter(t -> t.getText().equalsIgnoreCase(name)).findFirst()
                .ifPresent(t -> tabs.getSelectionModel().select(t));
    }

    /** Switches to the Chat tab and sends a message, e.g. from "Ask about this book". */
    void askInChat(String message) {
        tabs.getSelectionModel().select(chatTab);
        chat.send(message);
    }

    /** Reloads the data shown in every tab after something changed. */
    void refreshAll() {
        updateHeader();
    }

    // ---- Shared actions ------------------------------------------------------------------

    void reserve(Book book) {
        String memberId = controller.currentMember().map(Member::getMemberId).orElse(null);
        if (memberId == null) {
            setStatus("Sign in to reserve books.");
            return;
        }
        CommandResult result = gui.data(() -> controller.reserve(memberId, book.getIsbn()));
        setStatus(result.message());
        refreshAll();
    }

    void showBook(String isbn) {
        try {
            Book book = gui.data(() -> controller.bookDetails(isbn));
            Availability a = gui.data(() -> controller.availability(book));
            String copies = book.getCopies().stream()
                    .map(c -> c.getCopyId() + " (" + c.getState().name() + ")")
                    .collect(Collectors.joining(", "));
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(book.getTitle());
            alert.setHeaderText(book.getTitle() + " by " + book.getAuthor());
            alert.setContentText(book.getDescription() + "\n\n" + book.getPageCount() + " pages · "
                    + String.join(", ", book.getGenres()) + "\nISBN " + book.getIsbn()
                    + "\nCopies: " + copies + "\n" + a.availableCopies() + " of " + a.totalCopies()
                    + " available, " + a.queueLength() + " waiting");
            alert.show();
        } catch (LibraryException e) {
            setStatus(e.getMessage());
        }
    }

    void setStatus(String message) {
        status.setText(message);
    }

    private void undo() {
        String result = gui.data(controller::undoLast);
        setStatus(result);
        refreshAll();
    }

    // ---- Notices (Observer) --------------------------------------------------------------

    @Override
    public void onEvent(LibraryEvent event) {
        boolean forMe = controller.currentMember().map(m -> m.getMemberId().equals(event.memberId())).orElse(false);
        if (!forMe) {
            return;
        }
        Platform.runLater(() -> {
            pendingNotices.add(event.message());
            updateHeader();
            Gui.info((event.type() == EventType.HOLD_READY ? "Hold ready: " : "Overdue: ") + event.message());
        });
    }

    private void showNotices() {
        if (pendingNotices.isEmpty()) {
            Gui.info("No new notices.");
        } else {
            Gui.info(String.join("\n\n", pendingNotices));
            pendingNotices.clear();
            updateHeader();
        }
    }

    private void updateHeader() {
        boolean signedIn = controller.currentMember().isPresent();
        user.setText(controller.currentMember()
                .map(m -> m.getName() + " (" + m.getMemberId() + (m.isLibrarian() ? ", librarian" : "") + ")")
                .orElse(getCenter() == start ? "" : "Guest"));
        notices.setText(pendingNotices.isEmpty() ? "Notices" : "Notices (" + pendingNotices.size() + ")");
        notices.getStyleClass().remove("notices-on");
        if (!pendingNotices.isEmpty()) {
            notices.getStyleClass().add("notices-on");
        }
        notices.setVisible(signedIn);
        undo.setVisible(signedIn);
        signOut.setText(signedIn ? "Sign out" : "Back");
        signOut.setVisible(getCenter() != start);
    }
}
