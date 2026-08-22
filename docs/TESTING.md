# Testing and certification

CoronaPoker keeps its QA tooling outside the distributed game artifact. This
manual is the canonical reference for test lanes, deterministic protocol
campaigns, real multi-JVM game simulation, certification profiles and adding
regressions.

The test suite lives in its own Maven module, **`tools/qa`**, kept deliberately separate from the game: `mvn package` at the repo root builds and ships the game **without** compiling or running a single test. The tests are maintainer tooling, not part of the distributed jar.

They are **JUnit 5**. Deterministic game-code tests are kept in the fast lane;
the expensive tests carry `@Tag("slow")` and are split by purpose. The normal
QA command never runs bot-quality simulations: every bot-quality class,
including the headless bot smoke, is tagged slow and only selected by the
explicit `qa-bots` profile.

- **Fast lane (the default)** — domain, money, parsers, protocol and deterministic
  smoke tests; ~700 tests in about a minute.
- **`qa-bots`** — bot-quality statistics, matchups, Monte-Carlo hand potential
  and the headless bot game-flow smoke. Its result is quality evidence for bots,
  not a substitute for a game-code regression test.
- **`qa-crypto`** — cryptographic performance, differential and cascade tests.
- **`qa-network`** — slow real-socket/stall integration checks.
- **`qa-heavy`** — aggregate of the non-bot slow lanes; statistical bot quality
  remains separate. **`qa-release`** runs fast plus the non-bot slow lanes.
  The slow lanes are never part of the normal/default run.

Each slow profile explicitly enables the `slow` tag and clears the default
exclusion, so a successful slow-lane run must report at least one executed
test. A `BUILD SUCCESS` with `Tests run: 0` is an invalid QA result and should
be treated as a profile/classpath problem.

## Running the tests

The easiest way is the **opt-in QA reactor** (`tools/reactor/pom.xml`). It builds the game and runs the tests against it in one reactor, so you don't have to `install` the game jar first or keep a version in sync:

```bash
# Fast lane — the default. Game + all deterministic code tests (~1 min).
# Bot-quality simulations are excluded by the slow tag.
mvn -f tools/reactor/pom.xml test
# Explicit equivalent for CI/NetBeans scripts:
mvn -f tools/reactor/pom.xml test -P qa-fast

# Bot-quality lane only (statistical; does not replace fast game tests).
mvn -f tools/reactor/pom.xml test -P qa-bots

# Heavy crypto lane only.
mvn -f tools/reactor/pom.xml test -P qa-crypto

# Slow real-socket integration lane only.
mvn -f tools/reactor/pom.xml test -P qa-network

# Aggregate non-bot slow lanes.
mvn -f tools/reactor/pom.xml test -P qa-heavy

# Everything except statistical bot quality: fast + non-bot slow lanes.
# Run before a release; use -P qa-bots only when bot quality is explicitly in scope.
mvn -f tools/reactor/pom.xml test -P qa-release

# A single test class (the flag skips the test-less game module).
mvn -f tools/reactor/pom.xml test -Dtest=PotMathTest -Dsurefire.failIfNoSpecifiedTests=false
```

GitHub Actions applies that same `qa-release` reactor gate to every push and
pull request targeting `master`, on the documented Java 17 baseline. It uploads
the Surefire reports and built JARs even on failure. The Windows-only
multi-JVM/Swing scenario matrix remains the local certification gate below;
the Linux CI job complements it and does not claim to replace it.
CI limits each embedded protocol campaign to one wiring case; the local
certifier owns the seeded mass volume and avoids running it twice.

## Game simulation tools (Windows / PowerShell)

One certification command composes the complete local battery. The two lower-level
runners remain available for focused diagnosis:

| Runner | Purpose | Production coverage |
|---|---|---|
| `tools/qa/run-certification.ps1` | Fail-fast full game certification after a code change | `qa-release`, mass headless campaigns and every real-game scenario below; bot-quality statistics are opt-in |
| `tools/qa/run-headless-sim.ps1` | Fast seeded campaigns and fault injection | Protocol/domain components, SRA, signed actions, pots, Rabbit/RIT, EXIT/MISDEAL/recovery models, SQLite replay and production bots |
| `tools/qa/run-real-game-e2e.ps1` | Complete local games in separate JVMs | Real encrypted sockets, `WaitingRoomFrame`, `Crupier.run()`, `rondaApuestas()`, bots, consensus and per-peer SQLite |

