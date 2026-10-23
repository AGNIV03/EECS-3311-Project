package libraryagent.domain.fee;

import java.math.BigDecimal;
import java.time.LocalDate;
import libraryagent.domain.Loan;

/** Students get a grace period and a lower daily rate, capped at the standard maximum. */
public final class StudentFeePolicy implements LateFeePolicy {

    public static final int GRACE_DAYS = 2;
    public static final BigDecimal DAILY_RATE = new BigDecimal("0.10");

    @Override
    public BigDecimal calculateFee(Loan loan, LocalDate today) {
        long chargeableDays = Math.max(0, loan.daysLate(today) - GRACE_DAYS);
        return FeeMath.capped(DAILY_RATE.multiply(BigDecimal.valueOf(chargeableDays)),
                StandardFeePolicy.MAX_FEE);
    }
}
