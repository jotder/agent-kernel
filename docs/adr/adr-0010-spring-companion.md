# ADR-0010: Ship a Spring Boot auto-configuration companion in a ring-2 `agent-kernel-spring` module

**Status:** Accepted **Date:** 2026-06-05 **Deciders:** Kernel maintainers, CxO eng lead

## Context

K1 shipped ring-1 as framework-agnostic primitives, and ADR-0009 added the assembled `SyncOrchestrator`
in the pure ring-2 `agent-orchestration` module. A consumer still has to *wire* those ingredients: build
a `CapabilityRegistry` from its capabilities, pick an `EscalationPolicy` and a `ConfidenceEstimator`,
choose an `AuditSink`, and construct the `SyncOrchestrator`. UCC does this by hand in `UccAssistAgent`.

R1's second consumer, the Real-Estate **CxO** agent (`competitive-analysis`), is a **Spring Boot 3.5 /
Spring Modulith** application. Its `intelligence` module wants to *inject* a ready orchestrator and just
declare its capabilities as beans — re-deriving UCC's hand-wiring in every Spring app is exactly the
duplication the kernel exists to remove. CxO is the trigger that makes a Spring wiring companion
non-speculative: there is now a real Spring consumer to shape it (rule of three; R1 §2.2, §3).

ADR-0002's ring model and ADR-0001's zero-dep core forbid putting any framework (Spring included) in
ring-1. So the Spring glue must be an **opt-in ring-2 companion**, carrying its own Spring dependency in
isolation, exactly like `agent-provider-ollama` carries LangChain4j.

## Decision

Ship `agent-kernel-spring`, a new **ring-2** module providing a single Spring Boot
`@AutoConfiguration` (`AgentKernelAutoConfiguration`) that assembles the ring-1 ingredients into an
injectable runtime:

- `CapabilityRegistry` — `CapabilityRegistry.of(all Capability beans)`.
- `ConfidenceEstimator` — a conservative default (validated OK → 1.0, unvalidated OK → 0.5, else 0.0).
- `EscalationPolicy` — the default abstain-only posture (UCC's), below threshold → UNAVAILABLE.
- `AuditSink` — the in-memory `RingBufferAuditSink`.
- `SyncOrchestrator` — built over the above (from `agent-orchestration`).

**Every bean is `@ConditionalOnMissingBean`**, so an application overrides any piece by declaring its own
bean of that type (its own signal-composing `ConfidenceEstimator`, a tier-bump/human-handoff
`EscalationPolicy`, a durable `AuditSink`, or even a custom registry/orchestrator). With no overrides the
starter works out of the box.

Dependencies: `agent-kernel-core` + `agent-orchestration` (compile) and `spring-context` +
`spring-boot-autoconfigure` declared **`provided`** — required to compile the auto-config but supplied by
the consumer's own Spring Boot at runtime, and never imposed transitively. The Spring Boot version is
pinned in the parent (`3.5.3`, the version CxO runs) purely for compilation. **No ring-1 change was
required** — confirming the K1 + ADR-0009 seam holds for a Spring consumer.

## Options Considered

### Option A: Each Spring app hand-wires the ingredients (status quo, UCC-style)
**Pros:** zero kernel change. **Cons:** every Spring consumer re-derives the same registry+orchestrator
wiring; no shared, tested assembly; CxO would copy UCC's `@Configuration` rather than share one.

### Option B: A plain `spring-context` `@Configuration` (no spring-boot-autoconfigure)
**Pros:** one fewer dependency. **Cons:** no `@ConditionalOnMissingBean`, so apps cannot cleanly override
defaults (bean conflicts); not discoverable via `AutoConfiguration.imports`; not idiomatic for a Boot app.

### Option C: A Spring Boot `@AutoConfiguration` companion with conditional defaults (chosen)
**Pros:** idiomatic for the Boot/Modulith consumer; auto-discovered by adding the dependency; clean
override semantics; keeps ring-1 Spring-free; a natural home for future Spring wiring (endpoints, tenancy).
**Cons:** depends on spring-boot-autoconfigure (ring-2, `provided`) and pins a Boot version for compile.
Accepted — it is the substrate R1 §7 task 2 designated ("stand up `agent-kernel-spring` … the wiring
substrate the others plug into").

## Trade-off Analysis

One new ring-2 module + a `provided` Spring dependency, versus eliminating per-app orchestrator wiring and
giving the Spring consumers (CxO now; CVVE later) a shared, tested assembly. The companion is low-risk: it
adds no ring-1 type, the Spring deps are isolated and `provided`, and the auto-config is exercised by an
app-free `ApplicationContextRunner` test. Cost: one module + a pinned Boot version for compilation;
benefit: shared wiring + evidence the seam serves a Spring consumer unchanged.

## Consequences

- **Easier:** a Spring consumer adds the dependency, declares `Capability` beans, and injects a
  `SyncOrchestrator`; overrides any default with one bean. CxO's `intelligence` module needs no wiring code.
- **Harder:** nothing material; the module pins a Spring Boot version for compile (consumers override at
  runtime via their own BOM).
- **Revisit:** as CxO grows, this companion is where Spring-specific surface lands — REST/SSE endpoint
  adapters, `tenantId` wiring, actuator metrics. The streaming orchestrator (R1.3) and any ring-1 reshape it
  forces (e.g. a streaming `ModelProvider`, R1 §5) remain separate, trigger-shaped increments.

## Action Items

1. `agent-kernel-spring` module: `AgentKernelAutoConfiguration` (`com.gamma.agentkernel.spring`) +
   `AutoConfiguration.imports`; deps core + orchestration + `provided` Spring. ✅
2. App-free `ApplicationContextRunner` test proving assembly + `@ConditionalOnMissingBean` overrides. ✅
3. Register the module in the parent reactor; import the Spring Boot BOM (after junit-bom) for management. ✅
4. CxO's `intelligence` module consumes it (R1.1, CxO side); the kernel stays `0.x`/SNAPSHOT.
