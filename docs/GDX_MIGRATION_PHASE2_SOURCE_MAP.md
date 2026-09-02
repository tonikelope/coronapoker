# GDX migration phase 2 source map

This file records source ownership changes before they are applied, as required
by the migration plan.

| Date | Source | Destination | Reason |
|---|---|---|---|
| 2026-09-02 | `migration-reactor/coronapoker-core/src/main/java/com/tonikelope/coronapoker/core/package-info.java` | `src/main/java/com/tonikelope/coronapoker/core/package-info.java` | Make the neutral package available to the classic build and to the migration reactor from one physical source tree. |
| 2026-09-02 | `src/main/java/com/tonikelope/coronapoker/GameCommandId.java` | `src/main/java/com/tonikelope/coronapoker/core/network/GameCommandId.java` | Share the exact process-wide GAME identifier contract between Swing and GDX. |
| 2026-09-02 | `src/main/java/com/tonikelope/coronapoker/GameCommandType.java` | `src/main/java/com/tonikelope/coronapoker/core/network/GameCommandType.java` | Make the closed protocol registry renderer-neutral. |
| 2026-09-02 | `src/main/java/com/tonikelope/coronapoker/GameCommandGate.java` | `src/main/java/com/tonikelope/coronapoker/core/network/GameCommandGate.java` | Reuse the same direction and replay checks in both transports. |
| 2026-09-02 | `src/main/java/com/tonikelope/coronapoker/SessionOutbox.java` | `src/main/java/com/tonikelope/coronapoker/core/network/SessionOutbox.java` | Reuse the bounded, generation-aware reliable outbox without UI dependencies. |
| 2026-09-02 | `src/main/java/com/tonikelope/coronapoker/ConfirmationTracker.java` | `src/main/java/com/tonikelope/coronapoker/core/network/ConfirmationTracker.java` | Share request-scoped command acknowledgement state. |

The `coronapoker-core` module compiles the destination directory directly. The
`coronapoker-swing` module excludes that package from its reused classic source
tree and consumes it only through its dependency on `coronapoker-core`. No
renderer source moves in this phase; the approved GDX demo remains at its
original path.

## Temporary phase-2 exception

As of 2026-09-02 both frontend launchers use `CoronaPokerBootstrap`. The shared
bootstrap owns the process CSPRNG, SQLite connection lifecycle, persistent
preferences, the audio lifecycle/activation boundary, application metadata and
CoronaPoker release discovery. Schema and integrity orchestration, identity,
updater download/process handoff, MOD update networking and the concrete GDX
audio backend still belong outside the neutral core or remain placeholders and
must be extracted one at a time with characterization tests. This exception is
removed only when those concrete services are owned by the shared bootstrap;
the presence of both launchers by itself does not satisfy the phase-2 exit
criteria.
