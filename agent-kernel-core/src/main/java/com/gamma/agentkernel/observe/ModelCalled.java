package com.gamma.agentkernel.observe;

import com.gamma.agentkernel.model.ModelTier;

/** A model generation was issued at the given tier (with/without JSON formatting). No prompt text. */
public record ModelCalled(String capabilityId, long epochMillis, ModelTier tier, boolean jsonFormat)
        implements AgentEvent {}
