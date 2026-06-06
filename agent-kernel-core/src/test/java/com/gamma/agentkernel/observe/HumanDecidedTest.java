package com.gamma.agentkernel.observe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the 1.1 {@link HumanDecided} event (ADR-0015): it is an {@link AgentEvent}, {@code of(...)} stamps the
 * time, and it flows through the default sink like any other variant — confirming the sealed-interface
 * extension is observed end-to-end.
 */
class HumanDecidedTest {

    @Test
    void ofStampsTimeAndCarriesIdentifiers() {
        long before = System.currentTimeMillis();
        HumanDecided e = HumanDecided.of("validate", "CORRECT", "reviewer-7", "req-123");

        assertInstanceOf(AgentEvent.class, e);
        assertEquals("validate", e.capabilityId());
        assertEquals("CORRECT", e.decision());
        assertEquals("reviewer-7", e.reviewer());
        assertEquals("req-123", e.reference());
        assertTrue(e.epochMillis() >= before, "of() stamps the current time");
    }

    @Test
    void flowsThroughTheRingBufferSink() {
        RingBufferAuditSink sink = new RingBufferAuditSink(8);
        sink.emit(HumanDecided.of("validate", "APPROVE", "reviewer-7", "case-9"));

        List<AgentEvent> recent = sink.recent(1);
        assertEquals(1, recent.size());
        HumanDecided stored = assertInstanceOf(HumanDecided.class, recent.get(0));
        assertEquals("APPROVE", stored.decision());
    }
}
