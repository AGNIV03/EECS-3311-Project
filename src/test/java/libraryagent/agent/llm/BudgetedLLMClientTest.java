package libraryagent.agent.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BudgetedLLMClientTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T15:00:00Z"), ZoneId.of("America/Toronto"));
    private static final LlmRequest REQUEST = new LlmRequest("system", List.of(), List.of(Message.user("hi")), 1000);

    /** A model that does not answer until released. */
    private static final class SlowClient implements LLMClient {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public LlmResponse complete(LlmRequest request) {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return FakeLLMClient.text("done");
        }

        @Override
        public TokenUsage totalUsage() {
            return TokenUsage.ZERO;
        }
    }

    @Test
    void requestsStillWaitingForTheModelCountTowardTheCap() throws Exception {
        SlowClient slow = new SlowClient();
        BudgetedLLMClient budgeted = new BudgetedLLMClient(slow, BudgetedLLMClient.ESTIMATED_REQUEST_USD, CLOCK);
        Thread first = new Thread(() -> budgeted.complete(REQUEST));
        first.start();
        assertTrue(slow.started.await(5, TimeUnit.SECONDS));

        LlmException refused = assertThrows(LlmException.class, () -> budgeted.complete(REQUEST));
        assertEquals(LlmException.Kind.RATE_LIMITED, refused.kind());

        slow.release.countDown();
        first.join(5000);
        // The first request cost nothing in this test, so the budget is free again once it returns.
        assertEquals("done", budgeted.complete(REQUEST).toMessage().text());
    }

    @Test
    void aFailedRequestReleasesItsReservation() {
        FakeLLMClient failing = new FakeLLMClient();
        failing.fail(new LlmException(LlmException.Kind.UNAVAILABLE, "down", null)).reply(FakeLLMClient.text("ok"));
        BudgetedLLMClient budgeted = new BudgetedLLMClient(failing, BudgetedLLMClient.ESTIMATED_REQUEST_USD, CLOCK);

        assertThrows(LlmException.class, () -> budgeted.complete(REQUEST));
        assertEquals("ok", budgeted.complete(REQUEST).toMessage().text());
    }
}
