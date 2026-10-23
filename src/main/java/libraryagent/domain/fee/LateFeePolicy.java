package libraryagent.domain.fee;

import java.math.BigDecimal;
import java.time.LocalDate;
import libraryagent.domain.Loan;

/**
 * Strategy pattern: how late fees are computed. Each member type supplies its own policy
 * through {@link libraryagent.domain.MemberType#feePolicy()}.
 */
public interface LateFeePolicy {

    /** Fee owed for the loan as of {@code today} (or as of its return date if returned). */
    BigDecimal calculateFee(Loan loan, LocalDate today);
}
