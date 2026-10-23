package libraryagent.domain.fee;

import java.math.BigDecimal;
import java.time.LocalDate;
import libraryagent.domain.Loan;

/** Standard members pay a daily rate from the first late day, up to a maximum. */
public final class StandardFeePolicy implements LateFeePolicy {

    public static final BigDecimal DAILY_RATE = new BigDecimal("0.25");
    /** Highest fee for any single loan, for every member type. */
    public static final BigDecimal MAX_FEE = new BigDecimal("10.00");

    @Override
    public BigDecimal calculateFee(Loan loan, LocalDate today) {
        long daysLate = loan.daysLate(today);
        return FeeMath.capped(DAILY_RATE.multiply(BigDecimal.valueOf(daysLate)), MAX_FEE);
    }
}
