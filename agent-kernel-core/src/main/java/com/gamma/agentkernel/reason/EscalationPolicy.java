package com.gamma.agentkernel.reason;

import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.agent.AgentRequest;
import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.agent.Capability;
import com.gamma.agentkernel.agent.CapabilitySpec;
import com.gamma.agentkernel.model.ModelTier;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Runs a capability with confidence-driven escalation. The flow: attempt at the effective tier →
 * estimate confidence → if the result is OK and confidence ≥ the capability's threshold, return it
 * (with the estimated confidence). Otherwise walk the configured {@link EscalationRung}s in order —
 * {@link EscalationRung.BumpModelTier} re-attempts at the next tier, {@link EscalationRung.HumanHandoff}
 * parks for review, {@link EscalationRung.Abstain} returns UNAVAILABLE. If the rungs are exhausted
 * without acceptance, the policy abstains.
 *
 * <p>This is a K1 <em>ingredient</em>, not the assembled orchestrator (deferred to R1).
 */
public final class EscalationPolicy {

    private final List<EscalationRung> rungs;

    public EscalationPolicy(List<EscalationRung> rungs) {
        this.rungs = (rungs == null) ? List.of() : List.copyOf(rungs);
    }

    public AgentResult run(Capability capability, AgentRequest request, AgentContext baseCtx,
                           ConfidenceEstimator estimator) {
        CapabilitySpec spec = capability.spec();
        double threshold = spec.confidenceThreshold();

        ModelTier tier = baseCtx.effectiveTier(spec.defaultTier());
        AgentContext ctx = baseCtx.withEffectiveTier(tier);
        AgentResult result = capability.run(request, ctx);
        double confidence = estimator.estimate(request, result, ctx);
        if (accepts(result, confidence, threshold)) return result.withConfidence(confidence);

        for (EscalationRung rung : rungs) {
            switch (rung) {
                case EscalationRung.BumpModelTier ignored -> {
                    Optional<ModelTier> next = ctx.models().next(tier);
                    if (next.isEmpty()) continue; // already at the top tier; try the next rung
                    tier = next.get();
                    ctx = baseCtx.withEffectiveTier(tier);
                    result = capability.run(request, ctx);
                    confidence = estimator.estimate(request, result, ctx);
                    if (accepts(result, confidence, threshold)) return result.withConfidence(confidence);
                }
                case EscalationRung.HumanHandoff handoff -> {
                    return new AgentResult(spec.id(), spec.version(), AgentResult.Status.UNAVAILABLE,
                            null, List.of(), List.of(), null, confidence, false, tier, null, null,
                            "handed off for human review",
                            Map.of("escalation", "human-handoff", "queue", handoff.queue()));
                }
                case EscalationRung.Abstain ignored -> {
                    return abstain(spec, confidence, threshold);
                }
            }
        }
        return abstain(spec, confidence, threshold);
    }

    private static boolean accepts(AgentResult result, double confidence, double threshold) {
        return result.status() == AgentResult.Status.OK && confidence >= threshold;
    }

    private static AgentResult abstain(CapabilitySpec spec, double confidence, double threshold) {
        return AgentResult.unavailable(spec.id(), "confidence " + fmt(confidence)
                + " below threshold " + fmt(threshold) + "; abstaining");
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }
}
