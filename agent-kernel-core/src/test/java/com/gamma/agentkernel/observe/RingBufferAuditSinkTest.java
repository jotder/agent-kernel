package com.gamma.agentkernel.observe;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingBufferAuditSinkTest {

    @Test
    void evictsOldestBeyondCapacityAndReturnsNewestFirst() {
        RingBufferAuditSink sink = new RingBufferAuditSink(2);
        sink.emit(new AgentStarted("a", 1L, Set.of()));
        sink.emit(new AgentStarted("b", 2L, Set.of()));
        sink.emit(new AgentStarted("c", 3L, Set.of()));

        assertEquals(2, sink.size());
        List<AgentEvent> recent = sink.recent(10);
        assertEquals(List.of("c", "b"), recent.stream().map(AgentEvent::capabilityId).toList());
    }

    @Test
    void recentIsBoundedByN() {
        RingBufferAuditSink sink = new RingBufferAuditSink(5);
        for (int i = 0; i < 5; i++) sink.emit(new AgentStarted("c" + i, i, Set.of()));
        assertEquals(2, sink.recent(2).size());
    }
}
