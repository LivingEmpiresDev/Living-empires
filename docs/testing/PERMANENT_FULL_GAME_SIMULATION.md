# Permanent full-game simulation programme

This is the baseline integration for a continuously expanding simulation suite. The workflow runs the existing JVM test suite and builds the Android debug APK. **It does not yet claim to run a headless 100–500-turn game.** A deterministic headless scenario runner and assertion-based game tests must be added before that claim can be made.

## Gates
- Every PR targeting master: regression tests and Android build must pass.
- Every stage: add scenario tests for its mechanics and preserve all previous stage tests.
- Every audit defect: reproduce with a failing test, fix, then keep the test permanently.
- Before merge: inspect results, verify Stage 4 baseline and Stage 5 branch integration, and obtain player acceptance when UI/gameplay changed.

## Planned deterministic scenario harness
Implement a JVM test runner reusing the existing core game engine, with fixed RNG seeds, reproducible map/settings, and machine-readable JSON reports. Scenarios must cover:
- 100-turn normal game with 4/8 AI players; 500-turn stress game; multiple seeds
- city founding, construction, research, production, movement, combat, victory/defeat
- Stage 2: 20-turn non-aggression pacts, expiry, war edge cases, persistence
- Stage 3: defensive pacts, ally interventions, joint wars, expiry, persistence
- Stage 4: player/AI peace proposals, war-duration logic, counteroffers, city states
- Stage 5: every approved diplomacy mechanic, with explicit test IDs
- saves and reloads at key transitions; no crashes, hangs, invariant violations
- performance measurements: per-turn duration, memory peak, slowest AI decision
- regression fixtures for each audit issue, tagged with its issue/PR identifier

A scenario is passing only when explicit assertions pass; merely finishing turns is insufficient. Record seed, commit SHA, scenario ID, turns completed, assertions, duration and errors. Set thresholds using measured baseline data rather than invented limits.

## Workflow progression
1. Current workflow: executable existing tests + Android APK build.
2. Add deterministic headless game simulation test class and scenario registry.
3. Add 100/500-turn matrix jobs, JSON/JUnit reporting and retained failure saves.
4. Add performance trend comparisons and audit regression registry.
5. Require successful checks in branch protection once verified.

Do not label this system fully operational until steps 2–4 are implemented and verified in GitHub Actions.
