/**
 * RING 2 — a {@link com.gamma.agentkernel.model.ModelProvider} backed by Google AI Gemini via
 * LangChain4j. The second provider behind the ring-1 model seam (after {@code agent-provider-ollama}),
 * which exists to confirm the seam generalizes past a single provider (R1; ADR-0011). Opt-in: its
 * LangChain4j/Gemini dependencies never reach ring-1 core, and it is abstain-safe by default (no key ⇒
 * not available ⇒ no network).
 */
package com.gamma.agentkernel.provider.langchain4j;
