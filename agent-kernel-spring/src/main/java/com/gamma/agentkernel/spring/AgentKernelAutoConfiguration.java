package com.gamma.agentkernel.spring;

import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.agent.Capability;
import com.gamma.agentkernel.agent.CapabilityRegistry;
import com.gamma.agentkernel.model.ModelProvider;
import com.gamma.agentkernel.model.ModelRouter;
import com.gamma.agentkernel.observe.AuditSink;
import com.gamma.agentkernel.observe.RingBufferAuditSink;
import com.gamma.agentkernel.orchestrate.StreamingOrchestrator;
import com.gamma.agentkernel.orchestrate.SyncOrchestrator;
import com.gamma.agentkernel.reason.ConfidenceEstimator;
import com.gamma.agentkernel.reason.EscalationPolicy;
import com.gamma.agentkernel.reason.EscalationRung;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Spring Boot auto-configuration that assembles the ring-1 ingredients into an injectable agent
 * runtime. It is the Spring counterpart of the hand-wiring a consumer would otherwise write
 * (cf. UCC's {@code UccAssistAgent}): collect the application's {@link Capability} beans into a
 * {@link CapabilityRegistry}, then build a {@link SyncOrchestrator} over an {@link EscalationPolicy},
 * a {@link ConfidenceEstimator}, and an {@link AuditSink}.
 *
 * <p>Every bean is guarded by {@link ConditionalOnMissingBean}, so an application overrides any piece
 * by simply declaring its own bean of that type — define a {@code ConfidenceEstimator} to replace the
 * conservative default, an {@code EscalationPolicy} to change the posture, a durable {@code AuditSink},
 * or even a fully custom {@code CapabilityRegistry}/{@code SyncOrchestrator}. With no overrides the
 * starter works out of the box.
 *
 * <p>This module is ring-2: it depends only on {@code agent-kernel-core} + {@code agent-orchestration}
 * and the Spring APIs (declared {@code provided}). Ring-1 stays Spring-free and zero-dep.
 */
@AutoConfiguration
public class AgentKernelAutoConfiguration {

    /** Default ring buffer capacity for the in-memory {@link AuditSink}. */
    static final int DEFAULT_AUDIT_CAPACITY = 256;

    /**
     * A registry over every {@link Capability} bean in the context (keyed by {@code spec().id()};
     * last one wins, per {@link CapabilityRegistry#of}). Empty if the app declares no capabilities.
     */
    @Bean
    @ConditionalOnMissingBean
    public CapabilityRegistry agentCapabilityRegistry(ObjectProvider<Capability> capabilities) {
        return CapabilityRegistry.of(capabilities.orderedStream().toList());
    }

    /**
     * The conservative kernel default: trust a validated OK result fully, an unvalidated OK result
     * partially, and anything else not at all. Confidence is genuinely app-specific — an application
     * that composes real signals (validator/grounding/evidence-credibility, as UCC's
     * {@code UccConfidenceEstimator} does) declares its own {@code ConfidenceEstimator} bean and this
     * backs off.
     */
    @Bean
    @ConditionalOnMissingBean
    public ConfidenceEstimator agentConfidenceEstimator() {
        return (request, candidate, ctx) -> {
            if (candidate == null || candidate.status() != AgentResult.Status.OK) return 0.0;
            return candidate.validated() ? 1.0 : 0.5;
        };
    }

    /**
     * The default escalation posture: a single {@link EscalationRung.Abstain} rung — below threshold,
     * return UNAVAILABLE rather than ship a low-confidence guess (UCC's posture). Apps that wire model
     * tiers or human handoff declare their own {@code EscalationPolicy} bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public EscalationPolicy agentEscalationPolicy() {
        return new EscalationPolicy(List.of(new EscalationRung.Abstain()));
    }

    /** The default in-memory audit sink (bounded ring buffer). A durable sink is a ring-2 companion. */
    @Bean
    @ConditionalOnMissingBean
    public AuditSink agentAuditSink() {
        return new RingBufferAuditSink(DEFAULT_AUDIT_CAPACITY);
    }

    /**
     * The default model router: every tier resolves to {@link ModelProvider#unavailable} so capabilities
     * degrade gracefully to "model unavailable" out of the box. An app that uses a model declares its own
     * {@code ModelRouter} bean (e.g. CxO's Gemini router from {@code agent-provider-langchain4j}, or the
     * Ollama router), and this backs off.
     */
    @Bean
    @ConditionalOnMissingBean
    public ModelRouter agentModelRouter() {
        return ModelRouter.of(ModelProvider.unavailable("no model provider configured"));
    }

    /** The assembled synchronous orchestrator: resolve → escalate(estimate, rungs) → audit. */
    @Bean
    @ConditionalOnMissingBean
    public SyncOrchestrator syncOrchestrator(CapabilityRegistry registry,
                                             ConfidenceEstimator estimator,
                                             EscalationPolicy escalation) {
        return new SyncOrchestrator(registry, estimator, escalation);
    }

    /**
     * The assembled streaming orchestrator: the same pipeline as {@link #syncOrchestrator} but emitting the
     * answer progressively to an {@code AgentStreamListener} (result-granularity streaming; ADR-0012). Lets
     * a consumer expose an SSE/chat surface over the same neutral pipeline and audit.
     */
    @Bean
    @ConditionalOnMissingBean
    public StreamingOrchestrator streamingOrchestrator(CapabilityRegistry registry,
                                                       ConfidenceEstimator estimator,
                                                       EscalationPolicy escalation) {
        return new StreamingOrchestrator(registry, estimator, escalation);
    }
}
