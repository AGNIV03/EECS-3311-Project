package libraryagent.ui.gui;

import java.util.Arrays;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import libraryagent.controller.AgentController;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Member;
import libraryagent.domain.MemberType;

/** The start screen: sign in by member ID, register with a form, or continue as a guest. */
final class StartView extends VBox {

    private final AgentController controller;
    private final Gui gui;
    private final MainWindow window;

    StartView(AgentController controller, Gui gui, MainWindow window) {
        this.controller = controller;
        this.gui = gui;
        this.window = window;
        getStyleClass().add("start");
        setAlignment(Pos.TOP_CENTER);
        setSpacing(18);
        setPadding(new Insets(36));

        Label title = new Label("Library Agent");
        title.getStyleClass().add("title");
        Label subtitle = Gui.muted("Find, reserve and borrow books by talking to the library's AI assistant.");

        HBox cards = new HBox(24, signInCard(), registerCard());
        cards.setAlignment(Pos.TOP_CENTER);

        Button guest = new Button("Browse as a guest");
        guest.setOnAction(e -> window.showMain());
        Button chatSignUp = new Button("Sign up by chatting with the assistant");
        chatSignUp.setDisable(!controller.assistantAvailable());
        chatSignUp.setOnAction(e -> {
            window.showMain();
            window.askInChat("I'd like to sign up");
        });
        HBox guestRow = new HBox(12, guest, chatSignUp);
        guestRow.setAlignment(Pos.CENTER);

        getChildren().addAll(title, subtitle, cards, guestRow);
    }

    private VBox signInCard() {
        TextField memberId = new TextField();
        memberId.setPromptText("Member ID, e.g. M001");
        Label error = new Label();
        error.getStyleClass().add("error");
        Button signIn = new Button("Sign in");
        signIn.setDefaultButton(true);
        signIn.setOnAction(e -> {
            try {
                Member member = gui.data(() -> controller.login(memberId.getText().strip()));
                error.setText("");
                memberId.clear();
                window.onSignedIn(member);
            } catch (LibraryException ex) {
                error.setText(ex.getMessage());
            }
        });
        VBox card = card(Gui.heading("Sign in"), memberId, signIn, error,
                Gui.muted("Sample accounts: M001 (Alex, standard), M002 (Sam, student), LIB001 (Lee, librarian)."));
        card.setPrefWidth(320);
        return card;
    }

    private VBox registerCard() {
        TextField name = new TextField();
        TextField email = new TextField();
        ComboBox<String> type = new ComboBox<>();
        type.getItems().addAll("Standard", "Student");
        type.getSelectionModel().selectFirst();
        ListView<String> genres = new ListView<>();
        genres.getItems().setAll(gui.data(controller::subgenres));
        genres.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        genres.setPrefHeight(130);
        TextField authors = new TextField();
        authors.setPromptText("e.g. Andy Weir, Jane Austen");
        TextField maxPages = new TextField();
        maxPages.setPromptText("optional");
        Label error = new Label();
        error.getStyleClass().add("error");
        error.setWrapText(true);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.addRow(0, new Label("Name"), name);
        form.addRow(1, new Label("Email"), email);
        form.addRow(2, new Label("Member type"), type);
        form.addRow(3, new Label("Favourite genres"), genres);
        form.addRow(4, new Label("Favourite authors"), authors);
        form.addRow(5, new Label("Max pages"), maxPages);

        Button register = new Button("Create account");
        register.setOnAction(e -> {
            try {
                int pages = maxPages.getText().isBlank() ? 0 : Integer.parseInt(maxPages.getText().strip());
                List<String> authorList = Arrays.stream(authors.getText().split(","))
                        .map(String::strip).filter(s -> !s.isEmpty()).toList();
                Member member = gui.data(() -> {
                    Member created = controller.register(name.getText(), email.getText(),
                            MemberType.parse(type.getValue()));
                    controller.login(created.getMemberId());
                    controller.updatePreferences(created.getMemberId(),
                            List.copyOf(genres.getSelectionModel().getSelectedItems()), authorList, pages);
                    return created;
                });
                error.setText("");
                window.onSignedIn(member);
                Gui.info("Welcome, " + member.getName() + "! Your member ID is " + member.getMemberId() + ".");
            } catch (NumberFormatException ex) {
                error.setText("Max pages must be a number.");
            } catch (LibraryException ex) {
                error.setText(ex.getMessage());
            }
        });
        VBox card = card(Gui.heading("New member? Register"), form, register, error);
        card.setPrefWidth(440);
        return card;
    }

    private static VBox card(javafx.scene.Node... children) {
        VBox card = new VBox(10, children);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(18));
        return card;
    }
}
