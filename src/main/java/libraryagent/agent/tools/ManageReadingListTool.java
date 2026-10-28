package libraryagent.agent.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import libraryagent.agent.AgentContext;
import libraryagent.agent.llm.ToolSpec;
import libraryagent.command.AddToReadingListCommand;
import libraryagent.command.Command;
import libraryagent.command.CommandResult;
import libraryagent.command.MoveInReadingListCommand;
import libraryagent.command.RemoveFromReadingListCommand;
import libraryagent.domain.Book;
import libraryagent.domain.Member;
import libraryagent.domain.ReadingList;
import libraryagent.service.CatalogService;
import libraryagent.service.ReadingListService;

/** Creates, shows and edits the logged-in member's reading lists. Edits go through the undo history. */
public class ManageReadingListTool extends AbstractTool {

    private final ReadingListService readingLists;
    private final CatalogService catalog;

    public ManageReadingListTool(ReadingListService readingLists, CatalogService catalog) {
        this.readingLists = readingLists;
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "manageReadingList";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "Manage the logged-in member's reading lists. One call does one action on one list. "
                        + "action=create makes a new list; show returns a list (use listName \"*\" for all lists); "
                        + "add, remove and move change a list and need an isbn; move also needs a 1-based position. "
                        + "To create a list and fill it, call create first, then add once per book. If the user "
                        + "refers to a list that does not exist, offer to create it. Adds, removes and moves can be "
                        + "undone.",
                properties(
                        "action", enumProperty("What to do.", "create", "show", "add", "remove", "move"),
                        "listName", stringProperty("Name of the reading list, or \"*\" with show for all lists."),
                        "isbn", stringProperty("ISBN of the book for add, remove and move; \"\" for create and show."),
                        "position", integerProperty("New 1-based position for move; 0 for every other action.")),
                List.of("action", "listName", "isbn", "position"));
    }

    @Override
    protected List<String> validate(Map<String, Object> args) {
        List<String> errors = new ArrayList<>();
        String action = string(args, "action");
        if (action == null || !List.of("create", "show", "add", "remove", "move").contains(action)) {
            errors.add("action must be create, show, add, remove or move");
            return errors;
        }
        requireString(args, "listName", errors);
        if (List.of("add", "remove", "move").contains(action)) {
            requireString(args, "isbn", errors);
        }
        if (action.equals("move")) {
            Integer position = null;
            try {
                position = integer(args, "position");
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
            }
            if (position == null || position < 1) {
                errors.add("position must be 1 or more for move");
            }
        }
        return errors;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> args, AgentContext context) {
        Member member = context.requireMember();
        String action = string(args, "action");
        String listName = string(args, "listName");
        if (action.equals("show")) {
            List<ReadingList> lists = listName.equals("*")
                    ? readingLists.listsFor(member)
                    : List.of(readingLists.requireList(member, listName));
            List<Book> books = lists.stream().flatMap(l -> l.getBooks().stream()).distinct().toList();
            return ToolResult.ok(Map.of("lists", lists.stream().map(ManageReadingListTool::view).toList()), books);
        }
        if (action.equals("create")) {
            ReadingList list = readingLists.createList(member, listName);
            return ToolResult.ok(Map.of("created", view(list)));
        }
        ReadingList list = readingLists.requireList(member, listName);
        Book book = catalog.requireBook(string(args, "isbn"));
        Command command = switch (action) {
            case "add" -> new AddToReadingListCommand(readingLists, list, book);
            case "remove" -> new RemoveFromReadingListCommand(readingLists, list, book);
            default -> new MoveInReadingListCommand(readingLists, list, book, integer(args, "position") - 1);
        };
        CommandResult result = context.getHistory().run(command);
        if (!result.success()) {
            return ToolResult.error(result.message());
        }
        return ToolResult.ok(Map.of("message", result.message(), "list", view(list)), List.of(book));
    }

    private static Map<String, Object> view(ReadingList list) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", list.getName());
        List<Map<String, Object>> books = new ArrayList<>();
        List<Book> entries = list.getBooks();
        for (int i = 0; i < entries.size(); i++) {
            books.add(Map.of("position", i + 1, "title", entries.get(i).getTitle(), "isbn", entries.get(i).getIsbn()));
        }
        view.put("books", books);
        return view;
    }
}
