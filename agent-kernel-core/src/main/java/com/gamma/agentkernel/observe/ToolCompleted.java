package com.gamma.agentkernel.observe;

/** A tool invocation completed: the tool id, how much evidence it produced, and its duration. */
public record ToolCompleted(String capabilityId, long epochMillis, String toolId,
                            int evidenceCount, long durationMs) implements AgentEvent {}
