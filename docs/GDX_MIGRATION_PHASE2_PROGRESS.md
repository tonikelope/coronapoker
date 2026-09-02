# GDX full migration - phase 2 progress

Date: 2026-09-02

Branch: `feature/gdx-full-migration`

Status: **in progress; phase-2 exit criteria are not yet satisfied**

## Completed in this increment

- Established a single renderer-neutral core source tree under
  `src/main/java/com/tonikelope/coronapoker/core` and made the classic build and
  migration reactor compile that same source.
- Added `CoronaPokerApplication`, ordered process-service ownership and an
  explicit lifecycle: `NEW`, `STARTING`, `MENU`, `SESSION`, `TABLE`,
  `STOPPING`, `TERMINATED`, `FAILED`.
- Connected Swing startup, waiting-room creation, successful table creation,
  return-to-menu paths and launcher close to explicit lifecycle events.
- Added `SwingLauncher` and `GdxLauncher`. Both construct the application via
  `CoronaPokerBootstrap`; the GDX launcher delegates rendering to the approved
  `CoronaPokerGdxLauncher` and never constructs an alternative renderer.
- Extracted the process CSPRNG into the first concrete typed shared service,
  `SecureRandomService`. Swing consumes the same generator through the
  bootstrap instead of constructing it in `Init`.
- Extracted SQLite driver/configuration/connection ownership into the typed
  process service `DatabaseService`. Closing a game releases the current
  connection so a later game can reopen it; application failure or process
  shutdown closes the service permanently.
- Kept schema creation, migrations and startup integrity checks on the
  characterized classic path for now. `Init.SQLITE` remains only as a
  deprecated compatibility seam for the existing QA injection tests; normal
  production startup and access use `DatabaseService`.
- Extracted preference-file ownership into the neutral `PreferencesService`.
  Both launchers now load the same typed service; Swing temporarily exposes its
  `Properties` object through `SwingServiceBridge` while the existing settings
  callers are migrated. Atomic writes, 500 ms coalescing, shutdown flush and
  corrupt-file rescue-copy behavior are preserved without a Swing timer in the
  process service.
- Preserved the classic direct entry point: `Init.main` delegates to
  `SwingLauncher`.

Commits:

- `e3376dfbf feat(core): add neutral application lifecycle`
- `f26e4c3a9 refactor(swing): publish application lifecycle events`
- `31ffb2dc8 feat(app): add shared frontend launchers`
- `5d7613383 refactor(core): own process secure random service`
- `407d642fc refactor(core): own sqlite connection lifecycle`
- `bc6ef8880 refactor(core): own persistent preferences`

## Verification

- `mvn -f migration-reactor/pom.xml ... clean verify`: success for all six
  reactor projects.
- Core lifecycle/bootstrap/service tests: 10 passed, including connection
  release/reopen, permanent process close, failure cleanup, atomic preference
  persistence, deferred shutdown flush and corrupt-file rescue.
- Migration architecture tests: 4 passed, including the neutral import
  boundary, dependency direction, canonical demo source and common bootstrap
  launcher wiring.
- Classic install followed by `tools/qa`: 1,106 tests, 0 failures, 0 errors,
  0 skipped.
- Classic reactor JAR smoke reached `CSPRNG OK` and
  `Initialization complete. Ready` with an isolated temporary user home.
- Final GDX JAR smoke opened at `2560x1440 @ 240 Hz` and loaded the Goliat and
  PepsiMan shuffle animations plus the Rounders cinematic. The process was
  then stopped deliberately.
- `git diff --exit-code 627c71e4f --
  prototype-gdx/src/main/java/com/tonikelope/coronapoker/gdxdemo`: clean.

Final artifacts for this increment:

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `dist/CoronaPoker-24.11-swing.jar` | 430,068,538 | `AFDABE97190A570C99C76C2388F29B7DCBD619D3F2508F4DDD5303BCBEB54723` |
| `dist/CoronaPoker-24.11-gdx.jar` | 432,467,334 | `57FFAE62FBEFF50E0290AF42DAEEBF3D8C3F6969556B561BDE1CB30753F20229` |

## Still pending in phase 2

- Move SQLite schema creation, migrations and integrity verification out of
  `Helpers`, then migrate the legacy QA connection-injection seam.
- Extract identity/crypto orchestration beyond the process CSPRNG.
- Replace the transitional `Helpers.PROPERTIES`/`SwingServiceBridge` exposure
  with typed configuration and appearance projections.
- Extract updates and audio as typed shared services.
- Make process shutdown close those concrete resources and remove remaining
  normal-path `System.exit` ownership from frontends.
- Prove that both launchers initialize the complete same service set and that
  process audio/music starts exactly once.

The GDX smoke confirms startup and asset loading only. It is not a manual
visual comparison, capture matrix, audiovisual timing validation or 240 Hz
frame-pacing certification.
