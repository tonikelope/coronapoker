# Headless protocol simulator

This lane is separate from the bot-quality simulator. Every simulated peer owns
an independent production `HandStateChain`, and the campaign composes the
production betting reducer, deterministic shuffle, canonical action/community
records, Ed25519 signatures, RIT pot division, settlement record and
`HANDVERIFY` receipt parser.

Run from `tools/qa` after installing the current root artifact:

```powershell
& 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/Antonio/.m2/repository' '-Duser.home=C:/some/isolated/home' '-Dqa.sim.hands=2000' '-Dqa.sim.faults=2000' '-Dqa.sim.seed=3231711270' test '-Pqa-protocol-sim'
```

The seed and zero-based hand number identify a failing scenario. Re-run exactly
one hand, with a concise trace, using:

```powershell
& 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/Antonio/.m2/repository' '-Duser.home=C:/some/isolated/home' '-Dqa.sim.seed=3231711270' '-Dqa.sim.hand=48731' '-Dqa.sim.trace=true' test '-Pqa-protocol-sim'
```

Replay one zero-based fault case by replacing `qa.sim.hand` with
`-Dqa.sim.fault.case=48731`. Every assertion reports the seed, case, fault type
and injection position.

## Current production coverage

- Three independent peers: host, human client and host-authored bot.
- Exact preflop reducer: raise, call/check, folds, full all-in and short all-in.
- Canonical signed action records and stale-`PREV_H` rejection.
- Deterministic 52-card permutation and signed community records.
- Normal board and RIT side-B street domains.
- Exact-cent normal/tied/RIT payouts and settlement conservation.
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
- Dual-lock SRA deck, deterministic shuffle, pocket/community unlock, disjoint
  normal/RIT boards and exact 52-card recovery.
- Atomic `POTCARDS` roster with real showdown signatures and binding to each
  encrypted pocket, including the canonical upper boundary (card index 51).
- Community EXIT testament cannot unlock the exiting player's pocket.

## Not yet covered; do not infer it from a green campaign

- Actual `Crupier` orchestration and production bot decision generation.
- Full `Crupier` SRA request/response orchestration and proof-chain scheduling.
- Side-pot construction and complete multi-street betting state.
- Rabbit authorization/ledger.
- Full `Crupier` EXIT/MISDEAL/refund orchestration and SQLite recovery replay.
- Campaign-integrated fault scheduling, disconnect and reconnect. The current
  focused transport probes already cover duplicate/conflicting delivery,
  mutation and reordering at the production replay/chain gates.
- Real sockets, executors, Swing/EDT and lobby lifecycle.

Those items are added incrementally by composing or extracting production
components; protocol logic must not be copied into a parallel implementation.
