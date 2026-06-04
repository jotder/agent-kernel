package com.gamma.agentkernel.eval;

import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.agent.AgentRequest;
import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.agent.Capability;
import com.gamma.agentkernel.agent.CapabilitySpec;
import com.gamma.agentkernel.model.ModelTier;
import com.gamma.agentkernel.tool.CredibilityTier;
import com.gamma.agentkernel.tool.Evidence;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A trivial deterministic capability used to exercise the eval harness: echoes the user text, abstains on empty. */
final class EchoCapability implements Capability {

    private static final CapabilitySpec SPEC = new CapabilitySpec(
            "echo", 1, "echoes the input", ModelTier.SMALL, 0.0,
            Duration.ofSeconds(1), Set.of(), Set.of());

    @Override
    public CapabilitySpec spec() {
        return SPEC;
    }

    @Override
    public AgentResult run(AgentRequest request, AgentContext ctx) {
        String text = request.userText();
        if (text == null || text.isBlank()) {
            return AgentResult.unavailable("echo", "no input — abstaining");
        }
        Evidence ev = Evidence.of(text, CredibilityTier.USER_PROVIDED, "echo:src");
        return AgentResult.draft("echo", 1, "echo: " + text, List.of(ev), List.of(),
                "echoed the user's input", 1.0, ctx.effectiveTier(SPEC.defaultTier()),
                Map.of("echo", text));
    }
}