Ask either runner for its current options and examples:

```powershell
.\tools\qa\run-certification.ps1 -Help
.\tools\qa\run-headless-sim.ps1 -Help
.\tools\qa\run-real-game-e2e.ps1 -Help
```

Typical runs:

```powershell
# Recommended production gate: every deterministic/non-bot lane, bounded mass
# campaigns and every real-game scenario once.
.\tools\qa\run-certification.ps1

# Short preflight while iterating, or the deep release stress gate.
.\tools\qa\run-certification.ps1 -Mode quick
.\tools\qa\run-certification.ps1 -Mode stress

# Fast reproducible protocol campaign.
.\tools\qa\run-headless-sim.ps1 -Hands 5000 -Faults 5000 -BotHands 100 -Seed 3231711270

# One host, two human-client JVMs and one host bot, three complete hands.
# Windows stay hidden; any native creation is assigned to monitor 2 first.
.\tools\qa\run-real-game-e2e.ps1 -Clients 2 -Bots 1 -Hands 3 -WindowMode hidden -Screen 2

# Long real-socket soak (the supported range is 1..1000 hands).
.\tools\qa\run-real-game-e2e.ps1 -Scenario normal -Clients 2 -Bots 2 -Hands 250

# Production table-size boundaries: ten fully simulated humans, or a full
# mixed table. The host counts as one seat.
.\tools\qa\run-real-game-e2e.ps1 -Scenario normal -Clients 9 -Bots 0 -Hands 1
.\tools\qa\run-real-game-e2e.ps1 -Scenario normal -Clients 4 -Bots 5 -Hands 3

# Visual diagnosis on monitor 2, optionally with animations and production timing.
.\tools\qa\run-real-game-e2e.ps1 -WindowMode visible -Screen 2 -Animations -ProductionTiming

# Kill one client JVM during preflop and require MISDEAL + full refund + live host.
.\tools\qa\run-real-game-e2e.ps1 -Scenario abrupt-exit

# Exercise the real voluntary EXIT testament path; the host must finish normally.
.\tools\qa\run-real-game-e2e.ps1 -Scenario controlled-exit

# Exercise human bet/raise controls and exact signed monetary values.
.\tools\qa\run-real-game-e2e.ps1 -Scenario raise-mix -Clients 2 -Bots 2 -Hands 5

# Force a normal single-board all-in showdown (no RIT).
.\tools\qa\run-real-game-e2e.ps1 -Scenario allin-single-board -Clients 1 -Bots 0

# Force two all-ins and verify that the busted seat's rebuy reaches hand 2.
.\tools\qa\run-real-game-e2e.ps1 -Scenario allin-rebuy -Clients 1 -Bots 0 -Hands 5

# Force every human seat all-in, vote RIT unanimously and settle both boards.
# This deterministic scenario requires zero bots and exactly one hand.
.\tools\qa\run-real-game-e2e.ps1 -Scenario allin-rit -Clients 1 -Bots 0

# Both humans go all-in; the client then exits with its production testament.
.\tools\qa\run-real-game-e2e.ps1 -Scenario allin-controlled-exit -Clients 1 -Bots 0

# Post a signed voluntary straddle in every hand at a three-human table.
.\tools\qa\run-real-game-e2e.ps1 -Scenario straddle-post -Clients 2 -Bots 0 -Hands 3

# Distributed pause/resume and a deliberate live socket drop/reconnect.
.\tools\qa\run-real-game-e2e.ps1 -Scenario pause-resume -Clients 2 -Bots 1 -Hands 2
.\tools\qa\run-real-game-e2e.ps1 -Scenario reconnect-midhand -Clients 2 -Bots 1 -Hands 2

# Cut/reconnect at every street boundary, and combine transport faults with recovery.
.\tools\qa\run-real-game-e2e.ps1 -Scenario reconnect-every-street -Clients 2 -Bots 1 -Hands 4
.\tools\qa\run-real-game-e2e.ps1 -Scenario transport-chaos -Clients 3 -Bots 1 -Hands 5
.\tools\qa\run-real-game-e2e.ps1 -Scenario lifecycle-chaos -Clients 2 -Bots 1 -Hands 7

# Compound faults: two simultaneous JVM deaths, mixed clean/unclean exits, or
# an all-in peer dying before its mandatory showdown proof.
.\tools\qa\run-real-game-e2e.ps1 -Scenario dual-abrupt-exit -Clients 3 -Bots 1
.\tools\qa\run-real-game-e2e.ps1 -Scenario mixed-exit-crash -Clients 3 -Bots 1
.\tools\qa\run-real-game-e2e.ps1 -Scenario allin-abrupt-exit -Clients 2 -Bots 0

# Stop a live hand, recover/replay it, then deal and settle a fresh next hand.
.\tools\qa\run-real-game-e2e.ps1 -Scenario force-recover -Clients 1 -Bots 2 -Hands 2

# Repeat the full stop/rebuild/recover cycle on hands 1 and 3; hands 2 and 4
# must be newly dealt and settled with every peer still in agreement.
.\tools\qa\run-real-game-e2e.ps1 -Scenario double-force-recover -Clients 1 -Bots 2 -Hands 4

# Kill and relaunch the same client identity, recover, then complete a new hand.
.\tools\qa\run-real-game-e2e.ps1 -Scenario crash-rejoin-recover -Clients 1 -Bots 2 -Hands 2

# Add a brand-new client during recovery; it observes the replay, then joins hand 2.
.\tools\qa\run-real-game-e2e.ps1 -Scenario force-recover-add-client -Clients 2 -Bots 2 -Hands 2
```

