# Adding test scenarios

This guide is for contributors who need new automated gameplay coverage. Read
[Testing and certification](TESTING.md) first for commands, modes, costs and
report interpretation.

## Choose the lowest layer that proves the behaviour

Do not turn every regression into a complete multiplayer scenario.

| Behaviour | Primary layer |
|---|---|
| Pure rule, codec, persistence or money calculation | Unit test in `coronapoker-core` |
| GDX command, projection, dialog state or lifecycle binding | Focused test in `coronapoker-gdx` |
| Complete game flow using production core objects | `GdxReconnectScenarioTest` |
| Real sockets, separate processes, crash or recovery | `GdxMultiprocessScenarioTest` |
| Large seeded protocol volume | `tools/qa/headless-sim.cmd` campaign |

A mocked projection test cannot prove reconnect, crash handling or a process
boundary. A multiprocess scenario is also unnecessary when a pure rule test can
prove the invariant faster and more precisely.

## One executable catalogue

Every complete gameplay scenario belongs to
`GdxScenarioContract.CERTIFICATION_SCENARIOS`. Historical ports and new product
scenarios use the same map, runner and result.

The surrounding contracts are:

1. `tools/qa/reference/swing-gold-scenarios.tsv` preserves the historical
   migration baseline. It is not executable.
2. `GdxScenarioContract.java` owns the one executable mapping from scenario key
   to JUnit method names.
3. `GdxScenarioContractTest.java` guards mapping, provenance and harness
   invariants.
4. `GdxMultiprocessScenarioTest.java` owns release-level process scenarios.
5. `GdxMultiprocessNodeMain.java` drives each host or client process.
6. `run-certification.ps1` reads the Java map and applies repetition, depth,
   seed, isolation and reporting policy.

Do not add a second scenario list to PowerShell or Markdown. The live catalogue
is always visible through:

```powershell
.\qa.cmd list
```

## Important files

| File | Responsibility |
|---|---|
| `modules/coronapoker-gdx/src/test/java/com/tonikelope/coronapoker/gdx/scenarios/GdxScenarioContract.java` | Executable scenario catalogue and historical reference set |
| `modules/coronapoker-gdx/src/test/java/com/tonikelope/coronapoker/gdx/scenarios/GdxScenarioContractTest.java` | Catalogue and scenario-harness contracts |
| `modules/coronapoker-gdx/src/test/java/com/tonikelope/coronapoker/gdx/scenarios/GdxReconnectScenarioTest.java` | Production-core game flows observed through GDX |
| `modules/coronapoker-gdx/src/test/java/com/tonikelope/coronapoker/gdx/GdxNetworkHumanProjectionIntegrationTest.java` | Real network state projected through native GDX controls |
| `modules/coronapoker-gdx/src/test/java/com/tonikelope/coronapoker/gdx/GdxMultiprocessScenarioTest.java` | Parent process, topologies, faults and final assertions |
| `modules/coronapoker-gdx/src/test/java/com/tonikelope/coronapoker/gdx/GdxMultiprocessNodeMain.java` | Host and client process behaviour plus semantic markers |
| `modules/coronapoker-gdx/src/test/java/com/tonikelope/coronapoker/gdx/scenarios/GdxScenarioRenderer.java` | Test renderer, native action readiness and observations |
| `tools/qa/run-certification.ps1` | Execution policy and reports, never scenario ownership |

## 1. Define the contract before editing the harness

Write the precondition, trigger and observable result. Answer these questions:

- Which peer owns the action?
- At which hand and street can it occur?
- What event proves that the control is ready?
- Which socket or process fault is introduced?
- Which peers must remain alive?
- What ledger, stack, hand counter or consensus value must match?
- What semantic marker proves that the intended branch executed?
- What result would prove the opposite behaviour?

Elapsed time and process liveness are not evidence of game progress.

## 2. Add the smallest failing regression

Start with the narrowest test that fails for the product defect. Add a complete
gameplay scenario only when the behaviour crosses enough real lifecycle layers
to justify it.

Every complete gameplay method must be registered under exactly one scenario
key. A historical key retains at least one independent-JVM method. Focused
native or network coverage can be additional methods under that same key but
cannot replace its process-isolated historical port.

Run the catalogue contract while developing:

```powershell
mvn -f modules/pom.xml -pl coronapoker-gdx -am test `
  '-Dtest=GdxScenarioContractTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false'
