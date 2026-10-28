package libraryagent.agent.llm;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Decorator: wraps any {@link LLMClient} and refuses new requests once the day's estimated spend
 * reaches a budget. Used by the public web version so visitors cannot run up the API bill.
 */
public class BudgetedLLMClient implements LLMClient {

    private final LLMClient inner;
    private final BigDecimal dailyBudgetUsd;
    private final Clock clock;
    private LocalDate day;
    private TokenUsage today = TokenUsage.ZERO;
    private TokenUsage total = TokenUsage.ZERO;

    public BudgetedLLMClient(LLMClient inner, BigDecimal dailyBudgetUsd, Clock clock) {
        this.inner = inner;
        this.dailyBudgetUsd = dailyBudgetUsd;
        this.clock = clock;
        this.day = LocalDate.now(clock);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        synchronized (this) {
            rollOver();
            if (today.estimatedCostUsd().compareTo(dailyBudgetUsd) >= 0) {
                throw new LlmException(LlmException.Kind.RATE_LIMITED,
                        "The AI assistant has used up today's budget for this demo. Please try again tomorrow; "
                                + "searching, reserving and every other feature still work without it.", null);
            }
        }
        LlmResponse response = inner.complete(request);
        synchronized (this) {
            rollOver();
            today = today.plus(response.usage());
            total = total.plus(response.usage());
        }
        return response;
    }

    @Override
    public synchronized TokenUsage totalUsage() {
        return total;
    }

    /** Estimated spend so far today. */
    public synchronized BigDecimal spentTodayUsd() {
        rollOver();
        return today.estimatedCostUsd();
    }

    private void rollOver() {
        LocalDate now = LocalDate.now(clock);
        if (!now.equals(day)) {
            day = now;
            today = TokenUsage.ZERO;
        }
    }
}
