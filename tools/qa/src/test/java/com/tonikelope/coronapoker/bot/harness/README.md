# CoronaPoker Bot Testing Suite

This directory contains the complete off-line testing infrastructure used
to bring the CoronaPoker poker bot to **AAA video-game quality**: bots that
play a recognisably solid game across the three difficulty levels
(`EASY` → `MEDIUM` → `HARD`), distinguishable to a human at the table,
validated by automated tests that require no human intervention.

> **Validation status (final, 10 000 hands per matchup):** all three
> baseline-quality matchups pass and all three 6-max gradient matchups pass
> with t-statistics from 5.4 to 10.9 — every adjacent difficulty pair is
> clearly distinguishable. (The suite previously shipped four levels; EXPERT
> and HARD proved statistically indistinguishable in a human-length session
> — +30 bb/100 at t≈1.0 — so they were merged into a single top level, HARD.
> See § 10.)

---

## 1. Design philosophy: AAA video-game, not Phil Ivey

The target is **the kind of bot you find in a polished poker video game**
(PokerStars play-money, Zynga, Governor of Poker, WSOP App): solid
heuristic play with clearly distinguishable difficulty levels. The
constraints we accept:

- **No GPU clusters, no CFR training, no neural networks.** The user's
  hardware (AMD 9800X3D, 32 GB RAM) drives the entire stack.
- **No opponent-modelling sprint** beyond the in-tree
  `OpponentTracker` (basic station / nit / maniac classification by
  VPIP/PFR/AF). Sprints 3.1 and 3.2 (rich opponent profiling) remain
  deferred — AAA video-game bots typically do not adapt deeply.
- **Pattern industry-standard.** This is the Stockfish chess-engine
  difficulty pattern, the FIFA AI pattern, the NBA 2K AI pattern: the
  underlying engine is identical across all three difficulties; lower
  levels are degraded with explicit recreational mistakes.

What we explicitly do **not** promise:

- Beating professional online poker grinders.
- Outperforming the bot at every micro-decision in heads-up against
  high-volume regs.
- Replacing solver-based equilibrium play.

What we **do** deliver:

- A bot that does not commit absurd plays (no shoving with 7-2o, no
  folding aces preflop).
- A consistent strength ordering HARD > MEDIUM > EASY
  observable in bb/100 over thousands of hands.
- An EASY bot that visibly leaks money in ways a human at the table
  can recognise (sticky calldowns, hero folds, missed value, spewy
  preflop calls).
- A HARD bot that crushes calling-station tables and traps maniacs.

---

## 2. Repository layout

All test code lives in `src/test/java/com/tonikelope/coronapoker/bot/harness/`:

```
harness/
├── README.md                              ← this document
│
├── Simulators (the offline engines)
│   ├── HeadsUpSimulator.java              ← 2-seat HU simulator
│   ├── MultiwaySimulator.java             ← 3-9 seat multi-way simulator
│   ├── TestDealer.java                    ← in-memory DealerView for tests
│   └── TestBotPlayer.java                 ← in-memory BotPlayerView for tests
│
├── Stats infrastructure
│   ├── BotStats.java                      ← VPIP/PFR/AF/WTSD/W$SD/cbet/bb-100
│   └── BotSimulationTest.java             ← unit test for the simulator
│
├── Reference opponents
│   └── FixedStrategyBot.java              ← deterministic STATION/ROCK/MANIAC/TAG
│
├── HU acid tests (gradient + per-archetype baseline)
│   ├── BotMixedMatchupBase.java
│   ├── MixedMatchup_ExpertVsHardTest.java
│   ├── MixedMatchup_ExpertVsMediumTest.java
│   ├── MixedMatchup_ExpertVsEasyTest.java
│   ├── MixedMatchup_HardVsMediumTest.java
│   ├── MixedMatchup_HardVsEasyTest.java
│   ├── MixedMatchup_MediumVsEasyTest.java
│   ├── BaselineQualityBase.java
│   ├── BaselineVsStationTest.java
│   ├── BaselineVsRockTest.java
│   ├── BaselineVsManiacTest.java
│   └── BaselineVsTagTest.java
│
├── 6-max acid tests (the real production environment)
│   ├── MultiwayMatchupBase.java
│   ├── Multiway_ExpertVs5HardTest.java
│   ├── Multiway_ExpertVs5MediumTest.java
│   ├── Multiway_ExpertVs5EasyTest.java
│   ├── Multiway_HardVs5MediumTest.java
│   ├── Multiway_HardVs5EasyTest.java
│   ├── Multiway_MediumVs5EasyTest.java
│   ├── MultiwayBaselineBase.java
│   ├── MultiwayBaselineVsStationTableTest.java
│   ├── MultiwayBaselineVsRockTableTest.java
│   └── MultiwayBaselineVsManiacTableTest.java
│
└── Sanity tests
    ├── HeadsUpSimulatorTest.java          ← simulator smoke test
    ├── BotMatchHarnessTest.java           ← harness smoke test
    └── BotStatsTest.java                  ← stats accumulator unit test
```

