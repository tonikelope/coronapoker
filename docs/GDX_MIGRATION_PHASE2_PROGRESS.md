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
- Preserved the classic direct entry point: `Init.main` delegates to
  `SwingLauncher`.

Commits:

- `e3376dfbf feat(core): add neutral application lifecycle`
- `f26e4c3a9 refactor(swing): publish application lifecycle events`
- `31ffb2dc8 feat(app): add shared frontend launchers`
- `5d7613383 refactor(core): own process secure random service`
- `407d642fc refactor(core): own sqlite connection lifecycle`

## Verification

- `mvn -f migration-reactor/pom.xml ... clean verify`: success for all six
  reactor projects.
- Core lifecycle/bootstrap/service tests: 7 passed, including connection
  release/reopen, permanent process close and failure cleanup.
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
| `dist/CoronaPoker-24.11-swing.jar` | 430,062,381 | `985678F8281A0D39CB40D90182447C0CE6DCE1DBCC2F68334C2277A8D459370C` |
| `dist/CoronaPoker-24.11-gdx.jar` | 432,462,399 | `1841723DD72A7D7F53BD70703B8C63708D44D51C0FF60446C0F73C6F5ED7B4D8` |

## Still pending in phase 2

- Move SQLite schema creation, migrations and integrity verification out of
  `Helpers`, then migrate the legacy QA connection-injection seam.
- Extract identity/crypto orchestration beyond the process CSPRNG.
- Extract preferences/configuration, updates, appearance and audio as typed
  shared services.
- Make process shutdown close those concrete resources and remove remaining
  normal-path `System.exit` ownership from frontends.
- Prove that both launchers initialize the complete same service set and that
  process audio/music starts exactly once.

The GDX smoke confirms startup and asset loading only. It is not a manual
visual comparison, capture matrix, audiovisual timing validation or 240 Hz
frame-pacing certification.
