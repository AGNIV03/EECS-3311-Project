package libraryagent.ui.gui;

import java.util.List;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import libraryagent.command.CommandResult;
import libraryagent.controller.AgentController;
import libraryagent.domain.Book;
import libraryagent.domain.LibraryException;
import libraryagent.domain.ReadingList;
import libraryagent.service.Availability;
import libraryagent.service.BookQuery;

/**
 * The Catalog tab: keyword/genre/length search without AI, a detail pane with Reserve, Add to
 * list and "Ask about this book", and a "Recommended for you" panel.
 */
final class CatalogView extends BorderPane {

    private static final String ALL_GENRES = "All genres";

    private final AgentController controller;
    private final Gui gui;
    private final MainWindow window;
    private final TextField keywords = new TextField();
    private final ComboBox<String> genre = new ComboBox<>();
    private final TextField maxPages = new TextField();
    private final TableView<Book> results = new TableView<>();
    private final VBox detail = new VBox(8);
    private final ListView<Book> recommended = new ListView<>();
    private final VBox recommendedPanel;

    CatalogView(AgentController controller, Gui gui, MainWindow window) {
        this.controller = controller;
        this.gui = gui;
        this.window = window;

        keywords.setPromptText("Title, author or topic");
        HBox.setHgrow(keywords, Priority.ALWAYS);
        genre.getItems().add(ALL_GENRES);
        genre.getItems().addAll(gui.data(controller::genreNames));
        genre.getSelectionModel().selectFirst();
        maxPages.setPromptText("Max pages");
        maxPages.setPrefWidth(90);
        Button search = new Button("Search");
        search.setDefaultButton(true);
        search.setOnAction(e -> search());
        Button clear = new Button("Show all");
        clear.setOnAction(e -> {
            keywords.clear();
            maxPages.clear();
            genre.getSelectionModel().selectFirst();
            search();
        });
        HBox bar = new HBox(8, keywords, genre, maxPages, search, clear);
        bar.setPadding(new Insets(10));
        setTop(bar);

        results.getColumns().add(Gui.column("Title", Book::getTitle, 250));
        results.getColumns().add(Gui.column("Author", Book::getAuthor, 160));
        results.getColumns().add(Gui.column("Pages", b -> String.valueOf(b.getPageCount()), 60));
        results.getColumns().add(Gui.column("Genres", b -> String.join(", ", b.getGenres()), 170));
        results.getColumns().add(Gui.column("Availability", this::availabilityText, 140));
        results.setPlaceholder(new Label("No books match. Try fewer words or another genre."));
        results.getSelectionModel().selectedItemProperty().addListener((obs, old, book) -> showDetail(book));

        detail.setPadding(new Insets(14));
        detail.getStyleClass().add("card");
        showDetail(null);
        SplitPane split = new SplitPane(results, new javafx.scene.control.ScrollPane(detail));
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.55);
        setCenter(split);

