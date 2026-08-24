<div align="justify">

# Adding tests and real-game scenarios

This guide is the contributor path for adding a CoronaPoker regression. It
explains which test layer to choose and, for a real-game certification scenario,
every registration, orchestration, node-policy, documentation and validation
step required before the scenario is complete.

The broader lane and release policy remains in [Testing and
certification](TESTING.md). Read that manual when choosing how widely to validate
a change; use this guide while implementing the test itself.

## Choose the smallest layer that proves the defect

Start with the cheapest layer that can observe the behavior. A real-game
scenario is appropriate only when the claim depends on production JVM, socket,
Swing/`Crupier` or recovery orchestration.

| What must be proved | Add it here | Normal lane |
|---|---|---|
| Pure rule, parser, money, persistence or production helper behavior | Matching package under `tools/qa/src/test/java` | `qa-fast` |
| A short cross-component invariant suitable for refactor protection | `smoke/*Smoke.java` | `qa-fast` |
| Many deterministic protocol, fault or SQLite cases without full `Crupier` orchestration | `protocolsim` or the related campaign package | `qa-protocol-sim`, also wired into release QA |
| Deterministic framing, wire-format or queue behavior | `net` | `qa-fast` |
| A real-socket stall/back-pressure check that is genuinely slow | `net`, normally `SocketStallIntegrationTest`, and `@Tag("slow")` | `qa-network` |
| Crypto performance, differential or cascade behavior | `crypto` and `@Tag("slow")` | `qa-crypto` |
| Bot playing strength or statistical difficulty separation | `bot/harness` and `@Tag("slow")` | `qa-bots` |
| A complete host/client game, production dialogs, a live socket fault, recovery, or multi-JVM lifecycle | `e2e` scenario described below | `real-game-e2e.cmd` and certification matrix |

Do not create an E2E scenario merely to call a production method. If a focused
JUnit regression can prove the contract deterministically, keep it in
`qa-fast`. Conversely, do not replace a socket, recovery or lifecycle defect
with a model that cannot exercise the production orchestration which failed.

## The red-to-green workflow for ordinary tests

1. Reproduce the defect with one deterministic failing test. Give the test a
   behavioral name and make the assertion describe the broken contract.
2. Put the test beside the nearest existing tests. Search by the production
   class or method first instead of creating a new package by default.
3. Keep it out of `@Tag("slow")` unless it genuinely requires the bot, crypto or
   socket slow harness. Runtime alone is not a reason to hide a deterministic
   regression from the normal lane.
4. Run the single class, then the affected package/classes, then `qa-fast`.
5. If the fix changes protocol, networking, recovery, settlement or shared
   lifecycle behavior, also run the affected real-game scenarios as specified
   in the proportional validation matrix in `TESTING.md`.

Example single-class command from the repository root:

```powershell
mvn -f tools/reactor/pom.xml verify '-Dtest=PotMathTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

The reactor must reach `verify`: stopping at `test` does not package the game JAR
needed by the QA module.

## Real-game scenario architecture

The real-game harness has a parent process and several production nodes. Knowing
the boundary prevents a scenario from accidentally testing only its own harness.

```text
certify.cmd
  -> run-certification.ps1       selects Label/Name/topology/repetitions
     -> run-real-game-e2e.ps1    validates CLI and passes Maven properties
        -> RealGameLoopbackE2EIT launches and orchestrates all JVMs
           -> RealGameNodeMain   mounts production WaitingRoomFrame/Crupier
              -> production sockets, crypto, settlement, SQLite and recovery
