package com.gamma.agentkernel.reason;

import com.gamma.agentkernel.error.SystemError;
import com.gamma.agentkernel.error.ValidationError;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeadlineTest {

    @Test
    void returnsValueWithinLimit() {
        String v = Deadline.call(Duration.ofSeconds(2), () -> "done");
        assertEquals("done", v);
    }

    @Test
    void throwsSystemErrorOnTimeout() {
        assertThrows(SystemError.class, () -> Deadline.call(Duration.ofMillis(100), () -> {
            Thread.sleep(2000);
            return "late";
        }));
    }

    @Test
    void propagatesAgentErrorFromTask() {
        assertThrows(ValidationError.class, () -> Deadline.call(Duration.ofSeconds(1), () -> {
            throw new ValidationError("bad input");
        }));
    }

    @Test
    void wrapsOtherFailuresAsSystemError() {
        assertThrows(SystemError.class, () -> Deadline.call(Duration.ofSeconds(1), () -> {
            throw new IllegalArgumentException("boom");
        }));
    }
}