```

## 3. Synchronize on semantic progress

Coordinate parent and node processes using state or named markers such as:

- an exact hand and street
- an active local turn
- a native button becoming enabled
- a reconnect count changing
- a recovery lobby opening
- a player becoming spectator or active
- a committed action appearing in the durable ledger

Use `CP_E2E_*` markers for facts that must cross the process boundary. Markers
must describe an observable state, not merely that a timer elapsed.

Never replace a semantic gate with a fixed sleep. A short polling interval is
acceptable inside a bounded wait, but the predicate must represent progress.
Every wait needs a timeout and diagnostics naming the missing state.

A live JVM or nonzero CPU usage is not progress. Long waits need a semantic
progress signal and an accelerated no-progress watchdog when the scenario can
otherwise remain alive indefinitely.

Before sending an action through GDX, assert the same readiness used by the
real control. Calling a helper immediately after reconnection can otherwise
create a harness-only race that a player could never trigger.

## 4. Keep observations language neutral

Functional text checks must use `GdxFunctionalLabelOracle` or an equivalent
semantic observation. Assert canonical action, pot, blind and result meaning.
Do not assert one translated string or force the user's language.

Visual alignment, font quality and exact pixels remain manual checks. A
functional label oracle proves meaning, not appearance.

## 5. Add process coverage when the boundary matters

Use `GdxMultiprocessScenarioTest` when the behaviour depends on real sockets,
separate client state, process exit or recovery. Reuse the existing launchers
and isolated homes.

The final oracle normally checks that:

- every expected process reached its completion marker
- no fatal or unexpected dialog marker appeared
- canonical ledgers match across surviving peers
- stacks and buy-ins conserve money
- the expected durable hand count was committed
- required recovery, reconnect, Run It Twice, straddle or spectator markers occurred
- no stale child process remains after teardown

Scale timeouts by work and participant count. Do not increase a timeout until
the log proves semantic progress throughout the additional interval.

## 6. Register the method once

Add the method name to one entry in
`GdxScenarioContract.CERTIFICATION_SCENARIOS`. Do not copy it into the runner.

Add a new scenario key only when no existing key describes the same user-level
flow. Keep stable keys when improving coverage, because reports and replay
commands use them as public identifiers.

Add the key to `quickScenarios` in `run-certification.ps1` only when it belongs
in the short critical subset. `fast`, `balanced` and `stress` discover every
registered key automatically.

Change `swing-gold-scenarios.tsv` only to correct the recorded historical
baseline, and explain that correction in the commit.

## 7. Preserve replayable game-path entropy

All factories used by a GDX scenario obtain `GameEntropySource` and session IDs
from the seeded scenario helper. Do not use `CryptoRandom`, `ThreadLocalRandom`,
`Math.random`, `Collections.shuffle` or an unseeded `Random` for decisions that
can change cards, seats, bot actions, labels, animation choice or lifecycle.

Keys, proofs, IVs and transport challenges remain securely random. Replayability
applies to the functional path, not byte-for-byte cryptographic identity.

## 8. Validate from narrow to broad

Run the focused method first:

```powershell
mvn -f modules/pom.xml -pl coronapoker-gdx -am test `
  '-Dtest=GdxMultiprocessScenarioTest#yourMethodName' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  '-Dqa.sim.seed=42'
```

Run the exact scenario key with a known base seed:

```powershell
.\qa.cmd scenarios fast -Scenario your-scenario -Seed 42
```

Run every mapped scenario method once after changing shared harness behaviour:

```powershell
.\qa.cmd scenarios fast
```

Use `balanced` for release-level depth. Use `stress` after race, watchdog,
scheduling, reconnect or recovery changes:

```powershell
.\qa.cmd scenarios balanced
.\qa.cmd scenarios stress
```

On failure, replay the exact base seed before using a new seed. Fix the first
violated product or harness contract. Never weaken an oracle merely to make a
failing product green.

## Completion checklist

- [ ] The invariant and opposite failure are explicit.
- [ ] The narrowest useful regression is red before the fix and green after it.
- [ ] A complete gameplay method is registered under exactly one scenario key.
- [ ] Historical keys retain independent-JVM coverage.
- [ ] Process tests use separate JVMs and isolated homes.
- [ ] Every wait is bounded and synchronized on semantic progress.
- [ ] Long accelerated waits expose progress and detect no-progress hangs.
- [ ] Native GDX readiness is checked before submitting actions.
- [ ] Functional labels are checked by canonical meaning in every language.
- [ ] Ledgers, stacks, buy-ins and durable hand counts are asserted.
- [ ] Game-path entropy uses the seeded scenario source.
- [ ] The exact failing seed passes after the fix.
- [ ] The required focused, `fast`, `balanced` or `stress` gate passes.

Manual visual, audio-device, Internet, NAT and operating-system checks still
complement the automated certificate.
