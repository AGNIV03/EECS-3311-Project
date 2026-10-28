package libraryagent.agent.tools;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import libraryagent.domain.Book;
import libraryagent.service.Availability;

/** Compact views of domain objects for tool results (kept small to save tokens). */
final class ToolViews {

    private ToolViews() {
    }

    /** A money amount with its currency, e.g. "$2.25", so the model never guesses the currency. */
    static String money(BigDecimal amount) {
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
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