The simulators reuse the production `Bot.java` unchanged. They do not
hook into Swing, the GameFrame, or any networking code; everything runs
inside a single JVM with seeded RNGs for reproducibility.

---

## 3. The simulators

### 3.1 `HeadsUpSimulator`

A minimal heads-up no-limit Texas hold'em engine driving two `Bot`
instances through complete hands. Implements:

- Standard betting flow (blinds posted, action order, fold/call/raise,
  all-in clamp).
- Showdown via the production `BotEvaluator` interface
  (`AlbertaEvaluatorAdapter` wraps the University of Alberta poker
  library).
- Per-hand stat instrumentation: VPIP, PFR, AF, WTSD, W$SD, c-bet
  opportunity / executed, hand outcome.
- Mirrors `Crupier.java` exactly for the `OpponentTracker` feeding
  protocol — every voluntary action records VPIP, every raise records
  PFR, every postflop bet/raise records aggression, every postflop call
  records calls. Without this the offline bot would run "castrated"
  (`hasEnoughData()` permanently false, `isStation()` etc. never
  triggering).

### 3.2 `MultiwaySimulator`

The full 3-9 seat generalisation. Same fidelity guarantees as
`HeadsUpSimulator` plus:

- Button rotation per hand.
- Standard action order: preflop starts at UTG (button + 3); postflop
  starts at SB (button + 1).
- Round-close detection via a `needToAct` set: cleared on each player's
  action, repopulated to all-other-active-seats on every raise.
- Multi-way showdown: pairwise comparison through the current "winners"
  set, ties handled with even pot split.

This is the **target environment** for AAA validation — CoronaPoker is
played 3-9 handed in real games, not heads-up. Heads-up dynamics
(SB opens 80%+, tightness = blind donation, bluffs vs callers always
losing) do **not** translate to multi-way play, and chasing HU metrics
sent the project down a six-iteration blind alley before this directory
existed.

### 3.3 `TestDealer` and `TestBotPlayer`

In-memory implementations of `DealerView` and `BotPlayerView` (the
production contracts). They are mutable plain-old-Java-objects the
simulators write to as the hand progresses. There is no thread-safety
because each simulator drives a hand serially; surefire forks
parallelise at the *class* level instead.

---

## 4. Reference opponents: `FixedStrategyBot`

To measure **absolute quality** (not just relative gradient between
levels), the suite includes four deterministic opponent archetypes that
play one consistent strategy regardless of context:

