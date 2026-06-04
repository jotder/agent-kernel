package com.gamma.agentkernel.eval;

import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.agent.CapabilityRegistry;
import com.gamma.agentkernel.model.ModelRequest;
import com.gamma.agentkernel.model.ModelTier;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the eval harness end-to-end with zero apps and no Ollama — the kernel's own CI safety net. */
class HarnessSelfTest {

    private CapabilityRegistry registry() {
        return CapabilityRegistry.of(List.of(new EchoCapability()));
    }

    private AgentContext ctx() {
        return AgentContext.builder().build();
    }

    private List<EvalCase> cases() {
        return EvalCaseLoader.fromResource("/eval/echo/cases.json");
    }

    @Test
    void runsAllFixturesGreen() {
        List<EvalCase> cases = cases();
        assertEquals(3, cases.size());
        EvalReport report = new EvalRunner().run(registry(), ctx(), cases);
        assertTrue(report.allPassed(), () -> "failures: " + report.failures());
        assertEquals(3, report.passed());
    }

    @TestFactory
    Stream<DynamicTest> fixturesAsDynamicTests() {
        return Evals.asTests(registry(), ctx(), cases());
    }

    @Test
    void fakeModelProviderIsDeterministic() {
        FakeModelProvider fake = FakeModelProvider.builder()
                .onPromptContains("ping", "pong")
                .defaultResponse("default")
                .build();
        assertTrue(fake.available());
        assertEquals("pong", fake.generate(ModelRequest.text(ModelTier.SMALL, null, "ping?")).text());
        assertEquals("default", fake.generate(ModelRequest.text(ModelTier.SMALL, null, "other")).text());
    }
}
