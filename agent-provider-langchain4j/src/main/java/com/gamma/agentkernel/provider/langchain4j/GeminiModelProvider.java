package com.gamma.agentkernel.provider.langchain4j;

import com.gamma.agentkernel.error.ModelError;
import com.gamma.agentkernel.model.ModelProvider;
import com.gamma.agentkernel.model.ModelRequest;
import com.gamma.agentkernel.model.ModelResponse;
import com.gamma.agentkernel.model.ModelRouter;
import com.gamma.agentkernel.model.ModelTier;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * A LangChain4j-backed hosted {@link ModelProvider} over Google AI Gemini. One instance binds a single
 * {@link ModelTier}/model from a {@link GeminiModelProfile}. It is the second provider behind the ring-1
 * {@link ModelProvider} seam (after {@code agent-provider-ollama}) — the R1 confirmation that the seam
 * generalizes past one provider with no ring-1 change (ADR-0011).
 *
 * <h3>Lazy &amp; abstain-safe</h3>
 * The chat model is built on first {@link #generate} only — never in the constructor — so merely
 * constructing providers touches no network and needs no key. {@link #available()} is a pure config check
 * (enabled + key + a model mapped for the tier); callers test it before {@link #generate}.
 *
 * <h3>Streaming note (R1.3 → R1.5)</h3>
 * Gemini supports token streaming, but the ring-1 {@link ModelProvider#generate} contract is blocking
 * (request → {@link ModelResponse}). Token-level streaming therefore cannot be expressed through the
 * current seam; the {@code agent-orchestration} streaming entry point streams at result granularity over
 * this blocking call, and a streaming ring-1 seam is the deferred reshape candidate (ADR-0012).
 */
public final class GeminiModelProvider implements ModelProvider {

    private final GeminiModelProfile profile;
    private final ModelTier tier;
    private final String modelName;

    private volatile ChatModel chatModel;

    public GeminiModelProvider(GeminiModelProfile profile, ModelTier tier) {
        this.profile = profile;
        this.tier = tier;
        this.modelName = profile.model(tier);
    }

    /** A {@link ModelRouter} backed by one {@link GeminiModelProvider} per tier of the profile. */
    public static ModelRouter routerFor(GeminiModelProfile profile) {
        EnumMap<ModelTier, ModelProvider> m = new EnumMap<>(ModelTier.class);
        for (ModelTier t : ModelTier.values()) {
            m.put(t, new GeminiModelProvider(profile, t));
        }
        return ModelRouter.of(m);
    }

    /** A router from the environment-resolved profile. */
    public static ModelRouter fromEnvironment() {
        return routerFor(GeminiModelProfile.fromEnvironment());
    }

    @Override
    public String name() {
        return "gemini:" + modelName + " (" + tier + ")";
    }

    @Override
    public boolean available() {
        return profile.enabled()
                && profile.apiKey() != null && !profile.apiKey().isBlank()
                && modelName != null && !modelName.isBlank();
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        if (!available()) {
            throw new ModelError("gemini provider not available for tier " + tier
                    + " (disabled, no api key, or no model mapped)");
        }
        try {
            List<ChatMessage> messages = new ArrayList<>(2);
            String system = request.system();
            if (request.jsonFormat()) {
                String json = "Respond with valid JSON only, no prose or code fences.";
                system = (system == null || system.isBlank()) ? json : system + "\n\n" + json;
            }
            if (system != null && !system.isBlank()) {
                messages.add(SystemMessage.from(system));
            }
            messages.add(UserMessage.from(request.prompt()));
            ChatResponse response = chatModel().chat(messages);
            return new ModelResponse(response.aiMessage().text(),
                    inputTokens(response.tokenUsage()), outputTokens(response.tokenUsage()));
        } catch (ModelError e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ModelError("gemini generation failed for tier " + tier, e);
        }
    }

    private static int inputTokens(TokenUsage usage) {
        return (usage == null || usage.inputTokenCount() == null) ? -1 : usage.inputTokenCount();
    }

    private static int outputTokens(TokenUsage usage) {
        return (usage == null || usage.outputTokenCount() == null) ? -1 : usage.outputTokenCount();
    }

    private ChatModel chatModel() {
        ChatModel m = chatModel;
        if (m == null) {
            synchronized (this) {
                if ((m = chatModel) == null) {
                    m = chatModel = GoogleAiGeminiChatModel.builder()
                            .apiKey(profile.apiKey())
                            .modelName(modelName)
                            .build();
                }
            }
        }
        return m;
    }
}
