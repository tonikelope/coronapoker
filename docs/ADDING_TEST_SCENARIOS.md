# Adding GDX test scenarios

This guide explains how to add or extend a CoronaPoker gameplay scenario without
creating a test that passes for the wrong reason. Read
[Testing and certification](TESTING.md) first for the execution order and release
gates.

## Choose the correct layer

| Change | Primary test layer |
|---|---|
| Pure rule, codec, persistence or money calculation | Unit test in `coronapoker-core` |
| GDX command, projection, dialog state or lifecycle binding | Focused test in `coronapoker-gdx` |
| Complete game flow with production core objects | `GdxReconnectScenarioTest` |
| Real sockets, separate client processes, crashes or recovery | `GdxMultiprocessScenarioTest` |
| Large seeded protocol volume | `tools/qa/headless-sim.cmd` campaign |

Use the lowest layer that can prove the invariant, then add multiprocess coverage
when process isolation, sockets or lifecycle are part of the behavior. A mocked
projection test cannot certify a reconnect or a crashed client.

## Scenario architecture

The scenario system has four contracts:

1. `tools/qa/reference/swing-gold-scenarios.tsv` is the immutable list of the 37
   historical gameplay scenarios used as the migration baseline.
2. `GdxScenarioContract.java` maps every historical identifier to strict GDX
   tests and records post-Swing product scenarios separately. Its contract test
   rejects missing, duplicated or invented mappings.
3. `GdxMultiprocessScenarioTest.java` launches the host and client JVMs used by
   release certification. `GdxMultiprocessNodeMain.java` is the node process.
4. `tools/qa/run-certification.ps1` reads the Java mappings and defines only
   execution policy: mode depth, repetitions, seeds, isolation and reports.

The historical manifest is not the product scenario catalogue. New GDX-only
coverage can be added without changing it. Change the manifest only when
correcting the recorded historical baseline, and explain that correction in the
commit.

## Important files

| File | Responsibility |
|---|---|
| `modules/coronapoker-gdx/src/test/java/com/tonikelope/coronapoker/gdx/scenarios/GdxScenarioContract.java` | Historical identifier to strict GDX test mapping |
| `modules/coronapoker-gdx/src/test/java/com/tonikelope/coronapoker/gdx/scenarios/GdxScenarioContractTest.java` | Mapping and coverage invariants |
| `modules/coronapoker-gdx/src/test/java/com/tonikelope/coronapoker/gdx/scenarios/GdxReconnectScenarioTest.java` | Production-core game scenarios observed through GDX |
| `modules/coronapoker-gdx/src/test/java/com/tonikelope/coronapoker/gdx/GdxMultiprocessScenarioTest.java` | Parent process, topology, process faults and final assertions |
| `modules/coronapoker-gdx/src/test/java/com/tonikelope/coronapoker/gdx/GdxMultiprocessNodeMain.java` | Host or client process behavior and semantic markers |
| `modules/coronapoker-gdx/src/test/java/com/tonikelope/coronapoker/gdx/scenarios/GdxScenarioRenderer.java` | Test renderer, native action readiness and observations |
| `tools/qa/run-certification.ps1` | Mode depth, process isolation, seed schedule and reports |
| `docs/TESTING.md` | Public commands, topology matrix and scenario meaning |

## Step 1: define the behavior

Write down the precondition, trigger and observable result before editing the
harness. A useful contract answers all of these questions:

- Which peer owns the action?
- At which hand and street may it occur?
- What event proves the peer is ready?
- Which socket or process fault is applied?
- Which peer must remain alive?
- What ledger, stack, hand counter or consensus value must match at the end?
- What output proves that the intended branch actually executed?

Do not use elapsed time as proof that a game reached a state.

## Step 2: add the smallest failing regression

Add a focused unit or GDX integration test first. For an existing historical
scenario, register the exact test method in `STRICT_HOMOLOGUE_TESTS`. A new
product behaviour that deserves release certification belongs in
`GDX_ONLY_SCENARIOS`. A certification method may belong to one scenario only.
Useful integration coverage that is not a release scenario belongs in
`SUPPORTING_NETWORK_TESTS`.

Run the contract guard:

```powershell
mvn -f modules/pom.xml -pl coronapoker-gdx -am test `
  '-Dtest=GdxScenarioContractTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false'
```

## Step 3: use semantic synchronization

Coordinate the parent and node processes with explicit state or markers. Good
gates include:

- a specific hand and street;
- an active local turn;
- a native button becoming enabled;
- a reconnect count changing;
- a recovery lobby opening;
- a player becoming spectator or active;
- a committed action appearing in the durable ledger.

Never replace such a gate with a fixed sleep. A short polling interval is fine
inside a bounded wait, but the predicate must represent gameplay progress. Every
wait needs a timeout and diagnostics that name the missing state.

If an action is submitted through GDX, first assert the same readiness that the
real control uses. Calling a helper immediately after socket reconnection can
otherwise create a harness race that no user could trigger.

## Step 4: add multiprocess coverage

Add one method to `GdxMultiprocessScenarioTest`. Reuse the existing host/client
launchers and isolated homes. The method must assert the intended transition,
not only process exit code zero.

The end oracle should normally include:

- every expected process reached its completion marker;
- no fatal or unexpected dialog marker was emitted;
- canonical ledgers match across surviving peers;
- stacks and buy-ins conserve money;
- the expected number of durable hands was committed;
- required recovery, reconnect, RIT, straddle or spectator markers occurred;
- no stale process remains after teardown.

Scale timeouts by the actual work. Large tables need a participant-aware budget,
not only a hand-count budget. Do not raise a timeout until the log shows semantic
progress throughout the extra interval.

## Step 5: register the scenario

Register the method once in `GdxScenarioContract.java`:

1. Use `STRICT_HOMOLOGUE_TESTS` only for a historical Swing identifier.
2. Use `GDX_ONLY_SCENARIOS` for behaviour introduced after that baseline.
3. Add it to the certifier's `quickScenarios` only when it belongs in the short
   critical subset.

Do not add a second method list to PowerShell. The certifier discovers the Java
mapping and derives each execution seed from the printed base seed.

## Step 6: validate in increasing scope

Run the focused test first:

```powershell
mvn -f modules/pom.xml -pl coronapoker-gdx -am test `
  '-Dtest=GdxMultiprocessScenarioTest#yourMethodName' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  '-Dqa.sim.seed=42'
```

Run the complete behavioural catalogue once:

```powershell
.\tools\qa\certify.cmd -Mode fast
```

For a race, watchdog, scheduling or recovery change, finish with fresh-seed
stress:

```powershell
.\tools\qa\certify.cmd -Mode stress
```

On failure, rerun the exact printed seed before using a new seed. Fix either the
product or the harness according to the first violated contract. Do not weaken
the oracle to make a failing product pass.

## Completion checklist

- [ ] The invariant and expected failure mode are explicit.
- [ ] The lowest useful regression is red before the fix and green after it.
- [ ] Historical mappings remain one-to-one and complete.
- [ ] Multiprocess scenarios use separate JVMs and isolated homes.
- [ ] Synchronization is semantic and every wait is bounded.
- [ ] Native GDX readiness is checked before submitting an action.
- [ ] Final ledgers, stacks, buy-ins and durable hand counts are asserted.
- [ ] The scenario is registered once in the correct Java mapping.
- [ ] The exact failing seed passes after the fix.
- [ ] The required fast, balanced or stress gate finishes with a PASS banner.

Visual alignment, animation quality, physical audio hardware and real Internet
or NAT behavior remain manual checks. They complement the automated certificate
and do not replace it.
