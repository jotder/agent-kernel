package com.gamma.agentkernel.orchestrate;

import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.agent.AgentRequest;
import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.agent.Capability;
import com.gamma.agentkernel.agent.CapabilityRegistry;
import com.gamma.agentkernel.reason.ConfidenceEstimator;
import com.gamma.agentkernel.reason.EscalationPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The streaming sibling of {@link SyncOrchestrator} — the same assembled pipeline (resolve → escalate →
 * audit-once) but emitting the answer progressively to an {@link AgentStreamListener} before returning the
 * neutral {@link AgentResult}. Composed from the <em>same</em> ring-1 ingredients, with no ring-1 change
 * (ADR-0012).
 *
 * <h3>Result-granularity streaming (R1.3) vs token streaming (deferred, R1.5)</h3>
 * The ring-1 {@link com.gamma.agentkernel.model.ModelProvider#generate} contract is blocking
 * (request → response), so a capability produces its whole answer before this orchestrator can emit
 * anything. R1.3 therefore streams the <em>computed answer</em> in fixed-size chunks — enough to give a
 * consumer (CxO's {@code POST /api/chat} SSE) a progressive surface over the same neutral pipeline and
 * audit. <b>True token-level streaming requires a streaming model seam in ring-1</b> (a streaming
 * {@code generate}); that is the confirmed reshape candidate batched into the {@code 1.0} pass. The
 * {@link AgentStreamListener} contract is deliberately shaped so that reshape is additive, not breaking.
 */
public final class StreamingOrchestrator {

    /** Default answer-chunk size (characters) for result-granularity streaming. */
    static final int DEFAULT_CHUNK_CHARS = 48;

    private final CapabilityRegistry registry;
    private final ConfidenceEstimator estimator;
    private final EscalationPolicy escalation;
    private final int chunkChars;

    public StreamingOrchestrator(CapabilityRegistry registry, ConfidenceEstimator estimator,
                                 EscalationPolicy escalation) {
        this(registry, estimator, escalation, DEFAULT_CHUNK_CHARS);
    }

    public StreamingOrchestrator(CapabilityRegistry registry, ConfidenceEstimator estimator,
                                 EscalationPolicy escalation, int chunkChars) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.estimator = Objects.requireNonNull(estimator, "estimator");
        this.escalation = Objects.requireNonNull(escalation, "escalation");
        this.chunkChars = Math.max(1, chunkChars);
    }

    /**
     * Resolve → escalate → stream chunks → complete → audit-once. The audit is emitted in a {@code finally}
     * so exactly one {@link com.gamma.agentkernel.observe.AgentCompleted} is recorded even if the listener
     * throws. Returns the same neutral result a {@link SyncOrchestrator} would, for the caller to map.
     */
    public AgentResult run(AgentRequest request, AgentContext ctx, AgentStreamListener listener) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(listener, "listener");
        long startNanos = System.nanoTime();
        Capability capability = registry.get(request.capabilityId()).orElse(null);
        AgentResult result = (capability == null)
                ? AgentResult.unsupported(request.capabilityId())
                : escalation.run(capability, request, ctx, estimator);
        try {
            String answer = result.answer();
            if (result.status() == AgentResult.Status.OK && answer != null && !answer.isEmpty()) {
                for (String delta : chunkize(answer, chunkChars)) {
                    listener.onChunk(delta);
                }
            }
            listener.onComplete(result);
        } finally {
            ctx.audit().emit(Orchestrations.completed(request, result, startNanos));
        }
        return result;
    }

    /** Split into fixed-size character chunks; concatenation in order reconstructs the input exactly. */
    static List<String> chunkize(String s, int size) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < s.length(); i += size) {
            chunks.add(s.substring(i, Math.min(i + size, s.length())));
        }
        return chunks;
    }
}
