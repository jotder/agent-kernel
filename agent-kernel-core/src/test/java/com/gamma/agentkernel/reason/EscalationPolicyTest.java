package com.gamma.agentkernel.reason;

import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.agent.AgentRequest;
import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.agent.Capability;
import com.gamma.agentkernel.agent.CapabilitySpec;
import com.gamma.agentkernel.model.ModelTier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscalationPolicyTest {

    /** A capability that records the serving tier; the estimator scores confidence by that tier. */
    private static Capability tiered(double threshold) {
        CapabilitySpec spec = new CapabilitySpec("tiered", 1, "tier-sensitive", ModelTier.SMALL,
                threshold, Duration.ofSeconds(1), Set.of(), Set.of());
        return new Capability() {
            @Override public CapabilitySpec spec() { return spec; }
            @Override public AgentResult run(AgentRequest req, AgentContext ctx) {
                ModelTier tier = ctx.effectiveTier(spec.defaultTier());
                return AgentResult.ok("tiered", 1, "answer@" + tier, List.of(), List.of(),
                        "ok", 0.0, tier);
            }
        };
    }

    private static final ConfidenceEstimator BY_TIER = (req, candidate, ctx) -> switch (candidate.servedBy()) {
        case SMALL -> 0.2;
        case MEDIUM -> 0.6;
        case LARGE -> 0.9;
    };

    private static AgentResult run(List<EscalationRung> rungs, double threshold) {
        return new EscalationPolicy(rungs).run(tiered(threshold),
                new AgentRequest("tiered", null, null, "q"), AgentContext.builder().build(), BY_TIER);
    }

    @Test
    void abstainsWhenBelowThresholdAndNoBump() {
        AgentResult r = run(List.of(new EscalationRung.Abstain()), 0.5);
        assertEquals(AgentResult.Status.UNAVAILABLE, r.status());
        assertTrue(r.message().contains("abstaining"));
    }

    @Test
    void bumpModelTierReattemptsAtNextTier() {
        AgentResult r = run(List.of(new EscalationRung.BumpModelTier(), new EscalationRung.Abstain()), 0.5);
        assertEquals(AgentResult.Status.OK, r.status());
        assertEquals(ModelTier.MEDIUM, r.servedBy());
        assertEquals(0.6, r.confidence(), 1e-9);
    }

    @Test
    void bumpClimbsMultipleTiers() {
        AgentResult r = run(List.of(new EscalationRung.BumpModelTier(),
                new EscalationRung.BumpModelTier(), new EscalationRung.Abstain()), 0.8);
        assertEquals(AgentResult.Status.OK, r.status());
        assertEquals(ModelTier.LARGE, r.servedBy());
    }

    @Test
    void humanHandoffParks() {
        AgentResult r = run(List.of(new EscalationRung.HumanHandoff("review-queue")), 0.5);
        assertEquals(AgentResult.Status.UNAVAILABLE, r.status());
        assertEquals("human-handoff", r.data().get("escalation"));
        assertEquals("review-queue", r.data().get("queue"));
    }

    @Test
    void acceptsImmediatelyWhenAboveThreshold() {
        AgentResult r = run(List.of(new EscalationRung.Abstain()), 0.1);
        assertEquals(AgentResult.Status.OK, r.status());
        assertEquals(ModelTier.SMALL, r.servedBy());
    }
}
