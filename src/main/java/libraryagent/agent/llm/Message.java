package libraryagent.agent.llm;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A conversation turn. {@code parts} is what the application reads; {@code providerMessage}
 * keeps the model's own turn exactly as the API returned it (including reasoning blocks the
 * application never reads), so it can be sent back unchanged on the next request.
 *
 * @param providerMessage provider-specific message, or null for messages built by the app
 */
public record Message(Role role, List<MessagePart> parts, Object providerMessage) {

    public enum Role { USER, ASSISTANT }

    public Message {
        parts = List.copyOf(parts);
    }

    public static Message user(String text) {
        return new Message(Role.USER, List.of(new MessagePart.Text(text)), null);
    }

    /** All tool results from one round, returned together in a single user message. */
    public static Message toolResults(List<MessagePart.ToolResult> results) {
        return new Message(Role.USER, List.copyOf(results), null);
    }

    public static Message assistant(List<MessagePart> parts, Object providerMessage) {
        return new Message(Role.ASSISTANT, parts, providerMessage);
    }

    /** The text parts joined together. */
    public String text() {
        return parts.stream()
                .filter(MessagePart.Text.class::isInstance)
                .map(p -> ((MessagePart.Text) p).text())
                .collect(Collectors.joining("\n"))
                .strip();
    }

    public List<MessagePart.ToolUse> toolUses() {
        return parts.stream().filter(MessagePart.ToolUse.class::isInstance).map(MessagePart.ToolUse.class::cast).toList();
    }
}
