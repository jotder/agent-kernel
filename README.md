# agent-kernel

A reusable, **framework-agnostic agent core** shared by three agent applications:
**UCC File-Processor assist**, **CVVE V&V-as-a-Service**, and the **Real-Estate CxO Decision-Support** agent.
The kernel owns the shared spine — *LLM orchestrates & narrates; deterministic tools compute & validate* —
and each app supplies only its capabilities, tools, context, and orchestration shell.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design and [`docs/adr/`](docs/adr/) for the
decision log (ADRs 0001–0015; 0001–0008 are the founding set).

## Three rings

- **Ring 1 — `agent-kernel-core`**: the pure core. **Zero runtime dependencies** (JDK only). The build
  *fails* if any compile/runtime dependency is added (enforced via the Maven Enforcer + a CI guard).
- **Ring 2 — companion modules** (opt-in, all implemented):
  - `agent-orchestration` — assembled `SyncOrchestrator` (incl. the 1.1 `resume(...)` human-in-the-loop
    return path) and result-granularity `StreamingOrchestrator` (ADRs 0009, 0012, 0015)
  - `agent-kernel-spring` — Spring Boot auto-configuration that wires the ingredients into an injectable
    `SyncOrchestrator` (ADR-0010)
  - `agent-provider-ollama` — local Ollama `ModelProvider`
  - `agent-provider-langchain4j` — Google AI Gemini `ModelProvider` via LangChain4j (ADR-0011)
  - `agent-store-postgres` — pgvector-backed `Retriever` (ADR-0013)
  - `agent-eval` — eval harness (cases, fake provider, JUnit glue)
- **Ring 3 — per-app bindings**: live in each app's own repo; never travel into the kernel.

## Coordinates

```xml
<dependency>
  <groupId>com.gamma.agentkernel</groupId>
  <artifactId>agent-kernel-core</artifactId>
  <version>1.1.0</version>
</dependency>
```

Companion modules share the `com.gamma.agentkernel` group id and version. `main` carries the next
development version (`1.2.0-SNAPSHOT`); apps **pin** released versions.

Published to **GitHub Packages** (`https://maven.pkg.github.com/jotder/agent-kernel`). Consumers add that
repository and a `~/.m2/settings.xml` server entry (`id=github`) with a PAT carrying `read:packages`.

## Requirements

- **Java 25** (bytecode floor — every consumer must run JVM ≥ 25). Builds on JDK ≥ 25.
- Maven (use the bundled wrapper `./mvnw`).

## Build & test

```bash
./mvnw -B verify                 # unit tests + eval harness; releasable with ZERO apps present
./tools/check-core-no-deps.sh    # assert ring-1 has zero compile/runtime deps (also run in CI)
```

## Release

Versioning is an **independent SemVer line**, now past the `1.0` freeze: the ring-1 API was confirmed
stable after the second consumer exercised the full surface (ADR-0014), additive refinements ship as
minors (e.g. the 1.1 human-in-the-loop return batch, ADR-0015), and a breaking change would mean a major.
Tagging `v*` publishes the whole reactor (all 7 modules) to GitHub Packages via GitHub Actions using the
built-in `GITHUB_TOKEN`; `main` builds and tests on every push and carries the next `-SNAPSHOT`. Apps
**pin** concrete versions. See [`CHANGELOG.md`](CHANGELOG.md).

## Status

**1.1.0 released (2026-06-06); `main` is `1.2.0-SNAPSHOT`.** The ring-1 API is frozen as of `1.0`:
the rule-of-three review across a structurally different second consumer found no reshape needed
(ADR-0014). All companion modules are implemented — `agent-orchestration`, `agent-kernel-spring`,
`agent-provider-ollama`, `agent-provider-langchain4j`, `agent-store-postgres`, `agent-eval` — and the
kernel is consumed by all three apps (UCC, CxO, CVVE). `1.1.0` closed the human-in-the-loop return path
(`HumanDecided` audit event, self-describing `HumanHandoff` result, `SyncOrchestrator.resume`; ADR-0015).
Full reactor green (`./mvnw -B verify`, zero apps present, ring-1 zero-dep).
