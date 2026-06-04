package com.gamma.agentkernel.provider.ollama;

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
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.TokenUsage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A LangChain4j-backed local {@link ModelProvider} over Ollama (ported from UCC). One instance binds a
 * single {@link ModelTier}/model from a {@link ModelProfile}.
 *
 * <h3>Lazy &amp; abstain-safe</h3>
 * The chat model is built on first {@link #generate} only — never in the constructor — so merely
 * constructing providers touches no network. {@link #available()} is a pure configuration check.
 */
public final class OllamaModelProvider implements ModelProvider {

    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final ModelProfile profile;
    private final ModelTier tier;
    private final String modelName;

    private volatile ChatModel textModel;
    private volatile ChatModel jsonModel;

    public OllamaModelProvider(ModelProfile profile, ModelTier tier) {
        this.profile = profile;
        this.tier = tier;
        this.modelName = profile.model(tier);
    }

    /** A {@link ModelRouter} backed by one {@link OllamaModelProvider} per tier of the profile. */
    public static ModelRouter routerFor(ModelProfile profile) {
        EnumMap<ModelTier, ModelProvider> m = new EnumMap<>(ModelTier.class);
        for (ModelTier t : ModelTier.values()) m.put(t, new OllamaModelProvider(profile, t));
        return ModelRouter.of(m);
    }

    /** A router from the environment-resolved profile. */
    public static ModelRouter fromEnvironment() {
        return routerFor(ModelProfile.fromEnvironment());
    }

    @Override
    public String name() {
        return "ollama:" + modelName + " (" + tier + ")";
    }

    @Override
    public boolean available() {
        return profile.enabled()
                && modelName != null && !modelName.isBlank()
                && profile.baseUrl() != null && !profile.baseUrl().isBlank();
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        if (!available()) {
            throw new ModelError("ollama provider not available for tier " + tier
                    + " (disabled or no model mapped)");
        }
        try {
            ChatModel model = request.jsonFormat() ? jsonModel() : textModel();
            List<ChatMessage> messages = new ArrayList<>(2);
            if (request.system() != null && !request.system().isBlank()) {
                messages.add(SystemMessage.from(request.system()));
            }
            messages.add(UserMessage.from(request.prompt()));
            ChatResponse response = model.chat(messages);
            return new ModelResponse(response.aiMessage().text(),
                    inputTokens(response.tokenUsage()), outputTokens(response.tokenUsage()));
        } catch (ModelError e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ModelError("ollama generation failed for tier " + tier, e);
        }
    }

    private static int inputTokens(TokenUsage usage) {
        return (usage == null || usage.inputTokenCount() == null) ? -1 : usage.inputTokenCount();
    }

    private static int outputTokens(TokenUsage usage) {
        return (usage == null || usage.outputTokenCount() == null) ? -1 : usage.outputTokenCount();
    }

    private ChatModel textModel() {
        ChatModel m = textModel;
        if (m == null) {
            synchronized (this) {
                if ((m = textModel) == null) m = textModel = build(ResponseFormat.TEXT);
            }
        }
        return m;
    }

    private ChatModel jsonModel() {
        ChatModel m = jsonModel;
        if (m == null) {
            synchronized (this) {
                if ((m = jsonModel) == null) m = jsonModel = build(ResponseFormat.JSON);
            }
        }
        return m;
    }

    private ChatModel build(ResponseFormat format) {
        return OllamaChatModel.builder()
                .baseUrl(profile.baseUrl())
                .modelName(modelName)
                .timeout(TIMEOUT)
                .responseFormat(format)
                .build();
    }
}