The complete runner executes one wiring case for each protocol campaign inside
`qa-release`, then applies the requested mass volume once in its dedicated
headless phase. This avoids running the same 5,000-case campaign twice without
dropping any game-integrity test class. Statistical bot-quality tests are excluded
by default because they measure playing strength rather than protocol integrity;
use `-IncludeBotQuality` after changing bot AI or evaluation code.

Certification modes use intelligent defaults; any explicit numeric option
overrides the selected mode:

| Mode | Intended use | Headless hands/faults | Real-game matrix |
|---|---|---:|---|
| `quick` | Iteration preflight | 50 / 50 | Critical subset, one seed, 5-hand soak |
| `balanced` | Default production gate | 500 / 500 | Every scenario once, 20-hand soak |
| `stress` | Deep release/adversarial gate | 5,000 / 5,000 | Every race-sensitive scenario, including heads-up, with three seeds; 50-hand soak |

By default the console shows compact colored phase progress. Full Maven and JVM
output is retained under `target/certification/<timestamp>/`; `summary.csv` and
`summary.json` are machine-readable. Use `-VerboseOutput` only when live raw
output is useful. All three scripts build/install the exact checkout into the
ignored repository-local `.m2/repository`, preventing stale user-cache jars.
After diagnosing a failed real-game phase, `-StartAtScenario <label>` continues
from that stable scenario label. It skips QA/headless and is evidence to combine
with the preceding checkpoint, not a standalone release certificate; the final
release gate must still run normally from the beginning.
Real-game phases report completed hands as `hands N/M`. A premature table end
fails immediately; accelerated runs also fail after 120 seconds without a newly
completed hand. Production-timing runs keep the wider scenario timeout so a
slow human action is not misclassified as a hang.
Force-recovery scenarios explicitly allow the old table to end while its
replacement mounts; each observed table replacement resets that bounded
inactivity window. Missing recovery or a replacement that stalls still fails.
Controlled-EXIT scenarios require the departing peer's explicit
`CP_E2E_EXPECTED_EXIT_COMPLETE` terminal. Any `CP_E2E_FAIL` marker makes the
certification phase fail even if the surrounding JUnit scenario returned zero.
They find Maven through `mvnw.cmd`, `PATH`, or Apache NetBeans, in that order.
Java 17 and Maven (standalone or NetBeans) are the only tool prerequisites;
dependencies are downloaded automatically on the first run.

