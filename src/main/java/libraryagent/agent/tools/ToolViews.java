package libraryagent.agent.tools;

import java.util.LinkedHashMap;
import java.util.Map;
import libraryagent.domain.Book;
import libraryagent.service.Availability;

/** Compact views of domain objects for tool results (kept small to save tokens). */
final class ToolViews {

    private ToolViews() {
    }

    static Map<String, Object> bookSummary(Book book, Availability availability) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("isbn", book.getIsbn());
        view.put("title", book.getTitle());
        view.put("author", book.getAuthor());
        view.put("pages", book.getPageCount());
        view.put("genres", book.getGenres());
        view.put("availableCopies", availability.availableCopies());
        view.put("totalCopies", availability.totalCopies());
        view.put("waitingInQueue", availability.queueLength());
        return view;
    }
}
