# GDX migration phase 2 source map

This file records source ownership changes before they are applied, as required
by the migration plan.

| Date | Source | Destination | Reason |
|---|---|---|---|
| 2026-09-02 | `migration-reactor/coronapoker-core/src/main/java/com/tonikelope/coronapoker/core/package-info.java` | `src/main/java/com/tonikelope/coronapoker/core/package-info.java` | Make the neutral package available to the classic build and to the migration reactor from one physical source tree. |

The `coronapoker-core` module compiles the destination directory directly. The
`coronapoker-swing` module excludes that package from its reused classic source
tree and consumes it only through its dependency on `coronapoker-core`. No
renderer source moves in this phase; the approved GDX demo remains at its
original path.

## Temporary phase-2 exception

As of 2026-09-02 both frontend launchers use `CoronaPokerBootstrap`. The shared
bootstrap owns the process CSPRNG, SQLite connection lifecycle, persistent
preferences and the audio lifecycle/activation boundary. Schema and integrity
orchestration, identity, updates and the concrete GDX audio backend still
belong outside the neutral core or remain placeholders and must be extracted
one at a time with characterization tests. This exception is removed only when
those concrete services are owned by the shared bootstrap; the presence of
both launchers by itself does not satisfy the phase-2 exit criteria.