Scenario contracts:

| Scenario | What it tests | Green result |
|---|---|---|
| `normal` | Ordinary multi-JVM hands through production sockets and Crupiers | Every hand settles with identical consensus hashes and balances |
| `raise-mix` | Human peers use real bet/raise controls across several streets | Exact signed cents, consensus and settlement remain identical |
| `allin-single-board` | Heads-up all-in without RIT | Atomic POTCARDS, showdown and single-board settlement agree |
| `allin-rebuy` | At least five consecutive real all-in hands | A bust is followed by a later hand with increased cumulative buy-in; ties cannot make the scenario flaky |
| `allin-reconnect` | An all-in peer loses its socket before showdown | It reconnects and supplies the mandatory POTCARDS proof before exact settlement |
| `abrupt-exit` | Client process dies during preflop while another client survives | MISDEAL, full refund, zero pot and every survivor reaches recovery |
| `controlled-exit` | Client sends EXIT while another client survives | Remaining peers settle identically without MISDEAL |
| `allin-rit` | Human seats go all-in and unanimously choose run it twice | Both boards unlock and settle without divergence |
| `rit-network-cut` | A remote peer loses its socket immediately after its real RIT vote | It reconnects; SIDE-B unlocks and both boards settle identically |
| `allin-controlled-exit` | Heads-up players go all-in, then the client sends controlled EXIT | Pocket proof/testament suffice to settle without MISDEAL or blocked host |
| `straddle-post` | Human UTG posts through the production dialog and signed protocol | Every hand posts and all peers settle identically |
| `straddle-network-cut` | The remote straddler disconnects after signed acceptance | Deferred pocket delivery survives reconnect and later hands remain unanimous |
| `pause-resume` | Host pauses all peers mid-hand and resumes | Every peer observes both states and two hands settle |
| `reconnect-midhand` | A live client socket is deliberately closed | Secure reconnect succeeds without denial and two hands settle |
| `reconnect-twice` | Two different clients lose their sockets in consecutive hands | Both reconnect securely and a third hand settles identically |
| `reconnect-every-street` | One client disconnects at preflop, flop, turn and river in four hands | Every street transition resumes once and all four hands converge |
| `reconnect-storm` | A freshly reconnected socket fails again, followed by another peer | Repeated ownership changes do not duplicate, lose or reorder game commands |
| `dual-reconnect` | Two clients disconnect together during one hand | Both authenticate again and play continues with unanimous state |
| `host-channel-flap` | Every client channel drops while the host process remains alive | All clients reconnect and the table completes subsequent play without divergence |
| `reconnect-force-recover` | A client channel is cut after force-recovery starts | No ordinary reconnect loop is spawned; recovery and two fresh hands complete |
| `transport-chaos` | Dual reconnect, immediate relapse, pause, force-recover and later reconnect | All transport/lifecycle transitions converge across five hands |
| `lifecycle-chaos` | Reconnect, pause and two force-recovery cycles share one seven-hand table | Both recovered and fresh hands remain live, unanimous and money-conserving |
| `dual-abrupt-exit` | Two client JVMs die together while another human remains | One MISDEAL, exact refund and recovery-ready survivors |
| `mixed-exit-crash` | One client sends a valid EXIT while another JVM dies | The testament is honored, the missing unlock cancels safely and survivors recover |
| `allin-abrupt-exit` | An all-in client dies before its mandatory showdown proof | No partial settlement or accusation; exact refund and recovery |
| `force-recover` | Hand 1 is stopped; lobby, sockets and table are rebuilt | Interrupted hand recovers and a fresh hand 2 settles |
| `double-force-recover` | The same session is force-recovered during hands 1 and 3 | Both recoveries succeed and fresh hands 2 and 4 settle |
| `crash-rejoin-recover` | Client JVM dies, then restarts with the same home/nick/key | MISDEAL refunds safely; the peer rejoins recovery and completes hand 2 |
| `force-recover-add-client` | A brand-new client joins the rebuilt recovery lobby | It passively observes the old hand, then participates in fresh hand 2 |
| `force-recover-add-two` | Two brand-new clients join the rebuilt recovery lobby together | Both observe recovery safely and verify fresh hand 2 |
| `force-recover-swap-client` | One original peer disappears in recovery and a new peer replaces it | Missing history is handled safely and the replacement verifies fresh hand 2 |

