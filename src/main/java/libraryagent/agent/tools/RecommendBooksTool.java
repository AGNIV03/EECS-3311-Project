package libraryagent.agent.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import libraryagent.agent.AgentContext;
import libraryagent.agent.llm.ToolSpec;
import libraryagent.domain.Book;
import libraryagent.domain.Member;
import libraryagent.domain.genre.GenreComponent;
import libraryagent.service.BookQuery;
import libraryagent.service.CatalogService;
import libraryagent.service.FeedbackService;
import libraryagent.service.TasteProfile;

/**
 * Picks real catalogue books that fit the member's taste profile. The model only ranks and
 * explains these candidates; the ISBNs are remembered so curated lists can only use them.
 */
public class RecommendBooksTool extends AbstractTool {

    static final int MAX_COUNT = 10;
    private static final int POOL = 30;

    private final FeedbackService feedback;
    private final CatalogService catalog;

    public RecommendBooksTool(FeedbackService feedback, CatalogService catalog) {
        this.feedback = feedback;
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "recommendBooks";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "Get candidate books for the logged-in member from their saved preferences, the books they rated "
                        + "liked, and an optional theme. Books they already borrowed or disliked are never included. "
                        + "Recommend only from these candidates and give a one-line reason for each using "
                        + "matchedGenres/matchedAuthor. Call this before createCuratedList. If there is nothing to go "
                        + "on, ask the user what they enjoy.",
                properties(
                        "count", integerProperty("How many candidates to return (1-" + MAX_COUNT + ")."),
                        "theme", stringProperty("Genre or keyword to focus on, e.g. \"mystery\" or \"winter\"; "
                                + "\"\" for none."),
                        "maxPages", integerProperty("Maximum length the user asked for in this request; 0 for none.")),
                List.of("count", "theme", "maxPages"));
    }

    @Override
    protected List<String> validate(Map<String, Object> args) {
        List<String> errors = new ArrayList<>();
        checkInteger(args, "count", 1, errors);
        checkInteger(args, "maxPages", 0, errors);
        if (errors.isEmpty() && integer(args, "count") != null && integer(args, "count") > MAX_COUNT) {
            errors.add("count can be at most " + MAX_COUNT);
        }
        return errors;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> args, AgentContext context) {
        Member member = context.requireMember();
        Integer requested = integer(args, "count");
        int count = requested == null ? 5 : requested;
        String theme = string(args, "theme");
        Integer maxPages = integer(args, "maxPages");
        TasteProfile profile = feedback.tasteProfile(member);

        List<Book> pool = new ArrayList<>(catalog.recommendCandidates(member, profile, POOL));
        if (theme != null) {
            List<Book> themed = themeMatches(theme, profile);
            pool = pool.isEmpty() ? themed : pool.stream().filter(themed::contains).toList();
            if (pool.isEmpty()) {
                pool = themed;
            }
        }
        List<Book> candidates = pool.stream()
                .filter(b -> maxPages == null || maxPages == 0 || b.getPageCount() <= maxPages)
                .limit(count)
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("basedOn", Map.of("likedGenres", profile.likedGenres(), "likedAuthors", profile.likedAuthors(),
                "trackingOn", member.getPreferences().isTrackingEnabled()));
        if (candidates.isEmpty()) {
            data.put("candidates", List.of());
            data.put("hint", profile.isEmpty() && theme == null
                    ? "The member has no saved preferences or liked books yet. Ask what they enjoy."
                    : "No unread books match. Suggest loosening the theme or length.");
            return ToolResult.ok(data);
        }
        data.put("candidates", candidates.stream().map(b -> candidateView(b, profile)).toList());
        context.addRecommended(candidates.stream().map(Book::getIsbn).toList());
        return ToolResult.ok(data, candidates);
    }

    /** Books matching the theme as a genre, or as keywords, excluding borrowed and disliked books. */
    private List<Book> themeMatches(String theme, TasteProfile profile) {
        List<Book> matches;
        try {
            matches = catalog.search(new BookQuery(null, null, theme, null, null));
        } catch (RuntimeException notAGenre) {
            matches = catalog.search(BookQuery.ofKeywords(theme));
        }
        return matches.stream().filter(b -> !profile.excludedIsbns().contains(b.getIsbn())).toList();
    }

    private Map<String, Object> candidateView(Book book, TasteProfile profile) {
        Map<String, Object> view = new LinkedHashMap<>(ToolViews.bookSummary(book, catalog.availability(book)));
        List<String> matchedGenres = profile.likedGenres().stream()
                .filter(g -> catalog.genreTree().find(g).map((GenreComponent c) -> c.contains(book)).orElse(false))
                .toList();
        view.put("matchedGenres", matchedGenres);
        profile.likedAuthors().stream()
                .filter(a -> book.getAuthor().toLowerCase(Locale.ROOT).contains(a.toLowerCase(Locale.ROOT)))
                .findFirst()
                .ifPresent(a -> view.put("matchedAuthor", a));
        view.put("description", book.getDescription());
        return view;
    }
}
