package com.gamma.agentkernel.model;

import com.gamma.agentkernel.error.ModelError;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRouterTest {

    @Test
    void nextWalksTiersThenEmpty() {
        ModelRouter r = ModelRouter.of(Map.of());
        assertEquals(Optional.of(ModelTier.MEDIUM), r.next(ModelTier.SMALL));
        assertEquals(Optional.of(ModelTier.LARGE), r.next(ModelTier.MEDIUM));
        assertEquals(Optional.empty(), r.next(ModelTier.LARGE));
    }

    @Test
    void unmappedTierResolvesToUnavailable() {
        ModelRouter r = ModelRouter.of(Map.of());
        ModelProvider p = r.providerFor(ModelTier.SMALL);
        assertFalse(p.available());
        assertThrows(ModelError.class, () -> p.generate(ModelRequest.text(ModelTier.SMALL, null, "x")));
        assertFalse(r.anyAvailable());
    }

    @Test
    void singleProviderServesAllTiers() {
        ModelProvider always = new ModelProvider() {
            @Override public String name() { return "fake"; }
            @Override public boolean available() { return true; }
            @Override public ModelResponse generate(ModelRequest request) { return ModelResponse.of("ok"); }
        };
        ModelRouter r = ModelRouter.of(always);
        assertTrue(r.anyAvailable());
        assertEquals("ok", r.providerFor(ModelTier.LARGE).generate(
                ModelRequest.text(ModelTier.LARGE, null, "x")).text());
    }
}
