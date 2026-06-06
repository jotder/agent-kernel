# ADR-0013: Add a pgvector-backed `Retriever` in ring-2 `agent-store-postgres`, with the embedding seam in ring-2

**Status:** Accepted **Date:** 2026-06-06 **Deciders:** Kernel maintainers, CxO eng lead

## Context

Ring-1's `Retriever` is the seam that supplies a capability with *qualitative grounding* — text snippets
returned as `Evidence` with a `sourceRef` locator, **never figures** (ADR-0001/0008). Its contract is
deliberately tiny: `List<Evidence> retrieve(String query, ContextBudget budget)`. Until R1.4 the kernel
shipped exactly **one** implementation, the dependency-free lexical `DocRetriever` (in-memory term overlap
over Markdown). The rule of three (ADR-0002) says the seam isn't trustworthy until a second, structurally
different consumer exercises it.

R1's second consumer, the **CxO** agent, needs real RAG over a **PostgreSQL + pgvector** corpus of source
documents (brochures, RERA filings, listings). That makes a vector retriever non-speculative and turns it
into the test the seam was waiting for: *does `Retriever`/`ContextBudget` generalize from lexical to vector
retrieval with no ring-1 change?*

A vector retriever needs something ring-1 deliberately does not have: a way to turn the `String` query into
an embedding vector. An embedder is heavyweight and provider-specific — exactly what the zero-dep core
excludes.

## Decision

Ship `agent-store-postgres`, a new **ring-2** module:

- `PgVectorRetriever implements Retriever` — embeds the query, runs a nearest-neighbour search over a
  `doc_chunk(content, source_ref, embedding)` table ordered by cosine distance (`<=>`), and maps rows to
  grounding `Evidence` (tier `INDICATIVE`, label `"doc"`, confidence `1 − distance` clamped). It compiles
  against **`java.sql` only** — the JDBC driver is the consuming application's runtime concern — so the
  module's weight never approaches ring-1. It is **abstain-safe**: a missing dependency, blank/un-embeddable
  query, or store error returns an empty list rather than failing the agent (RAG is optional grounding, like
  `Retriever.NONE`). The constructor never connects. A `Config` record makes the SQL/tier overridable.
- `EmbeddingModel` (a `float[] embed(String)` SAM) — **the embedding seam, kept in ring-2, not ring-1.**
  The application supplies it (e.g. a LangChain4j embedding model).
- `agent-kernel-spring` gains a default `Retriever` bean (`Retriever.NONE`, `@ConditionalOnMissingBean`) so
  an app runs without RAG out of the box and overrides it by declaring its own `Retriever` bean.

**No ring-1 change was required** — the confirmation `Retriever`/`ContextBudget`/`Evidence` generalize from
lexical to vector retrieval unchanged.

## Options Considered

### Option A: Put an `EmbeddingModel` (and/or the vector store) in ring-1 / core
Rejected outright — an embedder drags in a model/provider dependency; violates the zero-dep core
(ADR-0001/0002).

### Option B: `agent-store-postgres` ring-2 module, `EmbeddingModel` seam in ring-2 (chosen)
**Pros:** isolates JDBC/pgvector/embedding concerns out of ring-1; proves the `Retriever` seam serves a
second, structurally different (vector) implementation unchanged; compiles against `java.sql` so it carries
no runtime dependency of its own; abstain-safe by default. **Cons:** the embedding abstraction lives in
ring-2, so a future second vector consumer can't share it through ring-1 yet. Accepted — it is the R1.4
trigger deliverable, and promoting the embedder is a clean `1.0` decision once a second consumer exists.

### Option C: Reuse `DocRetriever`, load documents into memory
Rejected — does not scale to a real corpus, and never exercises the seam against a vector store (the whole
point of R1.4).

## Consequences

- **Easier:** a deployment turns on RAG by declaring a `Retriever` bean (CxO wires `PgVectorRetriever` over
  its DataSource + an embedding model); with none, the kernel default `Retriever.NONE` keeps the agent
  running grounding-free. Capabilities read `ctx.retriever()` and are unchanged either way.
- **Harder:** the live pgvector `<=>` path is a SQL string not coverable by an offline unit test; it is
  verified against a live store in the consumer/deployment. Offline tests cover the budget→top-k math, the
  vector-literal format, the row→`Evidence` mapping (via H2), and the abstain-safe guards.
- **Evidence banked:** `Retriever`/`ContextBudget`/`Evidence` are now exercised by two implementations
  (lexical `DocRetriever` + vector `PgVectorRetriever`) with no ring-1 change → these abstractions clear the
  rule-of-three bar for the `1.0` freeze (R1.6). **Deferred reshape candidate:** promote `EmbeddingModel`
  into ring-1 if/when a second vector consumer (e.g. CVVE) appears (revisit at R1.5/R1.6).

## Action Items

1. `agent-store-postgres` module: `PgVectorRetriever` + `EmbeddingModel`
   (`com.gamma.agentkernel.store.postgres`); dep core only (`java.sql`), H2 + AssertJ test-scope. ✅
2. Offline tests: pure helpers (top-k, vector literal, mapping) + abstain-safety + an H2-backed JDBC
   plumbing test. ✅
3. `agent-kernel-spring`: default `Retriever` bean (`NONE`, `@ConditionalOnMissingBean`) + test. ✅
4. Register the module + reactor coordinate in the parent. ✅
5. CxO wires the pgvector retriever (DataSource + embedding model), adds the `doc_chunk` migration, and a
   `search-documents` capability that supplies qualitative context only (R1.4, CxO side); kernel stays `0.x`.
