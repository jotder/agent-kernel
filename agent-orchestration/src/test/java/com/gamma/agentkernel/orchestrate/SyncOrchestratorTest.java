package com.gamma.agentkernel.orchestrate;

import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.agent.AgentRequest;
import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.agent.Capability;
import com.gamma.agentkernel.agent.CapabilityRegistry;
import com.gamma.agentkernel.agent.CapabilitySpec;
import com.gamma.agentkernel.model.ModelTier;
import com.gamma.agentkernel.observe.AgentCompleted;
import com.gamma.agentkernel.observe.AgentEvent;
import com.gamma.agentkernel.observe.AuditSink;
import com.gamma.agentkernel.reason.ConfidenceEstimator;
import com.gamma.agentkernel.reason.EscalationPolicy;
import com.gamma.agentkernel.reason.EscalationRung;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the assembled sync pipeline: resolve → escalate (confidence gate) → audit exactly once. Uses
 * tiny in-test stubs and {@link AgentContext#builder()} — no model, fully CPU-only. This is the same
 * observable contract UCC's {@code UccAssistAgent} relies on, so it doubles as the regression net for
 * the extraction.
 */
class SyncOrchestratorTest {

    private static final String ID = "explain";

    /** A capability that returns a canned result (ignores tier; sufficient for the orchestrator contract). */
    private record StubCapability(CapabilitySpec spec, AgentResult canned) implements Capability {
        @Override public AgentResult run(AgentRequest request, AgentContext ctx) { return canned; }
    }

    private static CapabilitySpec spec(double threshold) {
        return new CapabilitySpec(ID, 1, "stub", ModelTier.SMALL, threshold,
                Duration.ofSeconds(5), Set.of(), Set.of());
    }

    private static AgentResult okResult() {
        return AgentResult.ok(ID, 1, "answer", List.of(), List.of(), "because", 0.0, ModelTier.SMALL);
    }

    /** A capturing audit sink + the orchestrator under test, wired with the given pieces. */
    private record Harness(SyncOrchestrator orchestrator, AgentContext ctx, List<AgentEvent> events) { }

    private static Harness harness(Capability cap, ConfidenceEstimator estimator, EscalationPolicy policy) {
        List<AgentEvent> events = new ArrayList<>();
        AuditSink sink = events::add;
        AgentContext ctx = AgentContext.builder().audit(sink).build();
        CapabilityRegistry registry = CapabilityRegistry.of(cap == null ? List.of() : List.of(cap));
        return new Harness(new SyncOrchestrator(registry, estimator, policy), ctx, events);
    }

    private static AgentCompleted onlyEvent(List<AgentEvent> events) {
        assertEquals(1, events.size(), "exactly one audit event per call");
        return (AgentCompleted) events.get(0);
    }

    @Test
    void unknownCapabilityIsUnsupportedAndStillAudited() {
        Harness h = harness(null, (req, cand, ctx) -> 1.0,
                new EscalationPolicy(List.of(new EscalationRung.Abstain())));

        AgentResult r = h.orchestrator().run(new AgentRequest("no-such", Map.of(), Map.of(), null), h.ctx());

        assertEquals(AgentResult.Status.UNSUPPORTED, r.status());
        AgentCompleted e = onlyEvent(h.events());
        assertEquals("no-such", e.capabilityId());
        assertEquals(AgentResult.Status.UNSUPPORTED, e.status());
        assertEquals(0, e.evidenceCount());
        assertEquals(false, e.modelInvoked(), "unsupported carries no serving tier");
    }

    @Test
    void okAboveThresholdIsReturnedWithEstimatedConfidence() {
        Capability cap = new StubCapability(spec(0.5), okResult());
        Harness h = harness(cap, (req, cand, ctx) -> 0.9,
                new EscalationPolicy(List.of(new EscalationRung.Abstain())));

        AgentResult r = h.orchestrator().run(new AgentRequest(ID, Map.of("entity", "EVENTS"), Map.of(), null),
                h.ctx());

        assertEquals(AgentResult.Status.OK, r.status());
        assertEquals(0.9, r.confidence(), 1e-9, "estimator's confidence rides through on acceptance");
        AgentCompleted e = onlyEvent(h.events());
        assertEquals(AgentResult.Status.OK, e.status());
        assertEquals(0.9, e.confidence(), 1e-9);
        assertTrue(e.contextKeys().contains("entity"), "screen-context keys are recorded (keys only)");
    }

    @Test
    void okBelowThresholdAbstainsToUnavailable() {
        Capability cap = new StubCapability(spec(0.5), okResult());
        Harness h = harness(cap, (req, cand, ctx) -> 0.30,
                new EscalationPolicy(List.of(new EscalationRung.Abstain())));

        AgentResult r = h.orchestrator().run(new AgentRequest(ID, Map.of(), Map.of(), null), h.ctx());

        assertEquals(AgentResult.Status.UNAVAILABLE, r.status(), "below-threshold abstains, not ships a guess");
        AgentCompleted e = onlyEvent(h.events());
        assertEquals(AgentResult.Status.UNAVAILABLE, e.status());
    }

    @Test
    void auditsThroughTheContextSink() {
        Capability cap = new StubCapability(spec(0.5), okResult());
        List<AgentEvent> events = new ArrayList<>();
        AuditSink sink = events::add;
        AgentContext ctx = AgentContext.builder().audit(sink).build();
        SyncOrchestrator orch = new SyncOrchestrator(CapabilityRegistry.of(List.of(cap)),
                (req, cand, c) -> 0.9, new EscalationPolicy(List.of(new EscalationRung.Abstain())));

        orch.run(new AgentRequest(ID, Map.of(), Map.of(), null), ctx);

        assertEquals(1, events.size());
        assertSame(ctx.audit(), sink, "orchestrator emits via the context's own sink");
    }
}
