package com.gamma.agentkernel.orchestrate;

import com.gamma.agentkernel.agent.AgentResult;

/**
 * The push sink a {@link StreamingOrchestrator} emits to: incremental {@link #onChunk(String) chunks} of
 * the answer, then a single {@link #onComplete(AgentResult)} carrying the neutral result (evidence,
 * confidence, status). A consumer adapts this to its transport — e.g. a Spring {@code SseEmitter}.
 *
 * <p>Functional interface (single abstract method {@link #onChunk}); {@link #onComplete} and
 * {@link #onError} default to no-op / rethrow. The contract is shaped to survive the R1.5 reshape to
 * true token-level streaming: today's chunks are answer substrings (the ring-1 model seam is blocking —
 * ADR-0012), but the same listener will carry model tokens once a streaming seam exists.
 */
@FunctionalInterface
public interface AgentStreamListener {

    /** A piece of the answer, in order. Concatenated in emission order, the chunks reconstruct the answer. */
    void onChunk(String delta);

    /** The terminal neutral result (after the last chunk). Defaults to no-op. */
    default void onComplete(AgentResult result) {
    }

    /** A streaming-time failure (reserved for the future token-streaming path). Defaults to rethrow. */
    default void onError(RuntimeException error) {
        throw error;
    }
}
