package com.gamma.agentkernel.tool;

import com.gamma.agentkernel.tool.GroundingGuard.Verdict;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumericGroundingGuardTest {

    private final GroundingGuard guard = new NumericGroundingGuard();

    private static Evidence ev(Object value) {
        return Evidence.of(value, CredibilityTier.DERIVED, "src");
    }

    @Test
    void groundedWhenEveryNumberAppearsInEvidence() {
        Verdict v = guard.check("Throughput was 1200 rows in 35 ms.",
                List.of(ev(Map.of("rows", 1200, "ms", 35))));
        assertTrue(v.grounded());
        assertTrue(v.ungrounded().isEmpty());
    }

    @Test
    void flagsAnUngroundedFigure() {
        Verdict v = guard.check("Throughput was 9999 rows.", List.of(ev(Map.of("rows", 1200))));
        assertFalse(v.grounded());
        assertEquals(List.of("9999"), v.ungrounded());
    }

    @Test
    void percentGroundsAgainstRatio() {
        Verdict v = guard.check("Error rate was 1.7%.", List.of(ev(0.017)));
        assertTrue(v.grounded());
    }

    @Test
    void nonNumericNarrationIsGrounded() {
        Verdict v = guard.check("The pipeline completed successfully.", List.of(ev("ok")));
        assertTrue(v.grounded());
    }

    @Test
    void emptyNarrationIsNotGrounded() {
        Verdict v = guard.check("  ", List.of(ev(1)));
        assertFalse(v.grounded());
    }
}
