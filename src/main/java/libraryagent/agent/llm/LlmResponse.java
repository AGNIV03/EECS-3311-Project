package libraryagent.agent.llm;

import java.util.List;

/** The model's reply to one request. */
public record LlmResponse(List<MessagePart> content, StopReason stopReason, TokenUsage usage, Object providerMessage) {

    /** Why the model stopped. */
    public enum StopReason {
        /** Finished its answer. */
        END_TURN,
        /** Wants one or more tools run. */
        TOOL_USE,
        /** Ran out of output tokens. */
        MAX_TOKENS,
        /** Declined the request on safety grounds. */
        REFUSAL,
        OTHER
    }

    public LlmResponse {
        content = List.copyOf(content);
    }

    /** This reply as a conversation turn, keeping the provider's original for replay. */
    public Message toMessage() {
        return Message.assistant(content, providerMessage);
    }
}
