package com.gamma.agentkernel.observe;

import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.model.ModelTier;

import java.util.Set;

/**
 * A capability invocation completed. The rich summary record (consolidating the old {@code AuditEvent}):
 * status, evidence count, duration, context keys, serving tier, whether a model was invoked, repair
 * rounds, confidence, and token usage. Summaries and counts only — never data-plane values (ADR-0008).
 */
public record AgentCompleted(String capabilityId, long epochMillis, AgentResult.Status status,
                             int evidenceCount, long durationMs, Set<String> contextKeys,
                             ModelTier servedBy, boolean modelInvoked, int repairRounds,
                             double confidence, int promptTokens, int completionTokens)
        implements AgentEvent {
    public AgentCompleted {
        contextKeys = (contextKeys == null) ? Set.of() : Set.copyOf(contextKeys);
    }
}
