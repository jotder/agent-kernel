# Changelog

All notable changes to **agent-kernel** are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
this project follows an **independent SemVer line** (unstable `0.x` until a 2nd consumer reshapes the API).

## [1.1.0] — Unreleased

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

## [Unreleased]

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

### Design note
- `AgentError` is a sealed **abstract class extending `RuntimeException`** (not a bare interface),
  so it can appear in `throws`/`catch` and be matched exhaustively — a deliberate, stronger choice
  than the plan's illustrative interface sketch.

### Deferred to R1
- Companion modules (`agent-kernel-spring`, `agent-store-postgres`, `agent-hitl`, `agent-orchestration`,
  `agent-provider-langchain4j`), the assembled orchestrator, and the `1.0` API freeze.
