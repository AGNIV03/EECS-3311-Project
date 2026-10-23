package libraryagent.domain.fee;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class FeeMath {

    private FeeMath() {
    }

    static BigDecimal capped(BigDecimal fee, BigDecimal max) {
        return fee.min(max).setScale(2, RoundingMode.HALF_UP);
    }
}
