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
- Extracted process audio ownership and its exactly-once activation gate into
  the neutral `AudioService`. The Swing backend is configured before process
  services start and is activated at the same characterized startup point as
  before: volume/mute state, asynchronous endpoint warm-up, `init.wav`, the
  one-time `uncover.wav` preload and background music retain their ordering.
- Failed audio activation cannot be retried in the same process. Application
  failure or normal process close still stops the volume timer, TTS, previews,
  danger alerts, MP3 loops, WAV playback and preloaded clips exactly once.
- The approved GDX demo remains byte-for-byte unchanged and continues to own
  its prototype audiovisual playback. Its production `AudioService` backend is
  therefore still silent; unifying that ownership without altering the
  canonical renderer remains a phase-2 task.
- Moved the application version and latest-release URI to neutral
  `ApplicationMetadata`, retaining the current visible version `24.10`.
- Extracted CoronaPoker release discovery into `UpdateService`: both launchers
  own the same service, checks are serialized on a process-owned daemon,
  distinguish available/current/unavailable results, preserve three silent
  attempts with a 10-second per-attempt bound and are cancelled on shutdown.
  Swing now only maps that typed result to its existing controls and dialogs.
- Updater download/process handoff and MOD update checks still use the legacy
  Swing/`Helpers` path. The frontend-network exit criterion is therefore not
  yet satisfied by this incremental extraction.
- Preserved the classic direct entry point: `Init.main` delegates to
  `SwingLauncher`.

Commits:

- `e3376dfbf feat(core): add neutral application lifecycle`
- `f26e4c3a9 refactor(swing): publish application lifecycle events`
- `31ffb2dc8 feat(app): add shared frontend launchers`
- `5d7613383 refactor(core): own process secure random service`
- `407d642fc refactor(core): own sqlite connection lifecycle`
- `bc6ef8880 refactor(core): own persistent preferences`
- `ddd5ef521 refactor(core): own process audio lifecycle`
- `a335eec36 refactor(core): own release update checks`

## Verification

- `mvn -f migration-reactor/pom.xml ... clean verify`: success for all six
  reactor projects.
- Core lifecycle/bootstrap/service tests: 14 passed, including connection
  release/reopen, permanent process close, failure cleanup, atomic preference
  persistence, deferred shutdown flush, corrupt-file rescue, exactly-once audio
  activation, failed-activation cleanup and typed update retry/outcome behavior.
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
| `dist/CoronaPoker-24.11-swing.jar` | 430,082,265 | `421A58CEDBF316A80F5D0C48A6C5DCB9D7E28538DDEFC8264149C470477AE52F` |
| `dist/CoronaPoker-24.11-gdx.jar` | 432,478,074 | `EDBBFEBA199AEB96FE0F79373258A665FF53E174F93372ECA5ED6B0DE4D5CBCD` |

## Still pending in phase 2

- Move SQLite schema creation, migrations and integrity verification out of
  `Helpers`, then migrate the legacy QA connection-injection seam.
- Extract identity/crypto orchestration beyond the process CSPRNG.
- Replace the transitional `Helpers.PROPERTIES`/`SwingServiceBridge` exposure
  with typed configuration and appearance projections.
- Move updater download/process handoff and MOD update networking out of Swing
  and `Helpers` into typed process services.
- Connect GDX audiovisual playback to the process `AudioService` without
  changing or approximating the canonical demo renderer and without starting
  a second music/audio owner.
- Make process shutdown close those concrete resources and remove remaining
  normal-path `System.exit` ownership from frontends.
- Prove that both launchers initialize the complete non-placeholder service set
  and that process audio/music starts exactly once.

The Swing and GDX smokes confirm startup and asset loading only. They are not a
manual visual comparison, capture matrix, audiovisual timing validation or
240 Hz frame-pacing certification.
