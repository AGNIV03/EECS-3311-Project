package libraryagent.agent.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import libraryagent.agent.AgentContext;
import libraryagent.agent.llm.ToolSpec;
import libraryagent.domain.Book;
import libraryagent.domain.BookFeedback;
import libraryagent.domain.Rating;
import libraryagent.service.CatalogService;
import libraryagent.service.FeedbackService;

/** Turns an opinion stated in chat ("I loved The Hobbit") into a stored rating. */
public class RecordFeedbackTool extends AbstractTool {

    private final FeedbackService feedback;
    private final CatalogService catalog;

    public RecordFeedbackTool(FeedbackService feedback, CatalogService catalog) {
        this.feedback = feedback;
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "recordFeedback";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "Save whether the logged-in member liked a book they borrowed, when they clearly say so (\"I loved "
                        + "it\" = liked, \"it was fine\" = neutral, \"couldn't finish it\" = disliked). If the "
                        + "opinion is vague, ask whether they liked it before calling. Only books from their "
                        + "borrowing history can be rated; rating again replaces the old rating. Ratings shape "
                        + "future recommendations.",
                properties(
                        "isbn", stringProperty("ISBN of the borrowed book."),
                        "rating", enumProperty("The member's opinion.", "liked", "neutral", "disliked"),
                        "comment", stringProperty("Short comment in the member's words, or \"\" if none.")),
                List.of("isbn", "rating", "comment"));
    }

    @Override
    protected List<String> validate(Map<String, Object> args) {
        List<String> errors = new ArrayList<>();
        requireString(args, "isbn", errors);
        String rating = string(args, "rating");
        if (rating == null || !List.of("liked", "neutral", "disliked").contains(rating.toLowerCase())) {
            errors.add("rating must be liked, neutral or disliked");
        }
        return errors;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> args, AgentContext context) {
        Book book = catalog.requireBook(string(args, "isbn"));
        BookFeedback saved = feedback.recordFeedback(context.requireMember(), book,
                Rating.parse(string(args, "rating")), string(args, "comment"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("saved", true);
        data.put("title", book.getTitle());
        data.put("rating", saved.getRating().name().toLowerCase());
        return ToolResult.ok(data, List.of(book));
    }
}
