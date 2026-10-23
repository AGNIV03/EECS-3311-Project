package libraryagent.domain;

import java.util.Locale;

/** Membership category; determines the borrowing limit. */
public enum MemberType {
    STANDARD(5),
    STUDENT(8);

    private final int borrowLimit;

    MemberType(int borrowLimit) {
        this.borrowLimit = borrowLimit;
    }

    /** Maximum number of loans a member of this type may have open at once. */
    public int borrowLimit() {
        return borrowLimit;
    }

    public static MemberType parse(String text) {
        try {
            return valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new LibraryException("Member type must be standard or student.");
        }
    }
}
