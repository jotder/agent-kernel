# Changelog

All notable changes to **agent-kernel** are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
this project follows an **independent SemVer line**: unstable `0.x` until the 2nd consumer had exercised
the API, frozen at `1.0` after the reshape review found no structural change needed (ADR-0014). Additive
refinements ship as minors; a breaking change would batch into the next major.

## [Unreleased]

Nothing yet (`main` is `1.2.0-SNAPSHOT`).

## [1.1.0] — 2026-06-06

Additive batch closing the human-in-the-loop **return** path, driven by CVVE's six build slices (the first
real `HumanHandoff` consumer). All non-breaking — no 1.0 type changed shape; consumers opt in by bumping.
See **ADR-0015**.

### Added
- **`observe.HumanDecided`** — new sealed `AgentEvent` variant recording a reviewer's terminal decision
  (`decision` label / `reviewer` / opaque `reference`; keys-and-summaries only per ADR-0008), so the human
  return trip lands in the same audit stream as machine `AgentCompleted` events.
- **`SyncOrchestrator.resume(request, ctx, decision, reviewer, reference)`** — re-dispatches a corrected
  request through the normal pipeline (one `AgentCompleted`) then emits one `HumanDecided`, blessing the
  resume-after-human round-trip so consumers stop hand-rolling it.

### Changed
- **`EscalationRung.HumanHandoff`** now returns a **self-describing** result: the candidate attempt's
  `answer`/`evidence`/`links`/`rationale`/`data` ride through alongside the `{escalation, queue}` routing
  keys (1.0 carried only the routing keys), so a HITL consumer no longer recomputes the candidate. The two
  routing keys are still present — existing readers are unaffected.

### Notes
- Adding `HumanDecided` to the sealed `AgentEvent` is non-breaking: there is no exhaustive `switch` over
  `AgentEvent` (kernel or consumers); every sink dispatches with `instanceof` guards and ignores unknowns.
- **Deferred:** per-capability escalation posture (the slice-5 "soft" idea) — its own ADR if a second
  multi-capability consumer wants it.

## [1.0.0] — 2026-06-06

First stable release — the ring-1 API freeze (R1.6), gated by the R1.5 reshape review (**ADR-0014**):
after the structurally different second consumer (the CxO agent) exercised the whole ring-1 surface
across four slices, no type needed a breaking reshape, so the surface froze as-is. Contains everything
from K0 (bootstrap) through R1.5.

### Added (K0 — bootstrap)
- Multi-module Maven reactor: `agent-kernel-parent` + `agent-kernel-core` (ring 1),
  `agent-provider-ollama`, `agent-eval` (ring 2).
- Java 25 bytecode floor (`maven.compiler.release=25`).
- Ring-1 **zero-runtime-deps** guarantee enforced at build time (Maven Enforcer `bannedDependencies`)
  and by a CI guard (`tools/check-core-no-deps.sh`).
- GitHub Actions CI: build + test on PR/push; deploy to GitHub Packages on tag `v*`.
- Founding ADR set (0001–0008) and `docs/ARCHITECTURE.md`.
- Compilable ring-1 package skeleton (`model`, `tool`, `agent`, `reason`, `error`, `observe`, `retrieve`).

### Added (K1 — ring-1 core + provider + eval harness)
- **`…model`**: `ModelTier`, `ModelRequest`, `ModelResponse` (token usage), `ModelProvider`,
  `ModelRouter` (interface with `next(tier)` escalation + `of(...)` factories).
- **`…tool`**: `CredibilityTier`, `Evidence` (+ `tierLabel` escape hatch), `ToolResult`, `ToolSpec`,
  `Tool` (transport-free), `ToolRegistry`, `GroundingGuard` + default `NumericGroundingGuard`.
- **`…error`**: sealed `AgentError` (abstract class extending `RuntimeException`) + 5 kinds + `Category`.
- **`…agent`**: `AgentRequest`/`AgentResult` (numeric confidence), `CapabilitySpec`, `Capability`,
  `CapabilityRegistry`, `AgentContext` (+ builder, `effectiveTier`/`withEffectiveTier` escalation seam).
- **`…reason`**: `ConfidenceEstimator`, `EscalationPolicy` + sealed `EscalationRung`
  (BumpModelTier/HumanHandoff/Abstain), `Deadline`, `RepairLoop`.
- **`…observe`**: sealed `AgentEvent` (started/completed/failed, model/tool calls), `AuditSink`,
  `RingBufferAuditSink`. Keys/summaries only (ADR-0008).
- **`…retrieve`**: `Retriever` (+ `NONE`), `ContextBudget`, lexical `DocRetriever`.
- **`agent-provider-ollama`**: `ModelProfile` + `OllamaModelProvider` (langchain4j-ollama; lazy,
  abstain-safe; returns token usage) + `routerFor(profile)`.
- **`agent-eval`**: `EvalCase`/`Expect`, `FakeModelProvider`, `EvalReport`, `EvalRunner`,
  `EvalCaseLoader` (jackson), `Evals.asTests` glue + self-test fixtures (test-jar).
- 27 tests green (`./mvnw -B verify`) with zero apps; ring-1 still zero runtime deps.

### Added (R1 — companion modules, driven by the 2nd consumer)
- **`agent-orchestration`**: assembled synchronous orchestrator `SyncOrchestrator` (**ADR-0009**) and
  result-granularity `StreamingOrchestrator` + `AgentStreamListener` (**ADR-0012**); `Orchestrations`
  factories.
- **`agent-kernel-spring`**: Spring Boot auto-configuration (`AgentKernelAutoConfiguration`) assembling
  the ingredients into an injectable `SyncOrchestrator` (**ADR-0010**).
- **`agent-provider-langchain4j`**: second `ModelProvider` — Google AI Gemini via LangChain4j
  (`GeminiModelProvider`/`GeminiModelProfile`), confirming the model seam generalizes past Ollama with
  no ring-1 change (**ADR-0011**).
- **`agent-store-postgres`**: pgvector-backed `PgVectorRetriever` + ring-2 `EmbeddingModel` seam,
  confirming the retrieval seam generalizes from lexical to vector with no ring-1 change (**ADR-0013**).

### Changed (R1.5 — ring-1 reshape review, ADR-0014)
- Ring-1 confirmed freeze-ready with **zero structural changes**; the only edit is a `CredibilityTier`
  Javadoc clarification — declaration order is **not** a trust ranking; ranking stays an application
  concern (supersedes the "ordered enum" facet of ADR-0004).
- Token-level streaming (`ModelProvider`) and `EmbeddingModel` promotion to ring-1 re-classified as
  additive-when-demanded — deferred, not `1.0` blockers.

### Design note
- `AgentError` is a sealed **abstract class extending `RuntimeException`** (not a bare interface),
  so it can appear in `throws`/`catch` and be matched exhaustively — a deliberate, stronger choice
  than the plan's illustrative interface sketch.

### Notes
- `agent-hitl`, named in early plans, was never built as a separate module: the handoff decision lives in
  ring-1 (`EscalationRung.HumanHandoff`) and the return path landed additively in `1.1` (**ADR-0015**).
