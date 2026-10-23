package libraryagent.domain;

import java.util.Locale;
import libraryagent.domain.fee.LateFeePolicy;
import libraryagent.domain.fee.StandardFeePolicy;
import libraryagent.domain.fee.StudentFeePolicy;

/** Membership category; selects the borrowing limit and the late-fee strategy. */
public enum MemberType {
    STANDARD(5, new StandardFeePolicy()),
    STUDENT(8, new StudentFeePolicy());

    private final int borrowLimit;
    private final LateFeePolicy feePolicy;

    MemberType(int borrowLimit, LateFeePolicy feePolicy) {
        this.borrowLimit = borrowLimit;
        this.feePolicy = feePolicy;
    }

    /** Maximum number of loans a member of this type may have open at once. */
    public int borrowLimit() {
        return borrowLimit;
    }

    /** Late-fee strategy for members of this type. */
    public LateFeePolicy feePolicy() {
        return feePolicy;
    }

    public static MemberType parse(String text) {
        try {
            return valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new LibraryException("Member type must be standard or student.");
        }
    }
}
