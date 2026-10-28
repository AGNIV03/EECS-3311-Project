package libraryagent.ui.gui;

import java.time.LocalDate;
import java.util.List;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import libraryagent.command.CommandResult;
import libraryagent.controller.AccountSummary;
import libraryagent.controller.AgentController;
import libraryagent.controller.HoldStatus;
import libraryagent.domain.Book;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Loan;
import libraryagent.domain.Member;
import libraryagent.domain.Rating;
import libraryagent.domain.ReadingList;
import libraryagent.domain.ReservationStatus;
import libraryagent.notification.LibraryEvent;
import libraryagent.notification.LibraryEventListener;

/**
 * The Account tab: loans (renew), holds (cancel), fees, reading lists (create, drag to reorder,
 * remove, AI-generated lists), borrowing history with ratings, and the tracking switch.
 */
final class AccountView extends ScrollPane implements LibraryEventListener {

    private final AgentController controller;
    private final Gui gui;
    private final MainWindow window;
    private final Label summary = new Label();
    private final CheckBox tracking = new CheckBox("Track what I borrow and how I rated it, to improve recommendations");
    private final TableView<Loan> loans = new TableView<>();
    private final TableView<HoldStatus> holds = new TableView<>();
    private final ListView<ReadingList> lists = new ListView<>();
    private final ListView<Book> listBooks = new ListView<>();
    private final TableView<Loan> history = new TableView<>();
    private LocalDate today = LocalDate.now();

    AccountView(AgentController controller, Gui gui, MainWindow window) {
        this.controller = controller;
        this.gui = gui;
        this.window = window;

        tracking.setOnAction(e -> {
            gui.dataRun(() -> controller.setTracking(memberId(), tracking.isSelected()));
            window.setStatus(tracking.isSelected() ? "Reading feedback tracking is on."
                    : "Reading feedback tracking is off. Ratings won't be saved.");
            refresh();
        });
        summary.setWrapText(true);
        VBox overview = section("Account", summary, tracking);

        loans.getColumns().add(Gui.column("Title", l -> l.getBook().getTitle(), 200));
        loans.getColumns().add(Gui.column("Copy", l -> l.getCopy().getCopyId(), 60));
        loans.getColumns().add(Gui.column("Due", l -> l.getDueDate().toString(), 90));
        loans.getColumns().add(Gui.column("Status", l -> l.isOverdue(today) ? "OVERDUE" : "On loan", 80));
        loans.getColumns().add(Gui.column("Renewals", l -> l.getRenewals() + " of " + Loan.MAX_RENEWALS, 70));
        loans.getColumns().add(Gui.column("Fee so far", l -> "$" + l.calculateFee(
                l.getMember().getType().feePolicy(), today).toPlainString(), 80));
        loans.setPlaceholder(new Label("No books on loan."));
        Button renew = new Button("Renew selected");
        renew.setOnAction(e -> {
            Loan loan = loans.getSelectionModel().getSelectedItem();
            if (loan == null) {
                window.setStatus("Select a loan to renew.");
                return;
            }
            act(gui.data(() -> controller.renew(loan.getLoanId())));
        });
        VBox loansBox = section("Loans", loans, renew);

        holds.getColumns().add(Gui.column("Title", h -> h.reservation().getBook().getTitle(), 200));
        holds.getColumns().add(Gui.column("Status", h -> h.reservation().getStatus() == ReservationStatus.READY
                ? "Ready: pick up copy " + h.reservation().getCopy().getCopyId() + " by " + h.reservation().pickupDeadline()
                : "Waiting, number " + h.queuePosition() + " in the queue", 280));
        holds.setPlaceholder(new Label("No reservations."));
        Button cancel = new Button("Cancel selected");
        cancel.setOnAction(e -> {
            HoldStatus hold = holds.getSelectionModel().getSelectedItem();
            if (hold == null) {
                window.setStatus("Select a reservation to cancel.");
                return;
            }
            act(gui.data(() -> controller.cancelReservation(hold.reservation().getReservationId())));
        });
        VBox holdsBox = section("Holds", holds, cancel);

        VBox listsBox = section("Reading lists", readingListsPane());

        history.getColumns().add(Gui.column("Checked out", l -> l.getCheckoutDate().toString(), 100));
        history.getColumns().add(Gui.column("Title", l -> l.getBook().getTitle(), 220));
        history.getColumns().add(Gui.column("Returned", l -> l.isOpen() ? "on loan" : l.getReturnDate().toString(), 100));
        history.getColumns().add(Gui.column("Your rating", l -> gui.data(() -> controller.ratingFor(memberId(),
                l.getBook().getIsbn())).map(f -> f.getRating().name().toLowerCase()).orElse(""), 100));
        history.setPlaceholder(new Label("You haven't borrowed anything yet."));
        ComboBox<String> rating = new ComboBox<>();
        rating.getItems().addAll("Liked", "Neutral", "Disliked");
        rating.setPromptText("Did you like it?");
        TextField comment = new TextField();
        comment.setPromptText("Comment (optional)");
        HBox.setHgrow(comment, Priority.ALWAYS);
        Button rate = new Button("Rate selected");
        rate.setOnAction(e -> {
            Loan loan = history.getSelectionModel().getSelectedItem();
            if (loan == null || rating.getValue() == null) {
                window.setStatus("Select a book and a rating.");
                return;
            }
            try {
                gui.dataRun(() -> controller.rateBook(memberId(), loan.getBook().getIsbn(),
                        Rating.parse(rating.getValue()), comment.getText()));
                window.setStatus("Saved your rating of \"" + loan.getBook().getTitle() + "\".");
                comment.clear();
                window.refreshAll();
            } catch (LibraryException ex) {
                window.setStatus(ex.getMessage());
            }
        });
        VBox historyBox = section("Borrowing history", history, new HBox(8, rating, comment, rate));

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setPadding(new Insets(14));
        ColumnConstraints half = new ColumnConstraints();
        half.setPercentWidth(50);
        grid.getColumnConstraints().addAll(half, half);
        grid.add(overview, 0, 0, 2, 1);
        grid.add(loansBox, 0, 1);
        grid.add(holdsBox, 1, 1);
        grid.add(listsBox, 0, 2, 2, 1);
        grid.add(historyBox, 0, 3, 2, 1);
        setContent(grid);
        setFitToWidth(true);
    }

