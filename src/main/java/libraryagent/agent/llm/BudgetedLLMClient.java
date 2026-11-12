package libraryagent.agent.llm;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Decorator: wraps any {@link LLMClient} and refuses new requests once the day's estimated spend
 * reaches a budget. Used by the public web version so visitors cannot run up the API bill.
 */
public class BudgetedLLMClient implements LLMClient {

    /** What one request is assumed to cost while its real cost is not yet known. */
    static final BigDecimal ESTIMATED_REQUEST_USD = new BigDecimal("0.05");

    private final LLMClient inner;
    private final BigDecimal dailyBudgetUsd;
    private final Clock clock;
    private LocalDate day;
    private TokenUsage today = TokenUsage.ZERO;
    private TokenUsage total = TokenUsage.ZERO;
    private int inFlight;

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
            // Requests already waiting for the model count as spent at an estimated cost each, so
            // several visitors arriving together cannot all slip in just under the cap.
            BigDecimal committed = today.estimatedCostUsd()
                    .add(ESTIMATED_REQUEST_USD.multiply(BigDecimal.valueOf(inFlight)));
            if (committed.compareTo(dailyBudgetUsd) >= 0) {
                throw new LlmException(LlmException.Kind.RATE_LIMITED,
                        "The AI assistant has used up today's budget for this demo. Please try again tomorrow; "
                                + "searching, reserving and every other feature still work without it.", null);
            }
            inFlight++;
        }
        LlmResponse response;
        try {
            response = inner.complete(request);
        } finally {
            synchronized (this) {
                inFlight--;
            }
        }
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
