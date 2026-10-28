package libraryagent.ui.gui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import libraryagent.agent.AgentResponse;
import libraryagent.controller.AgentController;
import libraryagent.domain.Book;
import libraryagent.domain.Member;
import libraryagent.notification.LibraryEvent;
import libraryagent.notification.LibraryEventListener;
import libraryagent.service.Availability;

/**
 * The Chat tab: talk to the AI assistant. Replies run on a background thread so the window stays
 * responsive; books in a reply are shown as cards with Details and Reserve buttons.
 */
final class ChatView extends BorderPane implements LibraryEventListener {

    private final AgentController controller;
    private final Gui gui;
    private final MainWindow window;
    private final VBox messages = new VBox(10);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final TextField input = new TextField();
    private final Button send = new Button("Send");

    ChatView(AgentController controller, Gui gui, MainWindow window) {
        this.controller = controller;
        this.gui = gui;
        this.window = window;
        messages.setPadding(new Insets(14));
        messages.getStyleClass().add("chat");
        scroll.setFitToWidth(true);
        messages.heightProperty().addListener((obs, old, now) -> scroll.setVvalue(1.0));
        setCenter(scroll);

        input.setPromptText(controller.assistantAvailable()
                ? "Ask anything, e.g. \"find two short sci-fi books and reserve whichever are free\""
                : "The AI assistant is off (no API key). Use the other tabs.");
        input.setDisable(!controller.assistantAvailable());
        send.setDisable(!controller.assistantAvailable());
        send.setDefaultButton(true);
        send.setOnAction(e -> send(input.getText()));
        HBox.setHgrow(input, Priority.ALWAYS);
        HBox bar = new HBox(8, input, send);
        bar.setPadding(new Insets(10));
        setBottom(bar);
        clear();
    }

    /** Starts a fresh conversation display (on sign-in or sign-out). */
    void clear() {
        messages.getChildren().clear();
        addBubble("Hi! I'm the library assistant. I can search the catalogue, answer questions about books, "
                + "reserve, renew and return books, manage your reading lists and recommend what to read next.",
                "assistant");
    }

    /** Sends a message as if the user had typed it. */
    void send(String text) {
        if (text == null || text.isBlank() || !controller.assistantAvailable()) {
            return;
        }
        String message = text.strip();
        input.clear();
        addBubble(message, "user");
        Label thinking = addBubble("Thinking…", "thinking");
        setBusy(true);
        Member before = controller.currentMember().orElse(null);
        Gui.background(() -> controller.chat(message), response -> {
            messages.getChildren().remove(thinking);
            showReply(response);
            setBusy(false);
            Member after = controller.currentMember().orElse(null);
            if (before != after) {
                window.onSignedIn(after);
            }
            window.refreshAll();
        }, error -> {
            messages.getChildren().remove(thinking);
            addBubble("Something went wrong: " + error.getMessage(), "error-bubble");
            setBusy(false);
        });
    }

    private void showReply(AgentResponse response) {
        addBubble(response.text(), response.error() ? "error-bubble" : "assistant");
        for (Book book : response.books()) {
            messages.getChildren().add(bookCard(book));
        }
    }

    private HBox bookCard(Book book) {
        Availability availability = gui.data(() -> controller.availability(book));
        Label text = new Label(book.getTitle() + "  ·  " + book.getAuthor() + "  ·  " + book.getPageCount()
                + " pages\n" + (availability.isAvailable()
                ? availability.availableCopies() + " of " + availability.totalCopies() + " available"
                : "All " + availability.totalCopies() + " out, " + availability.queueLength() + " waiting"));
        text.setWrapText(true);
        Button details = new Button("Details");
        details.setOnAction(e -> window.showBook(book.getIsbn()));
        Button reserve = new Button("Reserve");
        reserve.setDisable(controller.currentMember().isEmpty());
        reserve.setOnAction(e -> window.reserve(book));
        HBox card = new HBox(10, text, Gui.spacer(), details, reserve);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("book-card");
        return card;
    }

    private Label addBubble(String text, String style) {
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(720);
        bubble.getStyleClass().addAll("bubble", style);
        HBox row = new HBox(bubble);
        row.setAlignment(style.equals("user") ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messages.getChildren().add(row);
        return bubble;
    }

    private void setBusy(boolean busy) {
        input.setDisable(busy);
        send.setDisable(busy);
        if (!busy) {
            input.requestFocus();
        }
    }

    /** Observer: library events for the signed-in member appear in the conversation. */
    @Override
    public void onEvent(LibraryEvent event) {
        boolean forMe = controller.currentMember().map(m -> m.getMemberId().equals(event.memberId())).orElse(false);
        if (forMe) {
            Platform.runLater(() -> addBubble("🔔 " + event.message(), "notice"));
        }
    }
}
