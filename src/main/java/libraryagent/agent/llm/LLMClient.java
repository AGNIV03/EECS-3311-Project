package libraryagent.agent.llm;

/**
 * Strategy for talking to a language model. {@link ClaudeClient} calls the real API;
 * {@link FakeLLMClient} replays scripted replies so the agent can be tested offline.
 */
public interface LLMClient {

    /** Sends one request and returns the model's reply; throws {@link LlmException} on failure. */
    LlmResponse complete(LlmRequest request);

    /** Tokens used by every request so far (zero for clients that don't track usage). */
    default TokenUsage totalUsage() {
        return TokenUsage.ZERO;
    }
}
