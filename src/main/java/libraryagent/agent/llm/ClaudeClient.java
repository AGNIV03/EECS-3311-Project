package libraryagent.agent.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.errors.UnprocessableEntityException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import libraryagent.config.AppConfig;

/**
 * Adapter: implements the application's {@link LLMClient} interface on top of the Anthropic
 * Java SDK, translating between the app's message types and the SDK's. Always uses
 * Claude Sonnet 5.5 ({@link AppConfig#MODEL}).
 */
public class ClaudeClient implements LLMClient {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final AnthropicClient client;
    private final OutputConfig.Effort effort;
    private TokenUsage totalUsage = TokenUsage.ZERO;

    public ClaudeClient(String apiKey, String effort) {
        this(AnthropicOkHttpClient.builder().apiKey(apiKey).build(), effort);
    }

    ClaudeClient(AnthropicClient client, String effort) {
        this.client = client;
        this.effort = OutputConfig.Effort.of(effort);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        com.anthropic.models.messages.Message reply;
        try {
            reply = client.messages().create(toParams(request));
        } catch (RateLimitException e) {
            throw new LlmException(LlmException.Kind.RATE_LIMITED,
                    "The AI assistant is busy right now. Please try again in a minute.", e);
        } catch (UnauthorizedException | PermissionDeniedException e) {
            throw new LlmException(LlmException.Kind.AUTHENTICATION,
                    "The AI assistant could not sign in. Check ANTHROPIC_API_KEY in your .env file.", e);
        } catch (BadRequestException | UnprocessableEntityException | NotFoundException e) {
            throw new LlmException(LlmException.Kind.INVALID_REQUEST,
                    "The AI assistant rejected the request (" + e.getMessage() + ").", e);
        } catch (InternalServerException e) {
            throw unavailable(e);
        } catch (AnthropicServiceException e) {
            throw unavailable(e);
        } catch (AnthropicIoException e) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE,
                    "Could not reach the AI assistant. Check your internet connection.", e);
        }
        LlmResponse response = fromMessage(reply);
        synchronized (this) {
            totalUsage = totalUsage.plus(response.usage());
        }
        return response;
    }

    /** Tokens used by every request this client has made. */
    @Override
    public synchronized TokenUsage totalUsage() {
        return totalUsage;
    }

    // ---- App types -> SDK types ----------------------------------------------------------

    MessageCreateParams toParams(LlmRequest request) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(AppConfig.MODEL)
                .maxTokens(request.maxTokens())
                .outputConfig(OutputConfig.builder().effort(effort).build())
                // Caches the growing conversation between steps of the agent loop.
                .cacheControl(CacheControlEphemeral.builder().build())
                // Tools and system prompt never change within a session, so they are cached too.
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(request.system())
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()));
        for (ToolSpec spec : request.tools()) {
            builder.addTool(toTool(spec));
        }
        for (Message message : request.messages()) {
            builder.addMessage(toParam(message));
        }
        return builder.build();
    }

    private static Tool toTool(ToolSpec spec) {
        Tool.InputSchema.Properties.Builder properties = Tool.InputSchema.Properties.builder();
        spec.properties().forEach((name, schema) -> properties.putAdditionalProperty(name, JsonValue.from(schema)));
        return Tool.builder()
                .name(spec.name())
                .description(spec.description())
                .strict(true)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(properties.build())
                        .required(spec.required())
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }

    private static MessageParam toParam(Message message) {
        if (message.providerMessage() instanceof MessageParam original) {
            // The model's own turn, replayed exactly as received (reasoning blocks included).
            return original;
        }
        if (message.role() == Message.Role.SYSTEM) {
            return MessageParam.ofSystem(message.text());
        }
        List<ContentBlockParam> blocks = new ArrayList<>();
        for (MessagePart part : message.parts()) {
            switch (part) {
                case MessagePart.Text t -> blocks.add(ContentBlockParam.ofText(t.text()));
                case MessagePart.ToolResult r -> blocks.add(ContentBlockParam.ofToolResult(
                        ToolResultBlockParam.builder()
                                .toolUseId(r.toolUseId())
                                .content(r.content())
                                .isError(r.isError())
                                .build()));
                case MessagePart.ToolUse u -> throw new IllegalArgumentException(
                        "Assistant tool calls must be replayed from the provider's original message");
            }
        }
        return message.role() == Message.Role.USER
                ? MessageParam.ofUserOfContentBlockParams(blocks)
                : MessageParam.ofAssistantOfContentBlockParams(blocks);
    }

    // ---- SDK types -> app types ----------------------------------------------------------

    static LlmResponse fromMessage(com.anthropic.models.messages.Message reply) {
        List<MessagePart> parts = new ArrayList<>();
        for (ContentBlock block : reply.content()) {
            block.text().ifPresent(t -> parts.add(new MessagePart.Text(t.text())));
            block.toolUse().ifPresent(u -> parts.add(toToolUse(u)));
            // Reasoning and other block types stay only in the provider message for replay.
        }
        return new LlmResponse(parts, toStopReason(reply.stopReason().orElse(null)), toUsage(reply.usage()),
                reply.toParam());
    }

    private static MessagePart.ToolUse toToolUse(ToolUseBlock block) {
        Map<String, Object> input = block._input().convert(MAP);
        return new MessagePart.ToolUse(block.id(), block.name(), input == null ? Map.of() : input);
    }

    private static LlmResponse.StopReason toStopReason(StopReason reason) {
        if (reason == null) {
            return LlmResponse.StopReason.OTHER;
        }
        if (reason.equals(StopReason.END_TURN)) {
            return LlmResponse.StopReason.END_TURN;
        }
        if (reason.equals(StopReason.TOOL_USE)) {
            return LlmResponse.StopReason.TOOL_USE;
        }
        if (reason.equals(StopReason.MAX_TOKENS)) {
            return LlmResponse.StopReason.MAX_TOKENS;
        }
        if (reason.equals(StopReason.REFUSAL)) {
            return LlmResponse.StopReason.REFUSAL;
        }
        return LlmResponse.StopReason.OTHER;
    }

    private static TokenUsage toUsage(Usage usage) {
        return new TokenUsage(usage.inputTokens(), usage.outputTokens(),
                usage.cacheReadInputTokens().orElse(0L), usage.cacheCreationInputTokens().orElse(0L));
    }

    private static LlmException unavailable(AnthropicServiceException e) {
        return new LlmException(LlmException.Kind.UNAVAILABLE,
                "The AI assistant is temporarily unavailable. Please try again shortly.", e);
    }
}
