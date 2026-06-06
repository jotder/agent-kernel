# ADR-0014: Ring-1 reshape review for `1.0` — API confirmed stable; `CredibilityTier` order is not a trust ranking

**Status:** Accepted **Date:** 2026-06-06 **Deciders:** Kernel maintainers, CxO eng lead

## Context

The kernel shipped its ring-1 (`agent-kernel-core`) shaped by **one** consumer (UCC) and stayed at
`0.x`/SNAPSHOT on purpose: the rule of three (ADR-0002) says a concept only freezes once **≥2 apps** share
it. R1 introduced the **second** consumer — the CxO agent — and drove it across four slices (R1.1 Spring
companion, R1.2 reconciliation/analytics tools, R1.3 Gemini + streaming, R1.4 pgvector RAG). R1.5 is the
gate that asks the rule-of-three question across the whole ring-1 surface: **now that a structurally
different second consumer has exercised the API, what must ring-1 change before the `1.0` freeze?**

The standing rule: additive refinements ship as `0.x` minors; *breaking* changes batch into `1.0`; nothing
is built speculatively — only real consumer friction drives a change.

## Decision

**Ring-1 is confirmed freeze-ready: no structural change is required.** Across four CxO slices plus UCC, no
ring-1 type needed a breaking reshape — every abstraction was consumed unchanged. The review's findings,
abstraction by abstraction:

1. **`CredibilityTier` (the headline).** The two consumers map the enum **by name** 1:1 (the vocabulary
   generalizes), but they **rank it differently** — CxO ranks `USER_PROVIDED` above `INDICATIVE`; the kernel
   enum's declaration order is the reverse. Crucially, **ring-1 never ranks by `ordinal()`** (verified:
   zero `ordinal()`/`compareTo`/tier-sorting in core — `GroundingGuard`, `Evidence`, `DocRetriever`,
   `EscalationPolicy`, `ConfidenceEstimator` all treat the tier as an opaque label). So the rule-of-three
   verdict is: **keep the enum as a vocabulary; declaration order is NOT a canonical trust ranking; ranking
   stays an application concern** (each app supplies its own rank/`Comparator`, as CxO already does).
   **Do NOT promote `CredibilityTier` to an app-extensible interface** — the vocabulary fit by name across
   two consumers, so the `tierLabel` escape hatch is sufficient and an interface would be premature. This
   **supersedes the "ordered" / "revisit interface promotion at 1.0" facet of ADR-0004.** The only change
   made is a Javadoc clarification on `CredibilityTier`; no code or API shape changes.

2. **Streaming model seam (from ADR-0012).** `ModelProvider.generate` is blocking, so token-level streaming
   doesn't compose today. **Re-classified as a non-blocker for `1.0`:** `ModelProvider` is a plain interface,
   so a streaming method can be added later as a **`default`** (additive, binary- and source-compatible —
   existing providers keep working). With no consumer needing token streaming yet (CxO's result-granularity
   `StreamingOrchestrator` satisfies its SSE surface), it is **deferred until demanded**, not built now and
   not a breaking `1.0` change. ADR-0012 stands; this downgrades its reshape urgency.

3. **`EmbeddingModel` → ring-1? (from ADR-0013).** Only one vector consumer exists (CxO). Rule of three not
   met → **the embedding seam stays in ring-2 `agent-store-postgres`.** Revisit promotion if/when a second
   vector consumer (e.g. CVVE) appears.

4. **`AgentContext.tenantId()`.** Opaque, unused by ring-1, carried for CVVE's tenant scoping. It costs
   nothing and a known near-term consumer needs it → **retained as-is.**

5. **`EscalationRung` (sealed: `BumpModelTier`/`HumanHandoff`/`Abstain`).** Covers both consumers' postures
   (UCC `Abstain`, CVVE `HumanHandoff`, tier escalation `BumpModelTier`) → **the sealed set is the `1.0`
   surface.** A future rung is a deliberate minor (the kernel owns the exhaustive switch).

6. **No other friction.** CxO consumed `Capability`/`Tool`/`Evidence`/`ToolResult`/`AgentResult`/
   `AgentContext`/`SyncOrchestrator`/`Retriever`/`ContextBudget`/`ModelProvider`/`ModelRouter` with **zero
   ring-1 change** across all four slices — strong evidence the surface has stopped moving.

## Options Considered

### Option A: Promote `CredibilityTier` to a sealed/app-extensible interface at `1.0`
Rejected — the vocabulary fit by name across two consumers; the `tierLabel` hatch covers outliers. An
interface would be the premature abstraction ADR-0004 deliberately avoided, with no evidence demanding it.

### Option B: Add a canonical `Comparator`/`rank()` to ring-1 `CredibilityTier`
Rejected — the two consumers' orders conflict, so any canonical order ring-1 picked would be wrong for one
of them. Ranking is genuinely app-specific; ring-1 must stay neutral.

### Option C: Keep the enum as a neutral vocabulary; ranking stays app-side; clarify the contract (chosen)
**Pros:** matches what both consumers already do; no API churn; removes the misleading "ordered"
implication so future ring-1 code can't regress into `ordinal()`-based ranking. **Cons:** apps must supply
their own ordering (they already do). Accepted.

## Consequences

- **Easier:** the `1.0` ring-1 surface is settled; consumers rank tiers in their own domain without fighting
  a kernel-imposed order; streaming and embedding can be added additively later without a major bump.
- **Harder:** nothing material — this ratifies the status quo and documents it.
- **Revisit:** streaming `default` on `ModelProvider` when a consumer needs token streaming; `EmbeddingModel`
  promotion on a second vector consumer; a 4th `EscalationRung` only on real demand.
- **Gate:** with ring-1 confirmed stable, the `1.0` freeze (R1.6) is unblocked — it is now a release
  decision (version bump, tag, publish, migration notes), not an API decision.

## Action Items

1. Clarify `CredibilityTier` Javadoc: declaration order is not a trust ranking; ranking is an app concern;
   enum stays a vocabulary (not promoted). ✅
2. Record that this supersedes the "ordered"/interface-promotion facet of ADR-0004 (README annotation). ✅
3. No code change to `ModelProvider`/`Retriever`/`EmbeddingModel`/`tenantId`/`EscalationRung` — confirmed
   sufficient; streaming/embedding deferred as additive-when-demanded. ✅
4. Proceed to R1.6 (`1.0` freeze) on explicit go-ahead: bump versions, tag, publish, write `0.x`→`1.0`
   migration notes. ⏳
