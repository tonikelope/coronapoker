# Headless protocol simulator

This lane is separate from the bot-quality simulator. Every simulated peer owns
an independent production `HandStateChain`, and the campaign composes the
production betting reducer, deterministic shuffle, canonical action/community
records, Ed25519 signatures, RIT pot division, settlement record and
`HANDVERIFY` receipt parser.

Run from `tools/qa` after installing the current root artifact:

```powershell
& 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/Antonio/.m2/repository' '-Duser.home=C:/some/isolated/home' '-Dqa.sim.hands=2000' '-Dqa.sim.seed=3231711270' test '-Pqa-protocol-sim'
```

The seed and zero-based hand number identify a failing scenario. Re-run one
hand by keeping the seed and temporarily setting the volume to include that
hand; single-hand replay selection is the next harness milestone.

## Current production coverage

- Three independent peers: host, human client and host-authored bot.
- Exact preflop reducer: raise, call/check, folds, full all-in and short all-in.
- Canonical signed action records and stale-`PREV_H` rejection.
- Deterministic 52-card permutation and signed community records.
- Normal board and RIT side-B street domains.
- Exact-cent normal/tied/RIT payouts and settlement conservation.
- Independent `H_final` convergence and signed strict `HANDVERIFY` receipts.
- Per-hand probes proving signed mutation and stale-chain rejection.

## Not yet covered; do not infer it from a green campaign

- Actual `Crupier` orchestration and production bot decision generation.
- SRA cascade/proofs and pocket unlock/POTCARDS lifecycle.
- Side-pot construction and complete multi-street betting state.
- Rabbit authorization/ledger.
- EXIT/testaments, MISDEAL/refund and recovery snapshots/SQLite replay.
- Fault-scheduled transport, reconnect and duplicate delivery.
- Real sockets, executors, Swing/EDT and lobby lifecycle.

Those items are added incrementally by composing or extracting production
components; protocol logic must not be copied into a parallel implementation.
