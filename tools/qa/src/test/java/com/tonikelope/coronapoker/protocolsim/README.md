# Headless protocol simulator

This lane is separate from the bot-quality simulator. Every simulated peer owns
an independent production `HandStateChain`, and the campaign composes the
production betting reducer, deterministic shuffle, canonical action/community
records, Ed25519 signatures, RIT pot division, settlement record and
`HANDVERIFY` receipt parser.

Run from the repository root. The script uses the Maven reactor, so it always
compiles and tests the current checkout rather than a possibly stale installed
JAR:

```powershell
& .\tools\qa\run-headless-sim.ps1 -Hands 5000 -Faults 5000 -BotHands 100 -Seed 3231711270
```

The seed and zero-based hand number identify a failing scenario. Re-run exactly
one hand, with a concise trace, using:

```powershell
& 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd' '-f' '.\tools\reactor\pom.xml' '-Duser.home=C:/some/isolated/home' '-Dqa.sim.seed=3231711270' '-Dqa.sim.hand=48731' '-Dqa.sim.trace=true' test '-Pqa-protocol-sim'
```

Replay one zero-based fault case by replacing `qa.sim.hand` with
`-Dqa.sim.fault.case=48731`. Every assertion reports the seed, case, fault type
and injection position. A production-bot hand can likewise be isolated with
`-Dqa.sim.bot.hand=48731` (and a sufficiently large `qa.sim.bot.hands`).

The umbrella lane for every automated non-visual QA check (including the
protocol campaigns, crypto/SRA and production-bot simulations) is:

```powershell
& .\tools\qa\run-headless-sim.ps1 -Hands 2000 -Faults 2000 -BotHands 100 -AllNonVisual
```

It forces `java.awt.headless=true`; a display/window dependency therefore fails
the lane. `IdentityKeypairAclSmoke` remains separate because its exact Windows
ACL shape is environment-specific, not game behavior. Statistical bot-strength
tests remain in `qa-bots`; the umbrella lane replaces their noisy win-rate
thresholds with hard per-hand conservation, liveness and validity invariants.

## Current production coverage

- Three independent peers: host, human client and host-authored bot.
- Exact preflop reducer with randomized cent values: raise, call/check, folds,
  full all-in and short all-in; deterministic boundary hands include 1, 2, 99
  and 100-cent big blinds.
- Canonical signed action records and stale-`PREV_H` rejection.
- Deterministic 52-card permutation and signed community records.
- Normal board and RIT side-B street domains.
- Exact-cent normal/tied/RIT payouts and settlement conservation.
- Random 2-to-9-seat production `HandPot` layers with zero, one-cent, equal,
  folded, disconnected-all-in and large-stack commitments.
- Signed production Rabbit request/authorization sequences in every mode,
  including exact duplicates, mutation and cross-hand replay rejection.
- Independent `H_final` convergence and signed strict `HANDVERIFY` receipts.
- Per-hand probes proving signed mutation and stale-chain rejection.
- Direct single-hand replay with seed, hand index and concise trace output.
- Production `GameCommandGate` fault probes: exact retransmission, conflicting
  command ID, signed-record mutation and valid-but-reordered action.
- Seeded random fault campaigns over authenticated critical streams: honest
  delivery, exact duplicate, ID collision, signed mutation, reorder,
  disconnect, rate-limit refusal and unknown critical command. Every case
  either converges or closes explicitly without post-fault processing.
- Current-version controlled `EXIT` request/relay with authenticated identity
  and mandatory unlock material pairing.
- Abrupt transport closure as an explicit terminal state and strict canonical
  `MISDEAL` reason parsing.
- Session-bound recovery snapshot validation, exact local-balance
  reconciliation and signed action replay to the uninterrupted `H_t`.
- Cross-session snapshot and post-signature mutation rejection before chain
  mutation.
- Real in-memory SQLite recovery queries over thousands of games: latest-hand
  selection, durable hand ordinal, atomic production row conversion, strict
  current snapshot round-trip and corrupt/missing cryptographic hand-ID
  rejection.
- Seeded lifecycle transitions: normal drain, final exit, force-recover,
  abrupt disconnect/MISDEAL, socket reconnect with pending critical-command
  preservation, RIT side-B interruption and malformed termination. Old-session
  callbacks are rejected before the next table can mutate.
- Dual-lock SRA deck, deterministic shuffle, pocket/community unlock, disjoint
  normal/RIT boards and exact 52-card recovery.
- Atomic `POTCARDS` roster with real showdown signatures and binding to each
  encrypted pocket, including the canonical upper boundary (card index 51).
- Community EXIT testament cannot unlock the exiting player's pocket.

## Not yet covered; do not infer it from a green campaign

- Actual `Crupier` orchestration. Production bot decisions are exercised by a
  scalable 3-to-9-seat campaign, but its game harness is not `Crupier`.
- Full `Crupier` SRA request/response orchestration and proof-chain scheduling.
- Complete multi-street betting plus `Crupier` side-pot/settlement wiring; the
  production side-pot constructor itself is randomized above.
- Full `Crupier` Rabbit request/pause/showdown orchestration; the production
  signed ledger and all fee modes are randomized above.
- Full `Crupier` EXIT/MISDEAL/refund orchestration and action-by-action SQLite
  replay; current snapshot query/row conversion is covered above.
- A single live-`Crupier` campaign that interrupts an active hand, reconnects
  its real sockets and resumes through SQLite. Transport and lifecycle faults
  are currently seeded campaigns over the production gates/state machines,
  but not yet one GUI-free `Crupier.run()` execution.
- Real sockets/executors are covered by focused headless tests, but are not yet
  driven inside the same seeded hand campaign. Swing/EDT and lobby lifecycle
  remain outside the simulator.

Those items are added incrementally by composing or extracting production
components; protocol logic must not be copied into a parallel implementation.
