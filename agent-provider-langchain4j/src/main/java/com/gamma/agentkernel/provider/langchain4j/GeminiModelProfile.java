package com.gamma.agentkernel.provider.langchain4j;

import com.gamma.agentkernel.model.ModelTier;

import java.util.EnumMap;
import java.util.Map;

/**
 * A deployment's Google AI Gemini bundle — the per-{@link ModelTier} model-name mapping, plus the API key
 * and a master enable flag. The hosted-provider counterpart of the Ollama {@code ModelProfile}.
 *
 * <h3>Abstain-by-default</h3>
 * {@link #enabled} defaults to {@code false} and the API key is empty unless configured, so on CI / a
 * vanilla deployment {@link GeminiModelProvider#available()} returns {@code false} and no network call is
 * ever made. A deployment opts in explicitly with a key (and the enable flag).
 *
 * <h3>Selection (system property → upper-snake env var fallback)</h3>
 * <pre>
 *   -Dagentkernel.gemini.enabled=true|false        (default: false)
 *   -Dagentkernel.gemini.apiKey=...                (or env GEMINI_API_KEY / GOOGLE_API_KEY)
 *   -Dagentkernel.gemini.model.small=...           (default: gemini-2.0-flash-lite)
 *   -Dagentkernel.gemini.model.medium=...          (default: gemini-2.0-flash)
 *   -Dagentkernel.gemini.model.large=...           (default: gemini-2.5-pro)
 * </pre>
 */
public record GeminiModelProfile(boolean enabled, String apiKey, Map<ModelTier, String> models) {

    public GeminiModelProfile {
        models = (models == null) ? Map.of() : Map.copyOf(models);
    }

    /** The model name bound to a tier, or {@code null} when the tier is unmapped. */
    public String model(ModelTier tier) {
        return models.get(tier);
    }

    /** The default tier→model mapping (current Gemini family); names are overridable via properties. */
    public static Map<ModelTier, String> defaultModels() {
        return tiers("gemini-2.0-flash-lite", "gemini-2.0-flash", "gemini-2.5-pro");
    }

    private static Map<ModelTier, String> tiers(String small, String medium, String large) {
        EnumMap<ModelTier, String> m = new EnumMap<>(ModelTier.class);
        m.put(ModelTier.SMALL, small);
        m.put(ModelTier.MEDIUM, medium);
        m.put(ModelTier.LARGE, large);
        return m;
    }

    /** Resolve the active profile from system properties / env vars (see class javadoc). */
    public static GeminiModelProfile fromEnvironment() {
        boolean enabled = Boolean.parseBoolean(prop("agentkernel.gemini.enabled", "false"));
        String apiKey = prop("agentkernel.gemini.apiKey", null);
        if (apiKey == null) {
            apiKey = env("GEMINI_API_KEY");
        }
        if (apiKey == null) {
            apiKey = env("GOOGLE_API_KEY");
        }
        Map<ModelTier, String> models = tiers(
                prop("agentkernel.gemini.model.small", "gemini-2.0-flash-lite"),
                prop("agentkernel.gemini.model.medium", "gemini-2.0-flash"),
                prop("agentkernel.gemini.model.large", "gemini-2.5-pro"));
        return new GeminiModelProfile(enabled, apiKey, models);
    }

    private static String prop(String key, String def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            v = System.getenv(key.toUpperCase().replace('.', '_'));
        }
        return (v == null || v.isBlank()) ? def : v;
    }

    private static String env(String key) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? null : v;
    }
}
