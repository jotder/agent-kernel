package com.gamma.agentkernel.spring;

import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.agent.AgentRequest;
import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.agent.Capability;
import com.gamma.agentkernel.agent.CapabilityRegistry;
import com.gamma.agentkernel.agent.CapabilitySpec;
import com.gamma.agentkernel.model.ModelTier;
import com.gamma.agentkernel.model.ModelRouter;
import com.gamma.agentkernel.observe.AuditSink;
import com.gamma.agentkernel.observe.RingBufferAuditSink;
import com.gamma.agentkernel.orchestrate.AgentStreamListener;
import com.gamma.agentkernel.orchestrate.StreamingOrchestrator;
import com.gamma.agentkernel.orchestrate.SyncOrchestrator;
import com.gamma.agentkernel.reason.ConfidenceEstimator;
import com.gamma.agentkernel.reason.EscalationPolicy;
import com.gamma.agentkernel.reason.EscalationRung;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link AgentKernelAutoConfiguration} through a real (application-free) Spring context — no
 * UCC/CxO on the classpath, only the kernel + Spring. Proves the companion assembles a working
 * {@link SyncOrchestrator} from {@link Capability} beans, and that each default backs off when the
 * application declares its own bean (the {@link org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean}
 * contract).
 */
class AgentKernelAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentKernelAutoConfiguration.class));

    @Test
    void assemblesOrchestratorAndDispatchesACapabilityBean() {
        runner.withUserConfiguration(EchoConfig.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(SyncOrchestrator.class)
                    .hasSingleBean(StreamingOrchestrator.class)
                    .hasSingleBean(CapabilityRegistry.class)
                    .hasSingleBean(ConfidenceEstimator.class)
                    .hasSingleBean(EscalationPolicy.class)
                    .hasSingleBean(ModelRouter.class)
                    .hasSingleBean(AuditSink.class);

            // The default router is abstain-safe: no provider configured ⇒ nothing available.
            assertThat(ctx.getBean(ModelRouter.class).anyAvailable()).isFalse();

            assertThat(ctx.getBean(CapabilityRegistry.class).ids()).containsExactly("echo");

            SyncOrchestrator orchestrator = ctx.getBean(SyncOrchestrator.class);
            AgentResult result = orchestrator.run(
                    new AgentRequest("echo", Map.of(), Map.of(), "hello"), AgentContext.builder().build());

            assertThat(result.status()).isEqualTo(AgentResult.Status.OK);
            assertThat(result.answer()).isEqualTo("echo: hello");
            assertThat(result.confidence()).isEqualTo(1.0); // default estimator: validated OK -> 1.0
        });
    }

    @Test
    void streamsViaTheAutoWiredStreamingOrchestrator() {
        runner.withUserConfiguration(EchoConfig.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(StreamingOrchestrator.class);
            List<String> chunks = new ArrayList<>();
            AgentResult[] done = new AgentResult[1];
            AgentStreamListener listener = new AgentStreamListener() {
                @Override public void onChunk(String delta) { chunks.add(delta); }
                @Override public void onComplete(AgentResult result) { done[0] = result; }
            };

            AgentResult result = ctx.getBean(StreamingOrchestrator.class).run(
                    new AgentRequest("echo", Map.of(), Map.of(), "hello"), AgentContext.builder().build(),
                    listener);

            assertThat(result.status()).isEqualTo(AgentResult.Status.OK);
            assertThat(String.join("", chunks)).isEqualTo("echo: hello");
            assertThat(done[0]).isSameAs(result);
        });
    }

    @Test
    void unknownCapabilityIsUnsupportedAndStillAudited() {
        runner.withUserConfiguration(EchoConfig.class).run(ctx -> {
            RingBufferAuditSink sink = (RingBufferAuditSink) ctx.getBean(AuditSink.class);
            AgentResult result = ctx.getBean(SyncOrchestrator.class).run(
                    new AgentRequest("nope", Map.of(), Map.of(), "x"), AgentContext.builder()
                            .audit(sink).build());

            assertThat(result.status()).isEqualTo(AgentResult.Status.UNSUPPORTED);
            assertThat(sink.size()).isEqualTo(1); // the miss is still audited
        });
    }

    @Test
    void applicationBeansOverrideEveryDefault() {
        runner.withUserConfiguration(EchoConfig.class, OverridesConfig.class).run(ctx -> {
            assertThat(ctx.getBean(ConfidenceEstimator.class)).isInstanceOf(FixedLowEstimator.class);
            assertThat(ctx.getBean(AuditSink.class)).isSameAs(AuditSink.NONE);

            // Custom estimator scores 0.2 < the capability's 0.5 threshold, and the custom policy
            // abstains -> UNAVAILABLE rather than surfacing the OK answer. Proves both overrides win.
            AgentResult result = ctx.getBean(SyncOrchestrator.class).run(
                    new AgentRequest("echo", Map.of(), Map.of(), "hello"), AgentContext.builder().build());
            assertThat(result.status()).isEqualTo(AgentResult.Status.UNAVAILABLE);
        });
    }

    // ── fakes (the "application") ────────────────────────────────────────────────────

    @Configuration
    static class EchoConfig {
        @Bean
        Capability echoCapability() {
            return new EchoCapability(0.5);
        }
    }

    @Configuration
    static class OverridesConfig {
        @Bean
        ConfidenceEstimator confidenceEstimator() {
            return new FixedLowEstimator();
        }

        @Bean
        EscalationPolicy escalationPolicy() {
            return new EscalationPolicy(List.of(new EscalationRung.Abstain()));
        }

        @Bean
        AuditSink auditSink() {
            return AuditSink.NONE;
        }
    }

    /** A capability that echoes the user text as a validated OK result. */
    record EchoCapability(double threshold) implements Capability {
        @Override
        public CapabilitySpec spec() {
            return new CapabilitySpec("echo", 1, "echoes user text", ModelTier.SMALL, threshold,
                    Duration.ofSeconds(1), Set.of(), Set.of());
        }

        @Override
        public AgentResult run(AgentRequest request, AgentContext ctx) {
            return AgentResult.ok("echo", 1, "echo: " + request.userText(), List.of(), List.of(),
                    "test", 0.0, ctx.effectiveTier(spec().defaultTier()));
        }
    }

    /** Always scores 0.2 — below the echo capability's 0.5 threshold. */
    static final class FixedLowEstimator implements ConfidenceEstimator {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public double estimate(AgentRequest request, AgentResult candidate, AgentContext ctx) {
            calls.incrementAndGet();
            return 0.2;
        }
    }
}
