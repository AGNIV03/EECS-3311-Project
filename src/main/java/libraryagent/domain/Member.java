package libraryagent.domain;

import java.util.Objects;

/** A registered library member. Librarians are members with extra permissions. */
public class Member {

    private final String memberId;
    private final String name;
    private final String email;
    private final MemberType type;
    private final boolean librarian;
    private final ReadingPreferences preferences;

    public Member(String memberId, String name, String email, MemberType type,
                  boolean librarian, ReadingPreferences preferences) {
        this.memberId = Objects.requireNonNull(memberId);
        this.name = Objects.requireNonNull(name);
        this.email = Objects.requireNonNull(email);
        this.type = Objects.requireNonNull(type);
        this.librarian = librarian;
        this.preferences = Objects.requireNonNull(preferences);
    }

    public String getMemberId() {
        return memberId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public MemberType getType() {
        return type;
    }

    public boolean isLibrarian() {
        return librarian;
    }

    public ReadingPreferences getPreferences() {
        return preferences;
    }

    /** Delegates to the member type so the limit has a single source of truth. */
    public int getBorrowLimit() {
        return type.borrowLimit();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Member other && memberId.equals(other.memberId);
    }

    @Override
    public int hashCode() {
        return memberId.hashCode();
    }

    @Override
    public String toString() {
        return name + " (" + memberId + ")";
    }
}