```

The parent sends narrow control commands over each node's standard input. Nodes
report observable state as `CP_E2E_*` markers on standard output. The parent must
coordinate from those markers and production log evidence, never from a timing
guess.

Keep these files open while implementing a scenario:

| File | Responsibility |
|---|---|
| [`run-real-game-e2e.ps1`](../tools/qa/run-real-game-e2e.ps1) | Public options, help, examples and fail-fast CLI constraints |
| [`RealGameScenarioContract.java`](../tools/qa/src/test/java/com/tonikelope/coronapoker/e2e/RealGameScenarioContract.java) | Supported names, timing class and narrow terminal-MISDEAL classification |
| [`RealGameLoopbackE2EIT.java`](../tools/qa/src/test/java/com/tonikelope/coronapoker/e2e/RealGameLoopbackE2EIT.java) | Parent launch, gates, fault orchestration and green/red assertions |
| [`RealGameNodeMain.java`](../tools/qa/src/test/java/com/tonikelope/coronapoker/e2e/RealGameNodeMain.java) | Node-side production UI/action/dialog driver and semantic markers |
| [`run-certification.ps1`](../tools/qa/run-certification.ps1) | Certified labels, topology, repetition and `quick` selection |
| [`RealGameScenarioContractTest.java`](../tools/qa/src/test/java/com/tonikelope/coronapoker/e2e/RealGameScenarioContractTest.java) | Fast catalog, matrix and pure harness-contract checks |
| [`TESTING.md`](TESTING.md) | Public matrix, scenario oracle catalog and proportional validation policy |

### Terms used by the scenario catalog

- **Name** is the public `-Scenario` value and selects one behavior, for example
  `reconnect-midhand`.
- **Label** identifies one certification profile. Usually it equals `Name`, but
  one behavior can have several topologies: `normal` is certified as
  `normal-soak`, `normal-heads-up`, `normal-full-mixed` and
  `normal-full-human`.
- **Topology** is `Clients/Bots/Hands`. The host is an additional human seat, so
  `Clients=9, Bots=0` fills the ten-seat table.
- **Green oracle** is the exact evidence that proves success. Process exit code,
  a live JVM, CPU activity or the absence of an exception is not an oracle.
- **Post-fault liveness** means a fresh hand completes after the transition. Add
  that hand only when the scenario claims continued play.

### Choose exactly one timing contract

Every name belongs to one and only one set in `RealGameScenarioContract`:

| Contract | Use it when | Required coordination |
|---|---|---|
| `AUTONOMOUS` | The action driver and existing dialog policy can complete the scenario without a precisely timed external fault | Assert the final production outcome |
| `ACTION_GATED` | A fault or control action must occur when a real player action is available at an exact hand/street | Arm before `START_GAME`, await `CP_E2E_ACTION_GATE_REACHED`, perform the action once, then release |
| `DIALOG_ORDERED` | The causal point is a real production dialog whose decision must be delayed until the parent acts | Capture the dialog, emit a reached marker, perform the external action and explicitly release the dialog decision |

`MISDEAL_TERMINAL` is separate from the timing contract. Add a name there only
when a successful production MISDEAL intentionally dismantles the current
table. It relaxes specific common terminal handling; it must never become a
general error allowance.

## Design the scenario before editing code

Write this small contract first. If any cell is vague, the scenario is not ready
to implement.

| Decision | Required answer |
|---|---|
| Public name | Stable lower-case hyphenated `-Scenario` value |
| Defect or risk | One production behavior that existing tests cannot prove |
| Closest scenario | Existing scenario whose orchestration and node policy are most similar |
| Topology | Minimum clients, bots and hands, including the host seat |
| Causal point | Exact hand, street, dialog or production event where the action occurs |
| Parent action | Socket drop, process death, pause, recovery, controlled exit, or other single operation |
| Green oracle | Exact markers, consensus, balances, ledger, refund or recovery state |
| Red oracle | Premature exit, missing marker, `CP_E2E_FAIL`, divergence, invalid ledger or forbidden dialog/log evidence |
| Liveness | Whether a newly dealt following hand is required, and why |
| Timing class | `AUTONOMOUS`, `ACTION_GATED` or `DIALOG_ORDERED` |
| Certification | Profile label and whether it is critical enough for `quick` |

Use the smallest topology that contains an independent witness for the claim.
For example, a peer-loss scenario may need another surviving human because a
host plus bot cannot prove the same distributed receipt path. Hand counts are
contract evidence, not padding: one interrupted hand plus one fresh hand is two
hands; a scenario should not gain extra hands just to make a race less visible.

To inspect every current touchpoint for a similar scenario on Windows:

```powershell
Get-ChildItem tools/qa -Recurse -File | Select-String -SimpleMatch 'reconnect-midhand'
```

Repeat that search with the chosen nearest scenario and review every result
before writing the new name.

## Implement a complete real-game scenario

The following order keeps incomplete registration failures early and makes the
first runnable version diagnosable.

### 1. Register and validate the public CLI

Edit `tools/qa/run-real-game-e2e.ps1` in all four places:

1. Add `my-scenario` to the `-Scenario` `ValidateSet`.
2. Add every exact `Clients`, `Bots` and `Hands` constraint to the `Constraints`
   section printed by `-Help`.
3. Enforce the same constraints before Maven starts, with an error that states
   the accepted topology.
4. Add one complete, directly copyable example under `Examples`.

Do not rely only on `[ValidateRange]`: it proves the global range, not the
scenario-specific topology.

### 2. Add the Java-side catalog and mirrored input validation

In `RealGameScenarioContract`, add the name to exactly one timing set. Add it to
`MISDEAL_TERMINAL` only under the narrow definition above.

Then mirror the CLI topology constraint at the start of
`RealGameLoopbackE2EIT.completesConfiguredLocalGameWithRealCrupiersAndSockets`.
This duplication is intentional: direct Maven invocation can bypass the public
PowerShell runner, so both boundaries must reject invalid input.

The catalog contract test checks that the Java set, PowerShell `ValidateSet`,
certification names and public tables agree. It does **not** currently prove
that the PowerShell and Java topology predicates are equivalent; review both
manually and add a focused unit test when the predicate has non-trivial logic.

### 3. Account for launch topology and identities

The parent normally launches the host plus all configured clients before the
game. Recovery scenarios can start with fewer clients and add or replace nodes
later. If the new scenario changes membership, update the initial-client
calculation and give every late node the correct isolated home, nick, identity,
SQLite history and completed-hand target.

Never assume that `nodes.get(1)` is the desired role without documenting which
nick it represents after additions, removals or relaunches. Use an unaffected
human witness when the oracle depends on distributed agreement.

### 4. Establish causal coordination before game start

For `ACTION_GATED`, add a concrete plan to
`RealGameLoopbackE2EIT.armScenarioActionGates`. Gates must be armed before the
parent sends `START_GAME`; otherwise the automatic action driver can consume the
turn first.

The normal pattern is:

```java
armActionGate(victim, 1, Crupier.PREFLOP);       // before START_GAME
awaitActionGate(victim, 1, Crupier.PREFLOP);     // exact semantic point
dropAndAwaitReconnect(victim, host, "client1", 1); // fault once + prove reconnect
releaseActionGate(victim, 1, Crupier.PREFLOP);   // let production continue
```

Use the existing `armActionGate`, `awaitActionGate` and `releaseActionGate`
helpers. Do not replace them with `Thread.sleep`. A short polling sleep inside a
bounded generic waiter is an implementation detail; a sleep used to guess that
the table reached preflop/flop/recovery is invalid scenario coordination.

For `DIALOG_ORDERED`, follow the nearest RIT or straddle scenario: the node must
observe the actual dialog on the EDT, retain it, emit a reached marker and act
only after the parent has established the intended ordering.

### 5. Add the parent orchestration and exact oracles

In `RealGameLoopbackE2EIT`:

1. Add the top-level dispatch for the new name.
2. Put the behavior in a clearly named `run...Scenario` helper rather than
   extending the main test body with a long inline branch.
3. Await the causal marker and perform the fault/action exactly once.
4. Await positive completion evidence and assert all forbidden evidence.
5. Include `node.diagnostic()` in timeout and assertion failures.
6. Reuse common consensus, balance and ledger assertions where their contract
   matches; add a narrow assertion helper when it does not.

Settled hands normally require identical consensus hashes and canonical
balances on every surviving peer plus exact ledger conservation. Aborted hands
normally require the expected MISDEAL reason, zero pot, exact refund/recovery
state and a live recovery path. If continued liveness is claimed, require a
fresh hand rather than merely checking that the process remains alive.

### 6. Add only necessary node-side policy

`RealGameNodeMain` must remain a driver around production behavior, not a second
game implementation. Review the following extension points and change only
those the contract needs:

- `configureRuntime` for scenario-specific production settings;
- `driveLocalActions` for action choice while real buttons are enabled;
- `startControlThread` for a new narrow parent command;
- `applyScenarioWindowAction` for real dialog ordering;
- category helpers such as `isForceRecoverScenario`, `isAllInScenario`,
  `isRitScenario`, `requiresLiveStreetAfterPeerLoss` and
  `expectsPermanentLocalExit`;
- spectator/rebuy helper policies when roster state is part of the scenario.

Prefer a small predicate or command helper that can be unit-tested. Emit a
`CP_E2E_*` marker after the production action or observable state change, not
before it. Any node exception must surface as `CP_E2E_FAIL` and remain terminal.

### 7. Add the certification profile

Add one profile to `scenarioProfiles` in `tools/qa/run-certification.ps1`:

```powershell
@{ Label = 'my-scenario'; Name = 'my-scenario'; Clients = 2; Bots = 1; Hands = 2 }
```

Use a distinct `Label` only when the same `Name` needs multiple certified
topologies. `fast`, `balanced` and `stress` consume the full profile list. Add
the label to `quickLabels` only if it is a critical short preflight scenario;
do not use `quick` inclusion to compensate for missing focused tests.

### 8. Update the executable reference and public tables

Keep all public surfaces synchronized:

- `real-game-e2e.cmd -Help`: constraint, scenario description and copyable
  example (the text is implemented in `run-real-game-e2e.ps1`);
- the certification matrix in `TESTING.md`: exact topology for every mode and a
  one-sentence rationale for the minimum hand count;
- `Scenario contracts` in `TESTING.md`: what is exercised and the exact green
  result;
- typical commands in `TESTING.md` only when the scenario illustrates a new
  family not already represented there.

Do not duplicate the full scenario catalog in this contributor guide. The
contract test intentionally parses the two tables in `TESTING.md` as the public
source of truth.

### 9. Add focused contract tests

Extend `RealGameScenarioContractTest` or another small E2E contract test for all
new pure logic: gate selection, role targeting, terminal classification,
topology predicates, parser behavior, recovery targets and watchdog decisions.

Run the catalog/document contract first:

```powershell
mvn -f tools/reactor/pom.xml verify '-Dtest=RealGameScenarioContractTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