        recommended.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Book book, boolean empty) {
                super.updateItem(book, empty);
                setText(empty || book == null ? null : book.getTitle() + "\n" + book.getAuthor());
            }
        });
        recommended.getSelectionModel().selectedItemProperty().addListener((obs, old, book) -> {
            if (book != null) {
                results.getSelectionModel().clearSelection();
                showDetail(book);
            }
        });
        Button refreshRecs = new Button("Refresh");
        refreshRecs.setOnAction(e -> refresh());
        recommendedPanel = new VBox(8, Gui.heading("Recommended for you"),
                Gui.muted("From your favourite genres, authors and the books you liked."), recommended, refreshRecs);
        recommendedPanel.setPadding(new Insets(10));
        recommendedPanel.setPrefWidth(240);
        VBox.setVgrow(recommended, Priority.ALWAYS);
        setRight(recommendedPanel);

        search();
    }

    /** Reloads results, availability and recommendations. */
    void refresh() {
        Book selected = results.getSelectionModel().getSelectedItem();
        search();
        if (selected != null) {
            results.getSelectionModel().select(selected);
        }
        boolean signedIn = controller.currentMember().isPresent();
        recommendedPanel.setVisible(signedIn);
        recommendedPanel.setManaged(signedIn);
        if (signedIn) {
            String memberId = controller.currentMember().get().getMemberId();
            recommended.getItems().setAll(gui.data(() -> controller.recommendations(memberId, 6)));
            recommended.setPlaceholder(new Label("Rate books or set favourite\ngenres to get suggestions."));
        }
    }

    /** Shows one book (e.g. from a chat card). */
    void select(String isbn) {
        results.getItems().stream().filter(b -> b.getIsbn().equals(isbn)).findFirst()
                .ifPresentOrElse(b -> results.getSelectionModel().select(b),
                        () -> showDetail(gui.data(() -> controller.bookDetails(isbn))));
    }

    private void search() {
        try {
            Integer pages = maxPages.getText().isBlank() ? null : Integer.parseInt(maxPages.getText().strip());
            String selectedGenre = ALL_GENRES.equals(genre.getValue()) ? null : genre.getValue();
            BookQuery query = new BookQuery(null, null, selectedGenre, pages, BookQuery.ofKeywords(keywords.getText()).keywords());
            List<Book> books = gui.data(() -> controller.search(query));
            results.getItems().setAll(books);
            window.setStatus(books.size() + " book(s) found.");
        } catch (NumberFormatException e) {
            window.setStatus("Max pages must be a number.");
        } catch (LibraryException e) {
            window.setStatus(e.getMessage());
        }
    }

    private void showDetail(Book book) {
        detail.getChildren().clear();
        if (book == null) {
            detail.getChildren().add(Gui.muted("Select a book to see its details."));
            return;
        }
        Availability a = gui.data(() -> controller.availability(book));
        Label title = Gui.heading(book.getTitle());
        Label meta = Gui.muted(book.getAuthor() + "  ·  " + book.getPageCount() + " pages  ·  "
                + String.join(", ", book.getGenres()) + "  ·  ISBN " + book.getIsbn());
        Label description = new Label(book.getDescription());
        description.setWrapText(true);
        String copies = book.getCopies().stream().map(c -> c.getCopyId() + " " + c.getState().name())
                .collect(Collectors.joining(", "));
        Label availability = new Label(availabilityText(book) + "   (copies: " + copies + ")");

        Button reserve = new Button(a.isAvailable() ? "Reserve" : "Join hold queue");
        reserve.setOnAction(e -> {
            window.reserve(book);
            showDetail(book);
        });
        Button ask = new Button("Ask about this book");
        ask.setDisable(!controller.assistantAvailable());
        ask.setOnAction(e -> window.askInChat("Tell me about \"" + book.getTitle() + "\" (ISBN " + book.getIsbn()
                + "). What is it about, and who is it suitable for?"));

        HBox actions = new HBox(8, reserve, ask);
        if (controller.currentMember().isPresent()) {
            ComboBox<String> lists = new ComboBox<>();
            lists.setPromptText("Reading list");
            lists.getItems().setAll(gui.data(controller::readingLists).stream().map(ReadingList::getName).toList());
            Button add = new Button("Add to list");
            add.setOnAction(e -> {
                if (lists.getValue() == null) {
                    window.setStatus("Choose a reading list first (create one in the Account tab).");
                    return;
                }
                CommandResult r = gui.data(() -> controller.addToList(lists.getValue(), book.getIsbn()));
                window.setStatus(r.message());
                window.refreshAll();
            });
            actions.getChildren().addAll(lists, add);
        } else {
            reserve.setDisable(true);
            actions.getChildren().add(Gui.muted("Sign in to reserve or save books."));
        }
        detail.getChildren().addAll(title, meta, description, availability, actions);
    }

    private String availabilityText(Book book) {
        Availability a = gui.data(() -> controller.availability(book));
        return a.isAvailable()
                ? a.availableCopies() + " of " + a.totalCopies() + " available"
                : "All out" + (a.queueLength() > 0 ? ", " + a.queueLength() + " waiting" : "");
    }
}
