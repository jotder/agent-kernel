# Migrating from `agent-kernel` 0.x to 1.0

**TL;DR — there are no breaking changes.** `1.0.0` freezes the API that the `0.x` line already shipped.
Bump the dependency version and rebuild.

## Why 1.0 is safe

`agent-kernel` stayed at `0.1.0-SNAPSHOT` deliberately: the rule of three (ADR-0002) holds an API at `0.x`
until **two** consumers have exercised it. The R1 phase introduced the second consumer (the CxO agent)
alongside the first (UCC) and drove it across four slices — Spring companion, reconciliation/analytics
tools, a hosted model provider + streaming, and pgvector RAG. The R1.5 reshape review (ADR-0014) found that
**every ring-1 abstraction was consumed with no structural change**, so `1.0.0` ratifies the existing surface
rather than altering it.

## What to do

1. Bump the version in your build:
   ```xml
   <agent-kernel.version>1.0.0</agent-kernel.version>
   ```
   (or update each `com.gamma.agentkernel:*` dependency to `1.0.0`).
2. Rebuild. No source or binary changes are required.

## API notes (clarifications, not changes)

- **`CredibilityTier` is a vocabulary, not a ranking** (ADR-0014, supersedes the "ordered enum" facet of
  ADR-0004). The enum's *declaration order is not a trust ranking* — ring-1 never reads `ordinal()` on it.
  If your app ranks tiers (as CxO does), keep supplying your own rank/`Comparator`; nothing in `1.0` changed
  here, but do not start relying on enum order. Use `Evidence.tierLabel` for an app-specific tier name.
- **Streaming model seam** remains result-granularity via the ring-2 `StreamingOrchestrator`
  (`agent-orchestration`). Token-level streaming over `ModelProvider` is not in `1.0`; when a consumer needs
  it, it can be added as a `default` method on `ModelProvider` (additive, non-breaking) in a later minor
  (ADR-0012/0014).
- **`EmbeddingModel`** stays in ring-2 `agent-store-postgres` (ADR-0013); it is not a ring-1 type in `1.0`.

## Ring / module map (unchanged at 1.0)

- **Ring 1 (zero runtime deps):** `agent-kernel-core`.
- **Ring 2 (opt-in companions):** `agent-orchestration` (Sync + Streaming orchestrators),
  `agent-kernel-spring` (Spring Boot auto-config), `agent-provider-ollama`, `agent-provider-langchain4j`
  (Gemini), `agent-store-postgres` (pgvector `Retriever`), `agent-eval`.

## SemVer going forward

From `1.0.0`, the public API of ring-1 and the published ring-2 companions follows SemVer: additive changes
ship as minors (`1.x`), breaking changes wait for `2.0`. New ring-2 companions are independent additions.
