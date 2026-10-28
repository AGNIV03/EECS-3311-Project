package libraryagent.agent.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import libraryagent.agent.AgentContext;
import libraryagent.agent.llm.ToolSpec;
import libraryagent.domain.Book;
import libraryagent.service.BookQuery;
import libraryagent.service.CatalogService;

/** Searches the catalogue with structured criteria built by the model from a plain-language request. */
public class SearchCatalogTool extends AbstractTool {

    static final int MAX_RESULTS = 10;

    private final CatalogService catalog;

    public SearchCatalogTool(CatalogService catalog) {
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "searchCatalog";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "Search the library catalogue. Use it whenever the user wants to find books. Every field is "
                        + "optional but at least one is needed. A genre can be a category (Fiction, Nonfiction, "
                        + "Young Adult) or a subgenre (e.g. Fantasy, Science Fiction, Mystery); categories include "
                        + "all their subgenres. Returns up to " + MAX_RESULTS + " books with availability, best "
                        + "matches first. If a genre is unknown, the error lists the real subgenres.",
                properties(
                        "keywords", stringArrayProperty("Words to match in title, author, description or genre, "
                                + "e.g. [\"dragon\", \"quest\"]. Books matching more words rank higher."),
                        "title", stringProperty("Text the title must contain."),
                        "author", stringProperty("Text the author's name must contain."),
                        "genre", stringProperty("Category or subgenre name."),
                        "maxPages", integerProperty("Maximum page count, for requests like 'not too long'.")),
                List.of());
    }

    @Override
    protected List<String> validate(Map<String, Object> args) {
        List<String> errors = new ArrayList<>();
        checkInteger(args, "maxPages", 1, errors);
        if (errors.isEmpty() && query(args).isEmpty()) {
            errors.add("give at least one of keywords, title, author, genre or maxPages");
        }
        return errors;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> args, AgentContext context) {
        List<Book> matches = catalog.search(query(args));
        List<Book> shown = matches.subList(0, Math.min(MAX_RESULTS, matches.size()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalMatches", matches.size());
        data.put("books", shown.stream().map(b -> ToolViews.bookSummary(b, catalog.availability(b))).toList());
        if (matches.isEmpty()) {
            data.put("hint", "No books matched. Try fewer or broader criteria; never suggest titles that are "
                    + "not in the catalogue.");
        }
        return ToolResult.ok(data, shown);
    }

    private static BookQuery query(Map<String, Object> args) {
        return new BookQuery(string(args, "title"), string(args, "author"), string(args, "genre"),
                integer(args, "maxPages"), strings(args, "keywords"));
    }
}