This catches catalog, profile and table drift quickly. Its current static checks
cover:

- one non-overlapping Java timing contract per scenario name;
- exact equality between the Java catalog and PowerShell `ValidateSet`;
- coverage of every scenario name by certification profiles;
- unique profile labels and exact documented matrix topologies for every mode;
- exact scenario membership in the public `Scenario contracts` table.

It does not validate help descriptions/examples, equivalence of CLI and Java
topology predicates, parent dispatch, gate role/hand/street selection, node-side
policy or the semantic strength of the final oracle. Those require the focused
unit tests and live scenario runs above.

### 10. Run the new scenario and broader gates

First exercise the public launcher with two explicit seeds:

```powershell
.\tools\qa\real-game-e2e.cmd -Scenario my-scenario -Clients 2 -Bots 1 -Hands 2 -Seed 101
.\tools\qa\real-game-e2e.cmd -Scenario my-scenario -Clients 2 -Bots 1 -Hands 2 -Seed 102
```

Replace the topology with the contract designed above. One seed proves replay;
the second checks that the scenario does not accidentally depend on a specific
deal or schedule.

Then run:

```powershell
.\tools\qa\certify.cmd -Mode quick   # only when the new label is in quickLabels
.\tools\qa\certify.cmd -Mode fast    # required full-matrix integration check
```

