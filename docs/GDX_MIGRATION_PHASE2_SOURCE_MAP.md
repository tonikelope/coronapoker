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