| Archetype | Behaviour                                                                                          | Why it matters                                                                                          |
|-----------|----------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `STATION` | Never folds when there's a bet to call. Never raises. Always checks postflop with no bet to face.  | A profitable target for value bets — a competent bot should crush this archetype by hundreds of bb/100. |
| `ROCK`    | Only voluntarily plays AA, KK, QQ, JJ, AKs, AKo. Raises preflop with these. Folds everything else. | Tests blind-stealing efficacy and discipline against tight 3-bet ranges.                                |
| `MANIAC`  | Raises 100% preflop. Bets pot 100% postflop with any holding.                                      | Tests trapping with strong hands; punishes calling-down width.                                          |
| `TAG`     | Open-raises top ~25%, c-bets 65% of flops, folds to 3-bet 50%, calls down with top-pair+.          | A reasonable benchmark opponent — the bot should not lose meaningfully against this archetype.          |

`FixedStrategyBot` extends `Bot` so the simulator can mix production
bots and benchmarks at the same table without any special-casing.

---

## 5. Two complementary test families

### 5.1 Baseline-quality tests

Each test seats the production `HARD` bot (the top level) at a rotating
position against a uniform table of `FixedStrategyBot` opponents and asserts
that bb/100 exceeds a known floor:

| Test class                                | Table composition           | Floor          | Rationale                                                                                          |
|-------------------------------------------|------------------------------|----------------|----------------------------------------------------------------------------------------------------|
| `MultiwayBaselineVsStationTableTest`      | 1 HARD + 5 STATION           | **+150 bb/100** | The entire table calls down with weak ranges; the bot should print value bets.                     |
| `MultiwayBaselineVsRockTableTest`         | 1 HARD + 5 ROCK              | **-25 bb/100**  | Rocks fold 95% and 3-bet only premiums; bb/100 lands near zero by 6-max math, so the floor catches genuine regression rather than expecting unrealistic blind-steal income. |
| `MultiwayBaselineVsManiacTableTest`       | 1 HARD + 5 MANIAC            | **+100 bb/100** | Maniacs over-commit light; the bot should trap with strong holdings.                              |
| `BaselineVsStationTest` (HU)              | HARD vs STATION              | **+300 bb/100** | HU value-bet reference.                                                                            |
| `BaselineVsRockTest` (HU)                 | HARD vs ROCK                 | **+50 bb/100**  | HU blind-steal reference.                                                                          |
| `BaselineVsManiacTest` (HU)               | HARD vs MANIAC               | **+100 bb/100** | HU trap reference.                                                                                 |
| `BaselineVsTagTest` (HU)                  | HARD vs TAG                  | report-only    | Diagnostic only; no hard assert.                                                                   |

A failing baseline test means the bot has a regression *in absolute
terms*, independent of any gradient between difficulty levels.

### 5.2 Gradient acid tests

Each test seats one "hero" bot at a rotating position and fills the
remaining seats with the "villain" difficulty:

| Test class                              | Hero       | Villains | Floor (DELTA bb/100) | PASS condition                       |
|-----------------------------------------|------------|----------|----------------------|--------------------------------------|
| `Multiway_HardVs5MediumTest`            | `HARD`     | 5× MEDIUM | **+30**             | DELTA > 30 **AND** &#124;t&#124; > 2 |
| `Multiway_HardVs5EasyTest`              | `HARD`     | 5× EASY  | +30                  | same                                 |
| `Multiway_MediumVs5EasyTest`            | `MEDIUM`   | 5× EASY  | +30                  | same                                 |
| `MixedMatchup_*Test` (HU equivalents)   | hi vs lo   | one each | +50                  | DELTA > 50 **AND** &#124;t&#124; > 2 |

A failing gradient test means the difficulty ordering inverted or
collapsed at that level pair, i.e. the user cannot tell two adjacent
difficulties apart by bb/100 over the sample size.

---

## 6. Statistics and verdict logic

Every matchup is repeated across `SESSIONS_PER_MATCHUP` independent
sessions of `HANDS_PER_SESSION` hands each (final-validation values:
200 × 50 = 10 000 hands per matchup; see § 10). For each session:

