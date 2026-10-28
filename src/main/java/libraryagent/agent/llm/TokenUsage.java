package libraryagent.agent.llm;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Token counts for one request or a running total. */
public record TokenUsage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {

    public static final TokenUsage ZERO = new TokenUsage(0, 0, 0, 0);

    // Claude Sonnet 5.5 list prices in USD per million tokens. Cache writes (5-minute TTL)
    // cost 1.25x input; cache reads cost $0.20.
    private static final BigDecimal INPUT = new BigDecimal("2.00");
    private static final BigDecimal OUTPUT = new BigDecimal("10.00");
    private static final BigDecimal CACHE_READ = new BigDecimal("0.20");
    private static final BigDecimal CACHE_WRITE = new BigDecimal("2.50");
    private static final BigDecimal MILLION = new BigDecimal(1_000_000);

    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(inputTokens + other.inputTokens, outputTokens + other.outputTokens,
                cacheReadTokens + other.cacheReadTokens, cacheWriteTokens + other.cacheWriteTokens);
    }

    /** Approximate cost in USD at Claude Sonnet 5.5 list prices. */
    public BigDecimal estimatedCostUsd() {
        return INPUT.multiply(BigDecimal.valueOf(inputTokens))
                .add(OUTPUT.multiply(BigDecimal.valueOf(outputTokens)))
                .add(CACHE_READ.multiply(BigDecimal.valueOf(cacheReadTokens)))
                .add(CACHE_WRITE.multiply(BigDecimal.valueOf(cacheWriteTokens)))
                .divide(MILLION, 4, RoundingMode.HALF_UP);
    }
}
