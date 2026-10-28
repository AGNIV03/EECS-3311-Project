package libraryagent.agent.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import libraryagent.agent.AgentContext;
import libraryagent.agent.llm.ToolSpec;
import libraryagent.command.AddToReadingListCommand;
import libraryagent.command.CommandResult;
import libraryagent.domain.Book;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Member;
import libraryagent.domain.ReadingList;
import libraryagent.service.CatalogService;
import libraryagent.service.FeedbackService;
import libraryagent.service.ReadingListService;

/**
 * Saves an AI-curated reading list. Every book must have come from recommendBooks in this
 * session and must not be one the member disliked, so the model cannot slip in other titles.
 */
public class CreateCuratedListTool extends AbstractTool {

    static final int MAX_BOOKS = 10;

    private final ReadingListService readingLists;
    private final FeedbackService feedback;
    private final CatalogService catalog;

    public CreateCuratedListTool(ReadingListService readingLists, FeedbackService feedback, CatalogService catalog) {
        this.readingLists = readingLists;
        this.feedback = feedback;
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "createCuratedList";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "Save a themed reading list for the logged-in member, such as \"Winter break\". Every ISBN must come "
                        + "from recommendBooks results in this conversation (call it first), and books the member "
                        + "disliked are rejected. Creates a new list with the given name and adds the books in order.",
                properties(
                        "listName", stringProperty("Name for the new list."),
                        "isbns", stringArrayProperty("ISBNs of recommended books, in the order to list them.")),
                List.of("listName", "isbns"));
    }

    @Override
    protected List<String> validate(Map<String, Object> args) {
        List<String> errors = new ArrayList<>();
        requireString(args, "listName", errors);
        int count = strings(args, "isbns").size();
        if (count == 0) {
            errors.add("isbns must list at least one book");
        } else if (count > MAX_BOOKS) {
            errors.add("a curated list can hold at most " + MAX_BOOKS + " books");
        }
        return errors;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> args, AgentContext context) {
        Member member = context.requireMember();
        List<String> isbns = strings(args, "isbns");
        List<String> notRecommended = isbns.stream().filter(i -> !context.wasRecommended(i)).toList();
        if (!notRecommended.isEmpty()) {
            throw new LibraryException("These ISBNs did not come from recommendBooks in this conversation: "
                    + String.join(", ", notRecommended) + ". Call recommendBooks and use only its candidates.");
        }
        List<Book> books = isbns.stream().map(catalog::requireBook).distinct().toList();
        List<String> disliked = books.stream()
                .filter(b -> feedback.tasteProfile(member).dislikedIsbns().contains(b.getIsbn()))
                .map(Book::getTitle)
                .toList();
        if (!disliked.isEmpty()) {
            throw new LibraryException("The member rated these books disliked, so they can't go on a curated list: "
                    + String.join(", ", disliked) + ".");
        }

        ReadingList list = readingLists.createList(member, string(args, "listName"));
        for (Book book : books) {
            CommandResult result = context.getHistory().run(new AddToReadingListCommand(readingLists, list, book));
            if (!result.success()) {
                return ToolResult.error(result.message());
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", list.getName());
        data.put("books", books.stream().map(Book::getTitle).toList());
        return ToolResult.ok(data, books);
    }
}
