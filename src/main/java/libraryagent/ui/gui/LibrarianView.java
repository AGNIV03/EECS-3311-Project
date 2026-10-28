package libraryagent.ui.gui;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import libraryagent.command.CommandResult;
import libraryagent.controller.AgentController;
import libraryagent.domain.Book;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Loan;

/** The Librarian tab: desk checkout and check-in, the overdue list, and adding books with AI genre tagging. */
final class LibrarianView extends ScrollPane {

    private final AgentController controller;
    private final Gui gui;
    private final MainWindow window;
    private final TableView<Loan> overdue = new TableView<>();
    private final Label deskResult = new Label();
    private LocalDate today = LocalDate.now();

    LibrarianView(AgentController controller, Gui gui, MainWindow window) {
        this.controller = controller;
        this.gui = gui;
        this.window = window;

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setPadding(new Insets(14));
        ColumnConstraints half = new ColumnConstraints();
        half.setPercentWidth(50);
        grid.getColumnConstraints().addAll(half, half);
        grid.add(deskPane(), 0, 0);
        grid.add(overduePane(), 1, 0);
        grid.add(addBookPane(), 0, 1, 2, 1);
        setContent(grid);
        setFitToWidth(true);
    }

    private VBox deskPane() {
        TextField memberId = new TextField();
        memberId.setPromptText("Member ID, e.g. M002");
        TextField copyOut = new TextField();
        copyOut.setPromptText("Copy ID, e.g. C0010");
        Button checkOut = new Button("Check out");
        checkOut.setOnAction(e -> desk(gui.data(() -> controller.checkOut(memberId.getText().strip(),
                copyOut.getText().strip())), copyOut));

        TextField copyIn = new TextField();
        copyIn.setPromptText("Copy ID being returned");
        Button checkIn = new Button("Check in");
        checkIn.setOnAction(e -> desk(gui.data(() -> controller.checkIn(copyIn.getText().strip())), copyIn));

        deskResult.setWrapText(true);
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Member"), memberId);
        form.addRow(1, new Label("Copy"), copyOut, checkOut);
        form.addRow(2, new Label("Return copy"), copyIn, checkIn);
        return section("Desk", form, deskResult,
                Gui.muted("Copy IDs are shown in each book's details in the Catalog tab."));
    }

    private void desk(CommandResult result, TextField clear) {
        deskResult.getStyleClass().removeAll("error", "success");
        deskResult.getStyleClass().add(result.success() ? "success" : "error");
        deskResult.setText(result.message());
        if (result.success()) {
            clear.clear();
        }
        window.setStatus(result.message());
        window.refreshAll();
    }

    private VBox overduePane() {
        overdue.getColumns().add(Gui.column("Title", l -> l.getBook().getTitle(), 170));
        overdue.getColumns().add(Gui.column("Copy", l -> l.getCopy().getCopyId(), 60));
        overdue.getColumns().add(Gui.column("Member", l -> l.getMember().getName() + " (" + l.getMember().getMemberId() + ")", 150));
        overdue.getColumns().add(Gui.column("Due", l -> l.getDueDate().toString(), 85));
        overdue.getColumns().add(Gui.column("Days late", l -> String.valueOf(ChronoUnit.DAYS.between(l.getDueDate(), today)), 70));
        overdue.getColumns().add(Gui.column("Fee", l -> "$" + l.calculateFee(l.getMember().getType().feePolicy(), today).toPlainString(), 60));
        overdue.setPlaceholder(new Label("No overdue loans."));
        Button check = new Button("Check for overdue loans");
        check.setOnAction(e -> refresh());
        return section("Overdue loans", overdue, check,
                Gui.muted("Checking flags newly late copies and notifies their borrowers."));
    }

    private VBox addBookPane() {
        TextField isbn = new TextField();
        TextField title = new TextField();
        TextField author = new TextField();
        TextField pages = new TextField();
        TextField copies = new TextField("1");
        TextArea description = new TextArea();
        description.setPrefRowCount(3);
        description.setWrapText(true);
        ListView<String> genres = new ListView<>();
        genres.getItems().setAll(gui.data(controller::subgenres));
        genres.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        genres.setPrefHeight(160);
        Label message = new Label();
        message.setWrapText(true);

        Button suggest = new Button("Suggest genres");
        suggest.setDisable(!controller.assistantAvailable());
        suggest.setOnAction(e -> {
            Book draft;
            try {
                draft = draft(isbn, title, author, pages, description);
            } catch (NumberFormatException ex) {
                message.setText("Pages must be a number.");
                return;
            }
            suggest.setDisable(true);
            message.setText("Asking the assistant…");
            Gui.background(() -> controller.suggestGenres(draft), suggested -> {
                genres.getSelectionModel().clearSelection();
                suggested.forEach(g -> genres.getSelectionModel().select(g));
                message.setText(suggested.isEmpty() ? "No suggestion; please choose genres yourself."
                        : "Suggested: " + String.join(", ", suggested) + ". Adjust if needed, then add the book.");
                suggest.setDisable(false);
            }, error -> {
                message.setText(error instanceof LibraryException ? error.getMessage() : "Suggestion failed.");
                suggest.setDisable(false);
            });
        });

        Button add = new Button("Add book");
        add.setOnAction(e -> {
            try {
                Book draft = draft(isbn, title, author, pages, description);
                int copyCount = Integer.parseInt(copies.getText().strip());
                List<String> chosen = List.copyOf(genres.getSelectionModel().getSelectedItems());
                Book added = gui.data(() -> controller.addBook(draft, copyCount, chosen));
                message.setText("Added \"" + added.getTitle() + "\" with " + added.getCopies().size()
                        + " copies, filed under " + String.join(", ", added.getGenres()) + ".");
                for (TextField f : List.of(isbn, title, author, pages)) {
                    f.clear();
                }
                description.clear();
                copies.setText("1");
                genres.getSelectionModel().clearSelection();
                window.refreshAll();
            } catch (NumberFormatException ex) {
                message.setText("Pages and copies must be numbers.");
            } catch (LibraryException ex) {
                message.setText(ex.getMessage());
            }
        });

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("ISBN"), isbn, new Label("Copies"), copies);
        form.addRow(1, new Label("Title"), title, new Label("Pages"), pages);
        form.addRow(2, new Label("Author"), author);
        form.add(new Label("Description"), 0, 3);
        form.add(description, 1, 3, 3, 1);
        VBox genreBox = new VBox(6, new Label("Subgenres (Ctrl+click for several)"), genres, suggest);
        HBox body = new HBox(18, form, genreBox);
        return section("Add a book", body, new HBox(8, add), message);
    }

    private static Book draft(TextField isbn, TextField title, TextField author, TextField pages, TextArea description) {
        int pageCount = pages.getText().isBlank() ? 0 : Integer.parseInt(pages.getText().strip());
        return new Book(isbn.getText().strip(), title.getText().strip(), author.getText().strip(), pageCount,
                description.getText().strip());
    }

    /** Reloads the overdue list (librarians only). */
    void refresh() {
        boolean librarian = controller.currentMember().map(m -> m.isLibrarian()).orElse(false);
        if (!librarian) {
            return;
        }
        List<Loan> late = gui.data(controller::findOverdue);
        today = gui.data(() -> controller.accountSummary(controller.currentMember().get().getMemberId()).today());
        overdue.getItems().setAll(late);
    }

    private static VBox section(String title, javafx.scene.Node... content) {
        VBox box = new VBox(8);
        box.getChildren().add(Gui.heading(title));
        box.getChildren().addAll(content);
        box.getStyleClass().addAll("card", "section");
        return box;
    }
}