The real-game runner defaults to hidden windows, disabled sound/animations and
accelerated test timing. It preserves poker rules, signed protocol, accounting,
settlement, recovery and player lifecycle, while the harness action driver owns
turn input and therefore disables the Swing action clocks. `-ProductionTiming`
restores both normal pauses and real action clocks. Each peer gets a temporary isolated home,
identity and SQLite database, removed after the run. A run is green only when
all peers finish with matching consensus hashes and canonical balances and no
fatal/error dialog. Host + clients + bots cannot exceed ten seats.
`-Seed` fixes the action driver and scenario schedule. Normal certification uses
the same platform CSPRNG as production. The optional `-DeterministicCrypto`
diagnostic also seeds each isolated node's QA-only entropy stream, but it is not
a release gate and runs are not promised to be byte-for-byte identical: thread
scheduling, timing and fresh identities may still vary. Scenario oracles verify
protocol transitions and final outcomes, not specific cards or hashes. The
seeded generator exists only under `tools/qa/src/test` and is not packaged in the
CoronaPoker JAR. Use different seeds across stress repetitions to explore other
hands and races.
Every runner also assigns a fresh Maven QA home per invocation. Persisted
owner-only identity files are therefore never reused by a later CI, service or
sandbox account; failure to read a key created in the current run remains fatal.
The exact per-run home is deleted in a guarded `finally` block on success or
failure, while certification logs and machine-readable reports are retained.
Accelerated `TEST_MODE` still executes end-of-hand rebuys, exits, Rabbit
completion, consensus and settlement; apart from driver-owned action clocks, it
only shortens presentation work. Busted
seats are deterministically rebought or made spectators according to the table
configuration, so long soaks cannot continue with fake zero-stack active seats.
Run `-Help` for the current scenario list and every option.

The balanced certification matrix is deliberately broader than a single happy
path: a 20-hand mixed-table soak, heads-up and ten-seat mixed/all-human games, human raises,
single-board and RIT all-ins, straddle, disconnects at every street boundary,
simultaneous and repeated reconnects, transport/lifecycle chaos, concurrent and
mixed departures, all-in proof loss, repeated recovery, client restart and
dynamic recovery rosters. Balanced runs every scenario once; stress uses three
distinct deterministic seeds for race-sensitive cases. Use `-EdgeRepeats` or
`-SoakHands` for an explicit custom bar.

Coverage is layered rather than claimed from one harness:

| Integrity area | Deterministic QA | Mass headless campaign | Real sockets + Crupier |
|---|---:|---:|---:|
| Shuffle, cards, unlocks and POTCARDS | Yes | Yes | Normal, all-in/RIT and post-vote/post-all-in disconnects |
| Signed actions, cents and betting rules | Yes | Yes | Normal and `raise-mix` |
| Consensus, pots and settlement | Yes | Yes | Every completed scenario |
| EXIT, MISDEAL and refunds | Yes | Yes | Controlled and abrupt departures |
| Recovery and roster changes | Yes | Yes | Force, double, restart and add-client |
| Transport and lifecycle | Yes | Yes | Every-street, simultaneous, repeated and recovery-overlap faults |
| Straddle and RIT | Yes | Yes | Normal plus disconnect-after-decision variants |
| Rabbit and malformed hostile frames | Yes | Yes | Not UI-driven; verified below sockets |

The headless runner is the high-volume/adversarial layer; the real-game runner
is the production-orchestration layer. Neither replaces the other. Pure visual
painting/layout and behavior across two physical machines still require manual
inspection.

## Lane order and ownership

Run the lanes in this order when auditing or preparing a release. A failure in
one lane is recorded against that lane; it is not hidden by a later aggregate
run.

