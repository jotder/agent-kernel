# ADR-0012: Add a streaming orchestrator entry point in ring-2 `agent-orchestration`; defer token streaming to the `1.0` reshape

**Status:** Accepted **Date:** 2026-06-06 **Deciders:** Kernel maintainers, CxO eng lead

## Context

ADR-0009 shipped the assembled `SyncOrchestrator` (resolve → escalate → audit-once) as a blocking
request→`AgentResult` flow. R1's CxO consumer wants a **chat/SSE** surface (`POST /api/chat`) where the
user sees output appear progressively — "first token < 5s" is a stated CxO UX goal. So R1.3 needs a
*streaming* orchestrator sibling.

The question R1 was built to answer: **does streaming compose over the existing ring-1 ingredients with no
ring-1 change, or does it force a reshape?** It forces a partial one. The ring-1 model seam,
`ModelProvider.generate(ModelRequest) → ModelResponse` (ADR-0004), is **blocking**: a capability produces
its entire answer before returning. There is no way to surface model tokens incrementally through the
current seam. Token-level streaming therefore *cannot* be expressed in ring-1 today — exactly the kind of
reshape signal R1 §5 anticipated ("if streaming can't compose, that's the reshape signal — a streaming
`generate` — folded into R1.5").

## Decision

Two parts — ship what composes now, name the reshape for later.

**1. Ship a `StreamingOrchestrator` (ring-2 `agent-orchestration`) at *result granularity*.** It reuses the
*same* ring-1 ingredients (`CapabilityRegistry`, `ConfidenceEstimator`, `EscalationPolicy`) and the *same*
audit contract as `SyncOrchestrator` — resolve → escalate → **emit the computed answer in chunks** to an
`AgentStreamListener` → complete → audit exactly once (in a `finally`, so one `AgentCompleted` is recorded
even if the listener throws). Chunks are answer substrings whose in-order concatenation reconstructs the
answer. The shared audit summary is extracted to a package-private `Orchestrations.completed(...)` so both
entry points audit identically (behaviour-preserving for `SyncOrchestrator`). The `AgentStreamListener`
contract (`onChunk` / `onComplete` / `onError`) is deliberately shaped so the future reshape is **additive,
not breaking**. **No ring-1 change.**

**2. Defer token-level streaming to the `1.0` reshape (R1.5/R1.6).** Adding a streaming model seam to ring-1
(e.g. a streaming `generate(request, TokenSink)` or a `StreamingModelProvider` sub-interface, with the
`StreamingOrchestrator` driving it token-by-token) is a real ring-1 change. Per ADR-0002 (additive `0.x`
minors; breaking changes batch into `1.0`) and the R1 guardrail (no speculative ring-1 churn), it is
**recorded, not built** until the reshape pass — when CVVE or CxO's deeper streaming needs confirm its exact
shape alongside the other `1.0` candidates.

## Options Considered

### Option A: Add a streaming `ModelProvider.generate` to ring-1 now, drive real token streaming
**Pros:** real first-token latency. **Cons:** a ring-1 change shaped by a single consumer's first guess —
the speculative ring-1 churn R1 exists to avoid; should batch into `1.0` with the other reshape candidates.

### Option B: Result-granularity streaming over the blocking seam now; defer the ring-1 streaming seam (chosen)
**Pros:** gives CxO a working SSE surface over the same neutral pipeline + audit with **zero ring-1 change**;
proves the orchestration ingredients compose into a streaming entry point; concretely surfaces the reshape
signal with a forward-compatible listener API. **Cons:** chunks aren't model tokens, so first-token latency
isn't improved yet (the answer is computed before chunking). Accepted — honest, non-speculative, unblocks CxO.

### Option C: Do nothing until the ring-1 streaming seam is designed
**Pros:** no interim API. **Cons:** blocks CxO's chat surface for a whole phase; the result-granularity
orchestrator is useful and its listener API survives the reshape, so there is no waste.

## Consequences

- **Easier:** CxO exposes `POST /api/chat` (SSE) over `StreamingOrchestrator` immediately, reusing the
  resolve→escalate→audit pipeline and the Gemini narration (ADR-0011). The `agent-kernel-spring` auto-config
  provides the `StreamingOrchestrator` bean alongside `SyncOrchestrator`.
- **Harder:** token-level latency waits for the ring-1 streaming seam; until then "streaming" means chunked
  delivery of a fully-computed answer.
- **Revisit (R1.5/R1.6):** decide the streaming model seam shape and have `StreamingOrchestrator` drive it.
  This ADR records the gap; it does **not** itself change ring-1.

## Action Items

1. `agent-orchestration`: `AgentStreamListener` + `StreamingOrchestrator` (result-granularity); extract
   `Orchestrations.completed(...)` shared by both orchestrators (behaviour-preserving). ✅
2. Test: chunks reconstruct the answer; complete + audit-once; unsupported/abstain emit no chunks. ✅
3. `agent-kernel-spring`: expose a `StreamingOrchestrator` bean (`@ConditionalOnMissingBean`). ✅
4. CxO wires `POST /api/chat` SSE over it (R1.3, CxO side).
5. **R1.5/R1.6:** evaluate a streaming ring-1 model seam; this ADR is the standing record of the deferral.
