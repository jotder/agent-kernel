#!/usr/bin/env bash
#
# Ring-1 guarantee (ADR-0001): agent-kernel-core must have ZERO compile/runtime dependencies.
# This is enforced at build time by the Maven Enforcer (bannedDependencies) in the core pom;
# this script is the explicit CI/local guard that prints a clear report. Run from anywhere.
#
set -euo pipefail
cd "$(dirname "$0")/.."

echo "Checking ring-1 (agent-kernel-core) for compile/runtime dependencies..."

# dependency:list with runtime scope includes compile + runtime, excludes test/provided.
# Any coordinate line that survives is a forbidden ring-1 dependency.
out="$(./mvnw -q -pl agent-kernel-core dependency:list -DincludeScope=runtime -DexcludeTransitive=false 2>/dev/null || true)"
deps="$(printf '%s\n' "$out" | grep -E '^[[:space:]]+[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:' || true)"

if [ -n "$deps" ]; then
  echo "FAIL: agent-kernel-core has forbidden compile/runtime dependencies (ring-1 must be zero-dep):"
  printf '%s\n' "$deps"
  echo "Move anything you need into a ring-2 companion module (ADR-0001/ADR-0002)."
  exit 1
fi

echo "OK: agent-kernel-core has zero compile/runtime dependencies."