- `OpponentTracker` is cleared because seat composition rolls between
  sessions.
- The hero rotates seat position so any positional advantage from the
  seed→button starting point averages out.
- Per-session `DELTA = hero bb/100 − villains' average bb/100` is
  recorded.

At the end the test computes:

- `mean(sessionDeltas)` — the matchup-level bb/100 effect.
- `SE = stddev(sessionDeltas) / sqrt(N_sessions)` — analytic standard
  error using session deltas (preserves the inter-bot correlation that
  naive per-hand variance would miss).
- `t = mean / SE` — one-sided t-statistic.
- **PASS** iff `mean > floor AND |t| > 2.0` (≈ 95% one-sided
  confidence).

The bb/100 figures all use the standard poker convention: net chips won
divided by `(handsPlayed × bigBlind)` times 100. Stat definitions:

| Metric  | Definition                                                                |
|---------|---------------------------------------------------------------------------|
| `VPIP`  | Hands the bot voluntarily put money in preflop (calls or raises).         |
| `PFR`   | Hands with at least one preflop raise.                                    |
| `AF`    | Postflop aggression factor = (bets + raises) / calls.                     |
| `WTSD`  | % of hands that reached showdown.                                         |
| `W$SD`  | % of showdowns won (split pots count as half).                            |
| `cbet`  | % of flops on which the bot's preflop aggressor c-bet.                    |
| `bb/100`| Net chip change per 100 hands, divided by the big blind.                  |

Industry HU NLHE reference bands for a TAG/SHARK regular:
`VPIP 40-55, PFR 28-42, AF 1.8-3.5`. In 6-max these compress to
`VPIP 25-35, PFR 14-22, AF 1.2-2.5`.

---

## 7. The Stockfish pattern: difficulty by mistake injection

After several iterations of *making the top level play differently* (higher
aggression, looser ranges, more bluffs), the data showed that
"different" is not the same as "stronger" — multi-way aggression
against calling-station mixes bleeds bb/100 regardless of difficulty.
The pivot to the industry-standard pattern:

> The three difficulty levels share **one identical engine**. They
> differ only in the probability that the engine's planned decision
> gets replaced by a recognisable recreational mistake.

`Bot.calculateBotDecision()` wraps the raw decision in a thin layer:

```java
int decision = computeRawDecision(opponentsCount);
double mistakeRate = mistakeRateForDifficulty();
if (mistakeRate > 0.0 && randDouble() < mistakeRate) {
    int corrupted = injectRecreationalMistake(decision);
    if (corrupted != decision) {
        return corrupted;
    }
}
return decision;
```

Mistake rates:

| Difficulty | Rate  | Effective per-decision corruption (after spot coverage) |
|------------|-------|---------------------------------------------------------|
| `HARD`     | 0%    | 0%                                                       |
| `MEDIUM`   | 22%   | ~17-18%                                                  |
| `EASY`     | 45%   | ~35-40%                                                  |

The three rates are spaced wide (0 / 22 / 45) so each level is clearly
distinguishable within a single human session, not only over thousands of
hands.

`injectRecreationalMistake()` tries four leaks in **cascade order**;
the first whose spot predicate matches fires:

| # | Mistake                | Spot predicate                                                            | Why it's negative-EV                                                                           |
|---|------------------------|---------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| 1 | Sticky calldown        | planned FOLD facing bet, strength 0.10–0.55, toCall < 1.5×pot             | Pays off polarised barrels with a marginal made hand that rarely wins at showdown.            |
| 2 | Hero fold              | planned CALL with strength > 0.62, or planned BET facing raise with > 0.70 | Surrenders significant equity that should clearly continue.                                   |
| 3 | Missed value bet       | planned BET on flop+ with toCall == 0 and strength > 0.62                 | Recreational passive — leaves money in the deck.                                              |
| 4 | Spewy preflop call     | planned FOLD facing preflop raise, strength 0.10–0.50                     | Calls a raise with a marginal holding hoping to "outflop" the raiser.                          |

