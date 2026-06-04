package com.gamma.agentkernel.observe;

/**
 * A sealed observability event. Variants carry <b>identifiers, counts, durations, tiers, token usage,
 * confidence, and provenance references — keys and summaries only</b>. They never carry data-plane
 * values: no record contents, no {@code Evidence.value}, no raw prompt/output text (ADR-0008).
 */
public sealed interface AgentEvent
        permits AgentStarted, AgentCompleted, AgentFailed, ModelCalled, ToolCalled, ToolCompleted {

    /** The capability this event belongs to. */
    String capabilityId();

    /** Event time, epoch millis. */
    long epochMillis();
}
