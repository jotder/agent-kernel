/**
 * RING 2 — a {@code ModelProvider} backed by local Ollama (via langchain4j-ollama), plus the
 * {@code ModelProfile} wiring ported from UCC. Lazy and abstain-safe: reports unavailable until a
 * deployment turns the assist layer on.
 *
 * <p>K0 skeleton — {@code OllamaModelProvider} is added in K1.
 */
package com.gamma.agentkernel.provider.ollama;