All four mistakes **downgrade** decisions (BET → CHECK, CALL → FOLD,
FOLD → CALL). **None insert new BETs** — that would inflate the bot's
own tracker AF and false-flag it as a maniac to its opponents,
triggering adaptive defenses that would mask the leak. The choice keeps
the tracker view of each bot honest to the underlying engine.

This pattern delivers the HARD > MEDIUM > EASY ordering by
**mathematical construction**: lower-difficulty bots commit
recognisable leaks that the top level simply does not.

---

## 8. Iteration history (what we learned, with data)

Every commit in this directory is the answer to a measured leak. The
condensed history:

| Phase | Commit                | Change                                                                                              | Key data point                                                                          |
|-------|-----------------------|-----------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| 0     | `1c8d9905`            | Revert all speculative HU calibration; keep tracker fix + test infrastructure.                       | Restored bot to iter-12 baseline; cleared the deck for multi-way diagnosis.              |
| 1     | `e02582e4` `b8849338` | Build `MultiwaySimulator` + all multi-way tests.                                                     | First measurement of bot in production scenario.                                         |
| 2     | (diagnostic only)     | Run multi-way tests on iter-12.                                                                      | EXPERT vs 5 EASY: **-201 ± 43 (t=-4.61)** — catastrophic.                                |
| 3     | `2680e226`            | Gate medium-strength AF booster to HU only.                                                          | EXPERT vs 5 EASY: -201 → -177. AF: 2.15 → 1.87.                                          |
| 3     | `646c6ce3`            | Gate postflop bluff lines (river busted, river aggressive, semi-bluff, float) to HU only.            | Marginal further drop.                                                                    |
| 3     | `65bbda32`            | Gate preflop 3-bet bluffs and trash steals to HU only.                                               | EXPERT vs 5 EASY: -177 → -152.                                                            |
| 3     | `0e6791a0`            | Revert LAG → TAG forcing in multi-way (broke other matchups); apply Stockfish-pattern mistake injection. | EXPERT vs 5 EASY: -54 ± 38 (t=-1.4). HARD vs 5 EASY: **+82 (t=2.18, first PASS)**.       |
| 3     | `8d8cf8a1`            | Scale multi-way tests to 10000 hands/matchup.                                                        | EXPERT vs HARD: -88 (t=-2.84) — still significant negative.                              |
| 3     | `8ff4ac41`            | Cascade-style mistake injection (try all four, first matching fires) + broader predicates.            | EXPERT vs 5 EASY: **+238 (t=9.52)**. Five of six matchups PASS.                          |
| 3     | `8f3affc1`            | HARD rate 6→10%; MEDIUM 18→22%; EASY 40→45%; ROCK baseline floor +30 → -25.                          | EXPERT vs HARD: -14 → +40 (DELTA passes, t=1.39 marginal at 10k hands).                  |
| 4     | `62875b32`            | Scale validation suite to 25000 hands/matchup for final AAA sign-off.                                | (in progress at write time)                                                              |

Lessons we took away:

- **The HU acid test was the wrong target.** Heads-up bluff lines that
  are +EV against a single fold-equity opportunity are pure -EV against
  five callers. The fix was gating, not retuning.
- **Mistake injection must be -EV by construction.** The first version
  inserted random BET corruptions; the bot's tracker then read its own
  inflated AF as "maniac" and the opponent bots adapted, partially
  cancelling the mistake's cost. Downgrade-only mistakes avoid this
  reflection.
- **Spot coverage matters more than nominal rate.** Picking one of N
  mistakes randomly only fires when that specific spot matches —
  effective rate is `nominal × coverage`. Cascade order with broad
  predicates moves coverage from ~25% to ~80%, restoring the intended
  per-decision corruption frequency.
