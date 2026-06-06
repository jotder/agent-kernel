package com.gamma.agentkernel.provider.langchain4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamma.agentkernel.error.ModelError;
import com.gamma.agentkernel.model.ModelProvider;
import com.gamma.agentkernel.model.ModelRequest;
import com.gamma.agentkernel.model.ModelRouter;
import com.gamma.agentkernel.model.ModelTier;

/**
 * Network-free tests of the Gemini provider's abstain-safe contract: constructing providers and a router
 * touches no network and needs no key, {@link GeminiModelProvider#available()} is a pure config check, and
 * an unavailable provider throws {@link ModelError} on {@link GeminiModelProvider#generate} rather than
 * calling out. No live model is contacted.
 */
class GeminiModelProviderTest {

    private static GeminiModelProfile profile(boolean enabled, String apiKey) {
        Map<ModelTier, String> models = new EnumMap<>(ModelTier.class);
        models.put(ModelTier.SMALL, "gemini-2.0-flash-lite");
        models.put(ModelTier.MEDIUM, "gemini-2.0-flash");
        models.put(ModelTier.LARGE, "gemini-2.5-pro");
        return new GeminiModelProfile(enabled, apiKey, models);
    }

    @Test
    void unavailableWhenDisabledOrNoKey() {
        assertFalse(new GeminiModelProvider(profile(false, "k"), ModelTier.SMALL).available(),
                "disabled profile is never available");
        assertFalse(new GeminiModelProvider(profile(true, null), ModelTier.MEDIUM).available(),
                "no api key is never available");
        assertFalse(new GeminiModelProvider(profile(true, "  "), ModelTier.LARGE).available(),
                "blank api key is never available");
    }

    @Test
    void availableWhenEnabledWithKeyAndMappedModel_butMakesNoNetworkCall() {
        GeminiModelProvider p = new GeminiModelProvider(profile(true, "secret-key"), ModelTier.SMALL);
        assertTrue(p.available());
        assertEquals("gemini:gemini-2.0-flash-lite (SMALL)", p.name());
    }

    @Test
    void generateOnUnavailableThrowsRatherThanCallingOut() {
        GeminiModelProvider p = new GeminiModelProvider(profile(false, "k"), ModelTier.SMALL);
        assertThrows(ModelError.class,
                () -> p.generate(ModelRequest.text(ModelTier.SMALL, null, "hi")));
    }

    @Test
    void routerBindsOneProviderPerTier() {
        ModelRouter router = GeminiModelProvider.routerFor(profile(true, "secret-key"));
        assertEquals("gemini:gemini-2.0-flash-lite (SMALL)", router.providerFor(ModelTier.SMALL).name());
        assertEquals("gemini:gemini-2.5-pro (LARGE)", router.providerFor(ModelTier.LARGE).name());
        assertTrue(router.anyAvailable());
    }

    @Test
    void fromEnvironmentIsAbstainSafeByDefault() {
        // No system properties / env set in CI → disabled, no key → not available, no network.
        ModelProvider p = GeminiModelProvider.fromEnvironment().providerFor(ModelTier.SMALL);
        assertFalse(p.available());
    }
}
