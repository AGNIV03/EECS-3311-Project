package libraryagent.service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Member;
import libraryagent.domain.MemberType;
import libraryagent.domain.ReadingPreferences;
import libraryagent.persistence.GenreRepository;
import libraryagent.persistence.MemberRepository;

/** Registering members and maintaining their reading preferences. */
public class MemberService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final MemberRepository members;
    private final GenreRepository genres;

    public MemberService(MemberRepository members, GenreRepository genres) {
        this.members = members;
        this.genres = genres;
    }

    /** Creates a new standard or student member with default preferences. */
    public Member register(String name, String email, MemberType type) {
        if (name == null || name.isBlank()) {
            throw new LibraryException("A name is required.");
        }
        if (name.strip().length() > 100) {
            throw new LibraryException("Name must be at most 100 characters.");
        }
        if (email == null || !EMAIL.matcher(email.strip()).matches()) {
            throw new LibraryException("\"" + email + "\" is not a valid email address.");
        }
        if (members.findByEmail(email).isPresent()) {
            throw new LibraryException("An account with the email " + email.strip() + " already exists.");
        }
        if (type == null) {
            throw new LibraryException("Member type must be standard or student.");
        }
        Member member = new Member(members.nextId(), name.strip(), email.strip(), type, false,
                new ReadingPreferences());
        members.save(member);
        return member;
    }

    /**
     * Replaces the member's favourite genres, authors and page preference. Genres must exist
     * in the catalogue's hierarchy; the tracking setting is not changed here.
     */
    public void updatePreferences(Member member, ReadingPreferences prefs) {
        List<String> canonicalGenres = canonicalGenres(prefs.getFavoriteGenres());
        List<String> authors = prefs.getFavoriteAuthors().stream().map(String::strip)
                .filter(a -> !a.isEmpty()).distinct().toList();
        if (prefs.getMaxPages() < 0) {
            throw new LibraryException("Preferred maximum length cannot be negative.");
        }
        member.getPreferences().update(canonicalGenres, authors, prefs.getMaxPages());
        members.save(member);
    }

    /**
     * The catalogue's own names for the given genres (case-insensitive, duplicates removed).
     * Throws with the list of real subgenres if any genre is unknown.
     */
    public List<String> canonicalGenres(List<String> names) {
        return names.stream()
                .map(g -> genres.find(g)
                        .orElseThrow(() -> new LibraryException("\"" + g + "\" is not a genre in the catalogue. "
                                + "Known subgenres: " + String.join(", ", genres.root().subgenreNames()) + "."))
                        .getName())
                .distinct()
                .toList();
    }

    public Optional<Member> findById(String memberId) {
        return memberId == null ? Optional.empty() : members.findById(memberId);
    }

    public Member requireMember(String memberId) {
        return findById(memberId).orElseThrow(() -> new LibraryException("No member with ID " + memberId + "."));
    }

    public List<Member> allMembers() {
        return members.findAll();
    }
}
