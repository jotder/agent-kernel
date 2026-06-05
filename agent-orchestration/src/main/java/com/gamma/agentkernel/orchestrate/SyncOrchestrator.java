package com.gamma.agentkernel.orchestrate;

import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.agent.AgentRequest;
import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.agent.Capability;
import com.gamma.agentkernel.agent.CapabilityRegistry;
import com.gamma.agentkernel.observe.AgentCompleted;
import com.gamma.agentkernel.reason.ConfidenceEstimator;
import com.gamma.agentkernel.reason.EscalationPolicy;

import java.util.Objects;

/**
 * The assembled <em>synchronous</em> agent pipeline — the orchestrator K1 deferred to R1
 * (see {@link CapabilityRegistry}'s note and ADR-0009). It composes the ring-1 ingredients into the
 * one request → result flow a synchronous consumer needs:
 *
 * <ol>
 *   <li><b>Resolve</b> the capability bound to {@link AgentRequest#capabilityId()}; an unknown id
 *       yields {@link AgentResult#unsupported(String)} (still audited, so callers see the miss).</li>
 *   <li><b>Run with escalation</b> via {@link EscalationPolicy#run} — attempt at the effective tier,
 *       estimate confidence with the supplied {@link ConfidenceEstimator}, surface if it clears the
 *       capability's threshold, else walk the policy's rungs (e.g. abstain to UNAVAILABLE rather than
 *       ship a low-confidence guess).</li>
 *   <li><b>Audit</b> exactly one {@link AgentCompleted} to {@link AgentContext#audit()} — keys and
 *       summaries only, never data-plane values (ADR-0008).</li>
 * </ol>
 *
 * <p>This is precisely the pipeline UCC hand-rolled inside its {@code UccAssistAgent}; R1 lifts it here
 * so a second consumer composes the same ingredients rather than re-deriving them. It depends on
 * <em>no</em> ring-1 type beyond what already existed — the design test that the K1 seam was right.
 *
 * <p><b>Left to the caller</b> (these are app/transport concerns, not orchestration): mapping the
 * neutral {@link AgentResult} onto a wire/UI type, any human-readable logging (wrap the {@link
 * com.gamma.agentkernel.observe.AuditSink} to add it), short-circuiting before the agent exists, and
 * catching unexpected {@link RuntimeException}s. Async and streaming variants are separate entry points
 * added when a second consumer (CVVE/CxO) shapes them; this class stays synchronous.
 */
public final class SyncOrchestrator {

    private final CapabilityRegistry registry;
    private final ConfidenceEstimator estimator;
    private final EscalationPolicy escalation;

    /**
     * @param registry   the id → capability table to resolve against
     * @param estimator  scores each attempt's confidence for the escalation gate
     * @param escalation the policy (and its rungs) applied when an attempt is below threshold
     */
    public SyncOrchestrator(CapabilityRegistry registry, ConfidenceEstimator estimator,
                            EscalationPolicy escalation) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.estimator = Objects.requireNonNull(estimator, "estimator");
        this.escalation = Objects.requireNonNull(escalation, "escalation");
    }

    /** Resolve → escalate → audit, returning the neutral result for the caller to map. */
    public AgentResult run(AgentRequest request, AgentContext ctx) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(ctx, "ctx");
        long startNanos = System.nanoTime();
        Capability capability = registry.get(request.capabilityId()).orElse(null);
        AgentResult result = (capability == null)
                ? AgentResult.unsupported(request.capabilityId())
                : escalation.run(capability, request, ctx, estimator);
        ctx.audit().emit(completed(request, result, startNanos));
        return result;
    }

    /**
     * Build the keys-only {@link AgentCompleted} summary from the request + result (ADR-0008): no
     * record contents, no evidence values, no prompts. {@code repairRounds} reads the conventional
     * {@code data["repaired"]} boolean a capability may set; token counts are not tracked here (they
     * arrive via {@code ModelCalled} events when wired).
     */
    private static AgentCompleted completed(AgentRequest request, AgentResult result, long startNanos) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        boolean repaired = Boolean.TRUE.equals(result.data().get("repaired"));
        return new AgentCompleted(request.capabilityId(), System.currentTimeMillis(), result.status(),
                result.evidence().size(), durationMs, request.screenContext().keySet(), result.servedBy(),
                result.servedBy() != null, repaired ? 1 : 0, result.confidence(), 0, 0);
    }
}
