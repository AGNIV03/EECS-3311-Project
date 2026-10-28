package libraryagent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import libraryagent.agent.llm.LLMClient;
import libraryagent.agent.llm.LlmResponse;
import libraryagent.domain.Book;
import libraryagent.service.CatalogService;

/**
 * Suggests subgenres for a new book from its description. The model proposes; only names that
 * exist in the library's genre hierarchy are kept, so it can never invent a category.
 */
public class GenreTagger {

    static final int MAX_SUGGESTIONS = 3;

    private final LLMClient llm;
    private final PromptComposer composer;
    private final CatalogService catalog;
    private final ObjectMapper json = new ObjectMapper();

    public GenreTagger(LLMClient llm, PromptComposer composer, CatalogService catalog) {
        this.llm = llm;
        this.composer = composer;
        this.catalog = catalog;
    }

    /** Up to three existing subgenres that fit the book, best first (may be empty). */
    public List<String> suggestGenres(Book book) {
        List<String> allowed = catalog.subgenreNames();
        LlmResponse response = llm.complete(composer.buildTaggingRequest(book, allowed));
        Map<String, String> canonical = allowed.stream()
                .collect(Collectors.toMap(n -> n.toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        Set<String> kept = new LinkedHashSet<>();
        for (String name : parseNames(response.toMessage().text())) {
            String match = canonical.get(name.strip().toLowerCase(Locale.ROOT));
            if (match != null && kept.size() < MAX_SUGGESTIONS) {
                kept.add(match);
            }
        }
        return List.copyOf(kept);
    }

    /** Reads a JSON array if there is one, otherwise splits on commas and new lines. */
    List<String> parseNames(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            try {
                List<?> items = json.readValue(text.substring(start, end + 1), List.class);
                List<String> names = new ArrayList<>();
                items.forEach(i -> names.add(String.valueOf(i)));
                return names;
            } catch (JsonProcessingException e) {
                // Fall through to the plain-text split.
            }
        }
        List<String> names = new ArrayList<>();
        for (String part : text.split("[,\\n]")) {
            String cleaned = part.replaceAll("[\"\\[\\]*\\-•]", "").strip();
            if (!cleaned.isEmpty()) {
                names.add(cleaned);
            }
        }
        return names;
    }
}