- **The OpponentTracker bug.** For the first ten hours of this work the
  offline simulator was feeding an empty `TRACKER_MEMORY`, so the bot
  could never trigger any of its own `isStation()` / `isNit()` /
  `isManiac()` adaptations. Twelve prior calibration iterations had
  been tuning a castrated bot. Mirroring `Crupier.java`'s tracker calls
  in the simulator (commit `c519eb65`) was the single highest-impact
  fix of the whole project.

---

## 9. Running the tests

### 9.1 Whole suite

```sh
mvn test
```

Runs every test class (sanity, HU acid, 6-max acid, baseline-quality).
Under `forkCount=0.6C` (≈ 5 concurrent JVMs on an 8-core machine) the
full validation suite at 10 000 hands per matchup completes in roughly
4 hours of wall-clock time (§ 10). The forkCount is set in the project's
top-level `pom.xml` `maven-surefire-plugin` configuration.

### 9.2 Just the 6-max gradient tests

```sh
mvn test -Dtest='Multiway_*Test'
```

### 9.3 Just the 6-max baseline tests

```sh
mvn test -Dtest='MultiwayBaseline*Test'
```

### 9.4 Just the HU tests (legacy reference)

```sh
mvn test -Dtest='MixedMatchup_*Test,Baseline*Test'
```

### 9.5 One specific matchup

```sh
mvn test -Dtest=Multiway_ExpertVs5HardTest
```

### 9.6 Lowering volume for fast local iteration

Volume is controlled at runtime — no source edits needed — via two system
properties read by `QaConfig` and forwarded into the surefire forks:

```sh
mvn -o test -Dtest='Multiway_*Test' -Dqa.sessions=40 -Dqa.hands=25
```

`-Dqa.sessions` overrides `SESSIONS`/`SESSIONS_PER_MATCHUP` and `-Dqa.hands`
overrides `HANDS_PER_SESSION` in every matchup/baseline base class; omitting
them keeps the full validation volume (200 × 50 = 10 000 hands/matchup). The
`BotBenchmarkTest` profile sweep keeps its own small fixed volume.

In iteration mode read the **DELTA bb/100 and its direction**, not the
PASS/FAIL verdict: the `|t| > 2` significance gate is calibrated for the full
sample, so small-volume runs routinely report FAIL even when the DELTA is large
and correctly signed (e.g. MEDIUM vs 5× EASY at 500 hands: DELTA +193, t=1.6 →
FAIL on significance only). Re-run at full volume to sign off a change.

Since the hand evaluator was memoized (`MemoizedAlbertaEvaluator`, ~7× faster
than the raw Alberta path on Windows), a single 6-max matchup runs at roughly
8 hands/second per fork: ~1 minute for a 500-hand smoke, ~6 minutes for a
3 000-hand iteration pass, ~20 minutes for a full 10 000-hand matchup.

---

## 10. Final validation results

Volume: **200 sessions × 50 hands = 10 000 hands per matchup**, paralelised
under `forkCount=0.6C` on an 8-core AMD 9800X3D. With the memoized evaluator
the full gradient + baseline suite runs in well under an hour.

### 10.1 Baseline-quality (10 000 hands × 3 matchups)

The top level (HARD) seated against a uniform table of five fixed-strategy
archetypes:

| Matchup              | bb/100     | Floor   | Verdict   |
|----------------------|------------|---------|-----------|
| HARD vs 5× STATION   | **+731.0** | +150  | ✅ PASS    |
| HARD vs 5× MANIAC    | **+254.2** | +100  | ✅ PASS    |
| HARD vs 5× ROCK      | **-9.0**   | -25   | ✅ PASS    |

HARD crushes the loose-passive fish-fest by ~5× the floor, traps the maniac
table by ~2.5× the floor, and the nit-heavy ROCK table lands comfortably
inside the 6-max math floor. Crucially, the post-flop bluffing added to the
engine does **not** degrade value extraction — and stays disciplined: against
the foldable ROCKs the bot bluffs the river ~10% of its bets, against the
never-folding STATIONs it bluffs 0%.

