# agent-kernel

A reusable, **framework-agnostic agent core** shared by three agent applications:
**UCC File-Processor assist**, **CVVE V&V-as-a-Service**, and the **Real-Estate CxO Decision-Support** agent.
The kernel owns the shared spine — *LLM orchestrates & narrates; deterministic tools compute & validate* —
and each app supplies only its capabilities, tools, context, and orchestration shell.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design and [`docs/adr/`](docs/adr/) for the
founding decisions (ADRs 0001–0008).

## Three rings

- **Ring 1 — `agent-kernel-core`**: the pure core. **Zero runtime dependencies** (JDK only). The build
  *fails* if any compile/runtime dependency is added (enforced via the Maven Enforcer + a CI guard).
- **Ring 2 — companion modules** (opt-in): `agent-provider-ollama`, `agent-eval` today; `agent-kernel-spring`,
  `agent-store-postgres`, `agent-hitl`, `agent-orchestration`, `agent-provider-langchain4j` arrive at R1.
- **Ring 3 — per-app bindings**: live in each app's own repo; never travel into the kernel.

## Coordinates

```xml
<dependency>
  <groupId>com.gamma.agentkernel</groupId>
  <artifactId>agent-kernel-core</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

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

Versioning is an **independent SemVer line** (unstable `0.x` until a 2nd consumer reshapes the API, then
`1.0`). Merge to `main` makes `0.x-SNAPSHOT` available; tagging `v*` publishes a release to GitHub Packages
via GitHub Actions using the built-in `GITHUB_TOKEN`. Apps **pin** concrete versions.

## Status

**K1 complete.** Ring-1 core (model/tool/agent/reason/error/observe/retrieve), `agent-provider-ollama`,
and the `agent-eval` harness are implemented and tested (`./mvnw -B verify` green, zero apps, ring-1
zero-dep). Companion modules, the assembled orchestrator, and the `1.0` freeze are **R1** (driven by the
2nd consumer). UCC consumes this `0.x` at U1.
