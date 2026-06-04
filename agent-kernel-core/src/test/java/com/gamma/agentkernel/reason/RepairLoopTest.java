package com.gamma.agentkernel.reason;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairLoopTest {

    @Test
    void succeedsFirstRound() {
        RepairLoop.Result<String> r = RepairLoop.run(3, fb -> "good", raw -> raw);
        assertTrue(r.ok());
        assertEquals(1, r.rounds());
        assertFalse(r.repaired());
    }

    @Test
    void repairsOnSecondRound() {
        AtomicInteger n = new AtomicInteger();
        RepairLoop.Result<String> r = RepairLoop.run(3,
                fb -> n.getAndIncrement() == 0 ? "bad" : "good",
                raw -> {
                    if (!raw.equals("good")) throw new IllegalStateException("not good");
                    return raw;
                });
        assertTrue(r.ok());
        assertEquals(2, r.rounds());
        assertTrue(r.repaired());
        assertEquals(1, r.errors().size());
    }

    @Test
    void failsAfterMaxRounds() {
        RepairLoop.Result<String> r = RepairLoop.run(2, fb -> "bad", raw -> {
            throw new IllegalStateException("always bad");
        });
        assertFalse(r.ok());
        assertEquals(2, r.rounds());
        assertEquals(2, r.errors().size());
    }
}