| Order | Lane | Contents | Normal run? |
|---:|---|---|---|
| 1 | qa-fast | Rules, money, pots, recovery, parsers, framing, deterministic smoke and TDD regressions | Yes |
| 2 | qa-crypto | Heavy crypto/SRA differential, cascade and performance checks | No |
| 3 | qa-network | Real socket framing/stall checks | No |
| 4 | qa-heavy | Aggregate non-bot slow lanes | No, explicit only |
| 5 | qa-bots | Statistical bot quality, matchups, Monte-Carlo and bot-flow smoke | No, explicit only |
| 6 | qa-release | Fast plus non-bot slow lanes; bot quality remains separate | No, explicit only |

The bot lane is deliberately last and separate: its statistical `FAIL` signal
means that a quality threshold was not met for that sample, not that a
deterministic game-code assertion failed. It must not gate ordinary code tests
or be silently folded into the default lane.

Add `-o` (offline) once your local Maven cache is warm to skip dependency checks. The bot simulations honour two volume knobs for fast local iteration, e.g. `-Dqa.sessions=40 -Dqa.hands=25`.

If the opt-in reactor reports that game classes such as `Helpers` or `Crupier`
are missing while compiling `tools/qa`, treat that as a Maven/Windows
classpath or permissions problem, not as a game-test result. Run the standalone
fallback from NetBeans instead: first `mvn -DskipTests install` at the root,
then the `tools/qa` command below with the same root version. Record the
environmental failure in the audit index and do not turn it into a production
change.

<details><summary><b>Running the <code>tools/qa</code> module on its own (without the reactor)</b></summary>

You can run the module standalone, but then you must publish the game jar first and match its version:

```bash
mvn -DskipTests install                                       # publish CoronaPoker to your local ~/.m2
mvn -f tools/qa/pom.xml test -Dcoronapoker.version=<root pom version>       # fast, no bot quality
mvn -f tools/qa/pom.xml test -P qa-bots -Dcoronapoker.version=<root pom version>  # bot quality only
```

The standalone module also accepts `-P qa-crypto`, `-P qa-network`,
`-P qa-heavy` (non-bot slow lanes) and `-P qa-release` (fast plus non-bot slow
lanes). These are always explicit; the bare command above never runs bot-quality
simulations. Only `-P qa-bots` selects the statistical bot lane.
</details>

## Which tests to run for what you touch

| If you change… | Run |
|---|---|
| Game logic, pot / side-pot / blind / bet math, hand-integrity chains | **Fast lane** — it already guards these |
| The **bot AI** (`bot/`, `org/alberta/`, `Bot.java`) | **`-P qa-bots`** — statistical matchups + Monte-Carlo potential |
| The **crypto** stack (`crypto/`, the SRA cascade) | **`-P qa-crypto`** — perf / differential / cascade suite |
| **Networking** (`Net*`, `WireFrame`, `Participant`) | Fast lane covers wire & framing; add **`-P qa-network`** for socket-stall checks |
| Anything, **before committing or opening a PR** | **`-P qa-release`** |
| Before a **release** | **`-P qa-release`** plus the adversarial automated audit; manual-only residuals are reported separately |

Rule of thumb: **fast lane on every change**, the relevant slow lane when you
edited that subsystem or before merging, and **`-P qa-release` before a release**.
Manual play is only a short complement for flows that genuinely require Swing,
two live clients or human timing; it never replaces an automatable regression
test. Record those steps and the environment in the audit report.

## Adding a test

Put it in the matching package under `tools/qa/src/test/java`. If it is slow — a
bot simulation, a crypto perf/fuzz test, or a real-socket stall check — annotate
it with `@Tag("slow")` and keep it in the matching `qa-bots`, `qa-crypto` or
`qa-network` package so its lane remains explicit. Fast unit/domain tests
must not be hidden in a slow lane. Add a deterministic red test before changing
production, then keep the regression in the fast lane unless it genuinely
requires a slow harness.

## Scope boundary

The automated simulator exercises production sockets, `Crupier`, betting,
cryptographic messages, consensus, settlement, SQLite and lifecycle transitions.
It does not certify subjective rendering quality, physical audio devices or
real Internet/NAT behavior; use focused manual checks for those surfaces.
