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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the streaming pipeline: resolve → escalate (confidence gate) → stream answer chunks → complete →
 * audit exactly once. Same ring-1 ingredients and the same observable audit contract as
 * {@link SyncOrchestrator}; chunks reconstruct the answer (result-granularity streaming, ADR-0012).
 */
class StreamingOrchestratorTest {

    private static final String ID = "explain";

    private record StubCapability(CapabilitySpec spec, AgentResult canned) implements Capability {
        @Override public AgentResult run(AgentRequest request, AgentContext ctx) { return canned; }
    }

    private static CapabilitySpec spec(double threshold) {
        return new CapabilitySpec(ID, 1, "stub", ModelTier.SMALL, threshold,
                Duration.ofSeconds(5), Set.of(), Set.of());
    }

    /** A listener that records every chunk and the terminal result. */
    private static final class Recorder implements AgentStreamListener {
        final List<String> chunks = new ArrayList<>();
        AgentResult completed;
        @Override public void onChunk(String delta) { chunks.add(delta); }
        @Override public void onComplete(AgentResult result) { completed = result; }
        String joined() { return String.join("", chunks); }
    }

    private static StreamingOrchestrator orchestrator(Capability cap, ConfidenceEstimator estimator,
                                                      int chunkChars) {
        CapabilityRegistry registry = CapabilityRegistry.of(cap == null ? List.of() : List.of(cap));
        return new StreamingOrchestrator(registry, estimator,
                new EscalationPolicy(List.of(new EscalationRung.Abstain())), chunkChars);
    }

    @Test
    void streamsAnswerInChunksThenCompletesAndAuditsOnce() {
        String answer = "Lodha Amara leads on price; the figure is anchored on the RERA filing.";
        Capability cap = new StubCapability(spec(0.5),
                AgentResult.ok(ID, 1, answer, List.of(), List.of(), "because", 0.0, ModelTier.SMALL));
        List<AgentEvent> events = new ArrayList<>();
        AuditSink sink = events::add;
        AgentContext ctx = AgentContext.builder().audit(sink).build();
        Recorder rec = new Recorder();

        AgentResult r = orchestrator(cap, (req, cand, c) -> 0.9, 16)
                .run(new AgentRequest(ID, Map.of("entity", "EVENTS"), Map.of(), null), ctx, rec);

        assertEquals(AgentResult.Status.OK, r.status());
        assertEquals(0.9, r.confidence(), 1e-9);
        assertTrue(rec.chunks.size() > 1, "a long answer streams as several chunks");
        assertEquals(answer, rec.joined(), "chunks reconstruct the answer exactly");
        assertEquals(r, rec.completed, "onComplete carries the terminal neutral result");

        assertEquals(1, events.size(), "exactly one audit event");
        AgentCompleted e = (AgentCompleted) events.get(0);
        assertEquals(AgentResult.Status.OK, e.status());
        assertTrue(e.contextKeys().contains("entity"));
    }

    @Test
    void unknownCapabilityEmitsNoChunksButCompletesAndAudits() {
        List<AgentEvent> events = new ArrayList<>();
        AgentContext ctx = AgentContext.builder().audit((AuditSink) events::add).build();
        Recorder rec = new Recorder();

        AgentResult r = orchestrator(null, (req, cand, c) -> 1.0, 16)
                .run(new AgentRequest("no-such", Map.of(), Map.of(), null), ctx, rec);

        assertEquals(AgentResult.Status.UNSUPPORTED, r.status());
        assertTrue(rec.chunks.isEmpty(), "no answer ⇒ no chunks");
        assertEquals(r, rec.completed);
        assertEquals(1, events.size());
    }

    @Test
    void belowThresholdAbstainsWithNoChunks() {
        Capability cap = new StubCapability(spec(0.5),
                AgentResult.ok(ID, 1, "a guess", List.of(), List.of(), "because", 0.0, ModelTier.SMALL));
        List<AgentEvent> events = new ArrayList<>();
        AgentContext ctx = AgentContext.builder().audit((AuditSink) events::add).build();
        Recorder rec = new Recorder();

        AgentResult r = orchestrator(cap, (req, cand, c) -> 0.30, 16)
                .run(new AgentRequest(ID, Map.of(), Map.of(), null), ctx, rec);

        assertEquals(AgentResult.Status.UNAVAILABLE, r.status(), "below threshold abstains, no guess streamed");
        assertTrue(rec.chunks.isEmpty());
        assertEquals(1, events.size());
    }
}
