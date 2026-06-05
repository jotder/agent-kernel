/**
 * Spring Boot companion (ring-2): {@code AgentKernelAutoConfiguration} assembles the ring-1 ingredients
 * — the application's {@code Capability} beans, an {@code EscalationPolicy}, a {@code ConfidenceEstimator},
 * and an {@code AuditSink} — into an injectable {@code SyncOrchestrator} (from {@code agent-orchestration}).
 * Every bean is {@code @ConditionalOnMissingBean}, so an app overrides any piece by declaring its own.
 *
 * <p>Opt-in: Spring dependencies are {@code provided}; ring-1 core never sees Spring. First shaped by the
 * CxO consumer (R1); see docs/adr/adr-0010-spring-companion.md.
 */
package com.gamma.agentkernel.spring;
