package libraryagent.service;

import java.util.Arrays;
import java.util.List;

/**
 * Structured catalogue search. Every field is optional; null or empty fields are ignored.
 *
 * @param title    text the title must contain
 * @param author   text the author must contain
 * @param genre    category or subgenre name; includes all subgenres beneath a category
 * @param maxPages maximum page count
 * @param keywords words matched against title, author, description and genres; books matching
 *                 more keywords rank higher, and a book must match at least one
 */
public record BookQuery(String title, String author, String genre, Integer maxPages, List<String> keywords) {

    public BookQuery {
        keywords = keywords == null ? List.of() : keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(String::strip)
                .toList();
    }

    /** A plain keyword search, as typed into a search bar. */
    public static BookQuery ofKeywords(String text) {
        List<String> words = text == null ? List.of()
                : Arrays.stream(text.split("\\s+")).filter(w -> !w.isBlank()).toList();
        return new BookQuery(null, null, null, null, words);
    }

    public boolean isEmpty() {
        return blank(title) && blank(author) && blank(genre) && maxPages == null && keywords.isEmpty();
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
