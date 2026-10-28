package libraryagent.service;

import java.util.List;
import java.util.Set;

/**
 * What a member likes, built from their stated preferences and (when tracking is on) their
 * ratings.
 *
 * @param excludedIsbns books never to recommend: already borrowed or rated disliked
 * @param dislikedIsbns books the member rated disliked
 * @param maxPages      preferred maximum length, or 0 for none
 */
public record TasteProfile(List<String> likedGenres, List<String> likedAuthors, Set<String> excludedIsbns,
                           Set<String> dislikedIsbns, int maxPages) {

    public TasteProfile {
        likedGenres = List.copyOf(likedGenres);
        likedAuthors = List.copyOf(likedAuthors);
        excludedIsbns = Set.copyOf(excludedIsbns);
        dislikedIsbns = Set.copyOf(dislikedIsbns);
    }

    /** True when there is nothing to base recommendations on. */
    public boolean isEmpty() {
        return likedGenres.isEmpty() && likedAuthors.isEmpty();
    }
}