### 10.2 Gradient acid test (10 000 hands × 3 matchups)

Each adjacent difficulty pair, hero vs five villains:

| Matchup            | DELTA bb/100 | SE     | t-stat  | Verdict  |
|--------------------|--------------|--------|---------|----------|
| HARD vs 5× MEDIUM  | **+127.0**   | 23.4   | 5.43    | ✅ PASS   |
| MEDIUM vs 5× EASY  | **+162.5**   | 23.6   | 6.87    | ✅ PASS   |
| HARD vs 5× EASY    | **+297.5**   | 27.3   | 10.90   | ✅ PASS   |

Every adjacent pair clears the +30 floor and the |t| > 2 significance gate
with wide margins (t from 5.4 to 10.9). Merging the old EXPERT/HARD pair —
which sat at +30 bb/100, t≈1.0, below the significance gate — into a single
top level removed the only matchup a human could not tell apart, leaving three
levels that are each clearly distinguishable within a single session.

### 10.3 Stats per difficulty (6-max, 10 000+ hands each)

| Difficulty | VPIP    | PFR    | AF       | cbet% | bluff% | rvBluff% |
|------------|---------|--------|----------|-------|--------|----------|
| EASY       | 56.5%   | 9.5%   | 0.62     | 48%   | 4.0%   | ~0.3%    |
| MEDIUM     | 44.5%   | 13.8%  | 0.86     | 55%   | 7.9%   | 3.4%     |
| HARD       | 25-30%  | 18%    | 2.2-3.3  | 63%   | 7-11%  | 4.5-6.8% |

The progression is exactly the intended one. VPIP falls (loose fish → tight
shark), the aggression factor rises sharply (0.62 → 0.86 → 2.2-3.3), and bluff
frequency climbs from "plays its river face-up" (EASY, rvBluff ~0%) to a
balanced betting range (HARD). EASY sits intentionally on the loose-passive
side — high VPIP, low PFR, AF below 1.0 — the recreational-fish signature; HARD
matches the industry 6-max shark band (VPIP 25-35, PFR 14-22, AF 2+).

---

## 11. Future work (deferred but architecturally ready)

The following sprints remain pending in the project's task list. They
are not required for AAA video-game quality but would push the bot
closer to "small-stakes online regular":

- **Sprint 2.3** — `HandTier` enum (replace integer tier with a typed
  category and explicit suited/offsuit predicates).
- **Sprint 2.4** — Bet sizing tree by motive (value 0.65 pot, polarised
  1.2 pot, blocker 0.35 pot, river overbet 1.5 pot). Currently the bot
  uses one wet/dry/semiwet sizing tree; the per-motive tree would
  reduce telegraphing.
- **Sprint 3.1** — Rich `OpponentTracker` (3-bet %, fold-vs-cbet,
  WTSD, donk %, river-call frequency).
- **Sprint 3.2** — Hooks in `Crupier.java` to feed the richer tracker.
- **Sprint 3.4** — Weighted equity vs range (instead of the current
  uniform random-hand equity).

The architecture (`HandStrengthEvaluator`, `DealerView`,
`BotPlayerView`, `BotEvaluator`) is already in place to slot any of
these in without rewriting the bot.

---

## 12. Credits and references

The poker equity engine is the **University of Alberta poker
library** (`org.alberta.poker`), wrapped behind `BotEvaluator` /
`AlbertaEvaluatorAdapter` so the bot is decoupled from the specific
implementation. The simulator design takes inspiration from public
work on adversarial bot benchmarking (PokerStars / partypoker bot
testing methodology). The mistake-injection difficulty pattern is the
same approach the Stockfish chess engine uses for sub-elite skill
levels and that the major sports video games (FIFA, NBA 2K, Madden)
use for AI difficulty differentiation.
