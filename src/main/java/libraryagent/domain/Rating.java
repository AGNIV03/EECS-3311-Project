package libraryagent.domain;

import java.util.Locale;

/** A member's opinion of a book they borrowed. */
public enum Rating {
    LIKED,
    NEUTRAL,
    DISLIKED;

    /** Parses user input such as "liked", "Disliked" or "neutral". */
    public static Rating parse(String text) {
        try {
            return valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new LibraryException("Rating must be liked, neutral or disliked.");
        }
    }
}
