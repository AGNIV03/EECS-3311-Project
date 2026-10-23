package libraryagent.domain;

import java.util.ArrayList;
import java.util.List;

/** What a member told us they like, and whether we may track their reading feedback. */
public class ReadingPreferences {

    private final List<String> favoriteGenres = new ArrayList<>();
    private final List<String> favoriteAuthors = new ArrayList<>();
    /** Preferred maximum page count; 0 means no preference. */
    private int maxPages;
    private boolean trackingEnabled = true;

    public ReadingPreferences() {
    }

    public ReadingPreferences(List<String> favoriteGenres, List<String> favoriteAuthors,
                              int maxPages, boolean trackingEnabled) {
        this.favoriteGenres.addAll(favoriteGenres);
        this.favoriteAuthors.addAll(favoriteAuthors);
        this.maxPages = Math.max(0, maxPages);
        this.trackingEnabled = trackingEnabled;
    }

    public List<String> getFavoriteGenres() {
        return List.copyOf(favoriteGenres);
    }

    public List<String> getFavoriteAuthors() {
        return List.copyOf(favoriteAuthors);
    }

    public int getMaxPages() {
        return maxPages;
    }

    public boolean isTrackingEnabled() {
        return trackingEnabled;
    }

    public void setTrackingEnabled(boolean trackingEnabled) {
        this.trackingEnabled = trackingEnabled;
    }

    /** Replaces genres, authors and page preference; the tracking setting is kept. */
    public void update(List<String> genres, List<String> authors, int maxPages) {
        favoriteGenres.clear();
        favoriteGenres.addAll(genres);
        favoriteAuthors.clear();
        favoriteAuthors.addAll(authors);
        this.maxPages = Math.max(0, maxPages);
    }
}