Use `balanced` for a normal release. Reserve `stress` for a major baseline,
broad protocol/security change or suspected race family. Statistical bot
quality remains separate unless bot AI/evaluation changed.

## Worked pattern: an action-gated transition

`pause-resume` is the smallest existing reference for the common sequence:

1. CLI and Java require enough hands to prove the paused hand and a following
   hand.
2. `RealGameScenarioContract.ACTION_GATED` classifies it.
3. `armScenarioActionGates` arms the host at hand 1/preflop before `START_GAME`.
4. `runPauseResumeScenario` awaits the gate, sends `PAUSE_TOGGLE`, and requires
   every node to report `CP_E2E_PAUSE_STATE paused=true`.
5. It toggles again, requires `paused=false` from every node, and releases the
   gate.
6. Common final assertions require the configured hands, consensus, balances
   and ledger conservation.

When adding a different transition, copy this *shape*, not its assertions. The
new green oracle must prove the new production claim.

## Definition of done

A real-game scenario is complete only when every box is true:

- [ ] The contract states the smallest topology, causal point, green/red oracle
  and whether a fresh liveness hand is required.
- [ ] The PowerShell `ValidateSet`, help constraint, executable validation,
  scenario description and example agree.
- [ ] The Java catalog contains the name in exactly one timing class.
- [ ] Direct Maven input validation mirrors the public CLI constraint.
- [ ] Parent launch membership and node identities are correct.
- [ ] Every action gate is armed before `START_GAME`.
- [ ] Parent orchestration uses semantic markers and performs the fault once.
- [ ] Node policy still drives production UI, sockets, `Crupier`, crypto,
  settlement and SQLite instead of copying game logic.
- [ ] Premature termination, `CP_E2E_FAIL`, forbidden dialogs and no semantic
  progress fail with diagnostics.
- [ ] `scenarioProfiles`, optional `quickLabels`, the certification matrix and
  `Scenario contracts` agree.
- [ ] Focused contract tests pass.
- [ ] The public launcher passes with the replay seed and at least one different
  seed.
- [ ] The proportional lane from `TESTING.md` passes; `fast` covers the final
  full matrix before integration.

</div>
