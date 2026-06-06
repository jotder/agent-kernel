# ADR-0011: Add a second `ModelProvider` (Google AI Gemini, via LangChain4j) in ring-2 `agent-provider-langchain4j`

**Status:** Accepted **Date:** 2026-06-06 **Deciders:** Kernel maintainers, CxO eng lead

## Context

Ring-1's `ModelProvider` is the swappable seam between a capability and a language model (ADR-0004): a
capability declares a `ModelTier`, a `ModelRouter` resolves it to a provider, and the runtime checks
`available()` (cheap, no network) before `generate()`. Until R1.3 the kernel had exactly **one** concrete
provider — `agent-provider-ollama` (local models) — so the seam had only ever been shaped by a single
implementation. The rule of three (ADR-0002) says an abstraction isn't trustworthy until ≥2 consumers
exercise it.

R1's second consumer, the **CxO** agent, needs a **hosted** model (Gemini) for its narration. That makes a
second provider non-speculative and turns it into the test the seam was waiting for: *does `ModelProvider`
generalize past Ollama with no ring-1 change?*

## Decision

Ship `agent-provider-langchain4j`, a new **ring-2** module implementing `ModelProvider` over Google AI
Gemini through LangChain4j (`langchain4j-google-ai-gemini`, pinned in the parent at the existing
`langchain4j.version`). Shape it exactly like the Ollama provider:

- `GeminiModelProvider implements ModelProvider` — one instance per `ModelTier`/model, **lazy** (the chat
  model is built on first `generate`, never in the constructor) and **abstain-safe** (`available()` is a
  pure config check: enabled + API key + a model mapped for the tier).
- `GeminiModelProfile` — env/system-property resolved (`agentkernel.gemini.enabled`, an API key from
  `GEMINI_API_KEY`/`GOOGLE_API_KEY`, per-tier model names), **disabled with no key by default** so CI and
  vanilla deployments make no network call.
- `routerFor(profile)` / `fromEnvironment()` build a `ModelRouter` (one provider per tier), mirroring
  Ollama.

**No ring-1 change was required** — the confirmation the seam generalizes to a hosted provider. The seam's
one shortcoming surfaced here is recorded separately: the blocking `generate` contract cannot express
token streaming (ADR-0012), the deferred reshape candidate.

## Options Considered

### Option A: Reuse `agent-provider-ollama`, point it at a Gemini-compatible endpoint
**Pros:** no new module. **Cons:** Ollama's builder/semantics are local-model specific; conflates two
providers; doesn't actually test that the *seam* (not one impl) generalizes.

### Option B: A new ring-2 `agent-provider-langchain4j` for Gemini (chosen)
**Pros:** isolates Gemini/LangChain4j deps out of ring-1; proves `ModelProvider`/`ModelRouter` serve a
second, structurally-different (hosted) provider unchanged; gives CxO real narration; abstain-safe by
default. **Cons:** one more module + a pinned dependency. Accepted — it is the R1 §2.2 trigger deliverable.

### Option C: Put the Gemini provider in ring-1 / core
Rejected outright — violates the zero-dep core (ADR-0001/0002).

## Consequences

- **Easier:** a deployment swaps local↔hosted by wiring a different `ModelRouter` bean; capabilities are
  unchanged (tier-bound, not model-bound). CxO injects the Gemini router; UCC keeps Ollama.
- **Harder:** nothing material; the module carries the LangChain4j Gemini dependency in isolation.
- **Evidence banked:** `ModelProvider`/`ModelRouter`/`ModelTier`/`ModelRequest`/`ModelResponse` are now
  exercised by two providers with no ring-1 change → these abstractions clear the rule-of-three bar for the
  `1.0` freeze (R1.6), *except* for the streaming gap in ADR-0012.

## Action Items

1. `agent-provider-langchain4j` module: `GeminiModelProvider` + `GeminiModelProfile`
   (`com.gamma.agentkernel.provider.langchain4j`); dep core + `langchain4j-google-ai-gemini`. ✅
2. Network-free test of the abstain-safe contract (availability/naming/router; no live call). ✅
3. Register the module + pin the dependency in the parent reactor. ✅
4. CxO wires the Gemini `ModelRouter` and switches narration onto it (R1.3, CxO side); kernel stays `0.x`.
