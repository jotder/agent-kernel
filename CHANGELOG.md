# Changelog

All notable changes to **agent-kernel** are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
this project follows an **independent SemVer line** (unstable `0.x` until a 2nd consumer reshapes the API).

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

### Deferred to K1
- Ring-1 types (Model/Tool/Agent/Reason/Error/Observe/Retrieve), `OllamaModelProvider`
  (+ the `langchain4j-ollama` dependency), and the eval harness (+ the `jackson-databind` dependency).
  Versions are pinned in the parent now; the module dependencies are added when the code lands.

### Deferred to R1
- Companion modules (`agent-kernel-spring`, `agent-store-postgres`, `agent-hitl`, `agent-orchestration`,
  `agent-provider-langchain4j`), the assembled orchestrator, and the `1.0` API freeze.