    private HBox readingListsPane() {
        lists.setPrefWidth(220);
        lists.setPrefHeight(200);
        lists.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(ReadingList list, boolean empty) {
                super.updateItem(list, empty);
                setText(empty || list == null ? null : list.getName() + " (" + list.getBooks().size() + ")");
            }
        });
        lists.getSelectionModel().selectedItemProperty().addListener((o, old, list) -> showList(list));
        TextField newList = new TextField();
        newList.setPromptText("New list name");
        Button create = new Button("Create");
        create.setOnAction(e -> {
            act(gui.data(() -> controller.createList(newList.getText())));
            newList.clear();
        });
        Button generate = new Button("Generate a list with AI");
        generate.setDisable(!controller.assistantAvailable());
        generate.setOnAction(e -> window.askInChat(
                "Make me a reading list of 5 books based on my tastes, give it a fitting name, and save it."));
        VBox left = new VBox(8, lists, new HBox(6, newList, create), generate);

        listBooks.setPrefHeight(200);
        listBooks.setPlaceholder(new Label("Select a list. Add books from the Catalog tab or by chat."));
        listBooks.setCellFactory(v -> draggableBookCell());
        Button up = new Button("Move up");
        up.setOnAction(e -> moveSelected(-1));
        Button down = new Button("Move down");
        down.setOnAction(e -> moveSelected(1));
        Button remove = new Button("Remove");
        remove.setOnAction(e -> {
            ReadingList list = lists.getSelectionModel().getSelectedItem();
            Book book = listBooks.getSelectionModel().getSelectedItem();
            if (list != null && book != null) {
                act(gui.data(() -> controller.removeFromList(list.getName(), book.getIsbn())));
            }
        });
        VBox right = new VBox(8, listBooks, new HBox(6, up, down, remove,
                Gui.muted("Tip: drag books to reorder.")));
        HBox.setHgrow(right, Priority.ALWAYS);
        return new HBox(14, left, right);
    }

    private ListCell<Book> draggableBookCell() {
        ListCell<Book> cell = new ListCell<>() {
            @Override
            protected void updateItem(Book book, boolean empty) {
                super.updateItem(book, empty);
                setText(empty || book == null ? null : (getIndex() + 1) + ". " + book.getTitle() + " — " + book.getAuthor());
            }
        };
        cell.setOnDragDetected(e -> {
            if (cell.getItem() == null) {
                return;
            }
            Dragboard board = cell.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(cell.getItem().getIsbn());
            board.setContent(content);
            e.consume();
        });
        cell.setOnDragOver(e -> {
            if (e.getGestureSource() != cell && e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        cell.setOnDragDropped(e -> {
            ReadingList list = lists.getSelectionModel().getSelectedItem();
            if (list != null && e.getDragboard().hasString()) {
                int target = cell.isEmpty() ? listBooks.getItems().size() : cell.getIndex() + 1;
                act(gui.data(() -> controller.moveInList(list.getName(), e.getDragboard().getString(), target)));
                e.setDropCompleted(true);
            }
            e.consume();
        });
        return cell;
    }

    private void moveSelected(int delta) {
        ReadingList list = lists.getSelectionModel().getSelectedItem();
        int index = listBooks.getSelectionModel().getSelectedIndex();
        if (list == null || index < 0) {
            window.setStatus("Select a book in a list to move.");
            return;
        }
        Book book = listBooks.getItems().get(index);
        int target = Math.max(1, Math.min(listBooks.getItems().size(), index + 1 + delta));
        act(gui.data(() -> controller.moveInList(list.getName(), book.getIsbn(), target)));
        listBooks.getSelectionModel().select(target - 1);
    }

    private void showList(ReadingList list) {
        listBooks.getItems().setAll(list == null ? List.of() : list.getBooks());
    }

    /** Reloads everything for the signed-in member. */
    void refresh() {
        Member member = controller.currentMember().orElse(null);
        if (member == null) {
            return;
        }
        AccountSummary s = gui.data(() -> controller.accountSummary(member.getMemberId()));
        today = s.today();
        summary.setText(member.getName() + "  ·  " + member.getMemberId() + "  ·  "
                + member.getType().name().toLowerCase() + " member  ·  up to " + member.getBorrowLimit()
                + " loans\nFees owed: $" + s.feesOwed().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                + (member.getPreferences().getFavoriteGenres().isEmpty() ? ""
                        : "\nFavourite genres: " + String.join(", ", member.getPreferences().getFavoriteGenres()))
                + (member.getPreferences().getFavoriteAuthors().isEmpty() ? ""
                        : "  ·  Favourite authors: " + String.join(", ", member.getPreferences().getFavoriteAuthors())));
        tracking.setSelected(member.getPreferences().isTrackingEnabled());
        loans.getItems().setAll(s.loans());
        holds.getItems().setAll(s.holds());
        ReadingList selected = lists.getSelectionModel().getSelectedItem();
        lists.getItems().setAll(s.readingLists());
        if (selected != null) {
            s.readingLists().stream().filter(l -> l.getListId().equals(selected.getListId())).findFirst()
                    .ifPresent(l -> lists.getSelectionModel().select(l));
        }
        showList(lists.getSelectionModel().getSelectedItem());
        history.getItems().setAll(gui.data(() -> controller.loanHistory(member.getMemberId())));
        history.refresh();
    }

    private void act(CommandResult result) {
        window.setStatus(result.message());
        window.refreshAll();
    }

    private String memberId() {
        return controller.currentMember().map(Member::getMemberId).orElse("");
    }

    private static VBox section(String title, javafx.scene.Node... content) {
        VBox box = new VBox(8);
        box.getChildren().add(Gui.heading(title));
        box.getChildren().addAll(content);
        box.getStyleClass().addAll("card", "section");
        return box;
    }

    /** Observer: a hold becoming ready or a loan going overdue refreshes the page. */
    @Override
    public void onEvent(LibraryEvent event) {
        boolean forMe = controller.currentMember().map(m -> m.getMemberId().equals(event.memberId())).orElse(false);
        if (forMe) {
            Platform.runLater(this::refresh);
        }
    }
}
