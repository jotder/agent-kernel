package com.gamma.agentkernel.reason;

import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.agent.AgentRequest;
import com.gamma.agentkernel.agent.AgentResult;

/**
 * Estimates a real confidence in a candidate result, by composing deterministic signals (validator
 * pass/fail, grounding coverage, oracle agreement, schema conformance, evidence credibility, model
 * self-report). Returns a value in {@code [0, 1]}; the escalation policy compares it to the
 * capability's {@code confidenceThreshold}.
 */
@FunctionalInterface
public interface ConfidenceEstimator {

    double estimate(AgentRequest request, AgentResult candidate, AgentContext ctx);
}
