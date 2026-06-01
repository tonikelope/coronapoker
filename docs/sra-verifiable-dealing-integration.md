# Verifiable dealing — integration spec (Phases 3–5)

Status: **DRAFT / for review before touching the game.** This is the operational
"how to wire it" companion to [`sra-verifiable-dealing-design.md`](sra-verifiable-dealing-design.md)
(the "what/why"). The crypto engine it builds on is **done and validated** (Phases 1–2,
package `com.tonikelope.coronapoker.crypto`, 49 tests). Nothing here is implemented yet.

These phases modify the **live game flow** in `Crupier`/`WaitingRoomFrame`, so — per the
0.5 lesson (implicit dependencies a grep cannot find) — each lands behind a **manual smoke
of a real table + recovery**, not just unit tests.

---

## 0. Engine recap (already built)

`crypto.RistrettoSRA` is the drop-in replacement for the SRA primitives in `CryptoSRA`:

| CryptoSRA (Montgomery x-only) | RistrettoSRA (prime-order) | Notes |
|---|---|---|
| `generateLockScalar()` (clamped) | `generateLockScalar()` (uniform [1,L), no clamp) | 32 B |
| `getUnlockScalar(k)` | `getUnlockScalar(k)` | inverse mod L |
| `applyCommutativeLock(deck,k)` | `applyCommutativeLock(deck,k)` | null on off-group point (replaces `arePointsOnCurve`) |
| `getGenesisDeck()` | `getGenesisDeck()` | hash-to-group, unknown mutual DL |
| `resolveCardIndex(pt)` | `resolveCardIndex(pt)` | canonical-encoding compare |
| `arePointsOnCurve(d)` | (folded into `decode`) | decode returns null ⇒ invalid |

`crypto.Dleq.prove/verify` proves `h1=k·g1 ∧ h2=k·g2`. `crypto.Ristretto255` and
`crypto.EdwardsPoint` are the group. **Wire stays 32 bytes per point**, so the *command
protocol shape* does not change — only payload content and a few additive fields.

---

## 1. Phase 3 — migrate the cascade group (no new security yet)

Goal: swap the SRA group from `CryptoSRA` to `RistrettoSRA` end-to-end, with **no protocol
behaviour change**, so any regression is isolated to "the group changed".

- Replace `CryptoSRA.*` SRA calls in `Crupier` (cascade, rotation, pocket/community dealing,
  showdown verify) with `RistrettoSRA.*`. Keep `CryptoSRA.shuffleDeck` (byte-agnostic, unchanged).
- The genesis deck changes representation (Ristretto encodings) — every peer derives it
  identically, nothing on the wire.
- `arePointsOnCurve` gates become "`decode != null`" (or keep `arePointsOnCurve` as a thin
  wrapper that tries `decode`).
- Bump the **protocol/cascade version**: a table runs all-old or all-new (incompatible group;
  no mixed cascade). Surface a clear version-mismatch message at join.

**Gate:** the existing `CryptoSRACascadeTest`-style flow green on the new group (already have
`RistrettoSRACascadeTest`); full app smoke: open table, play a hand, showdown.

This phase alone does NOT close the blind oracle — it only changes the group. The verifiable
binding is Phase 4.

---

## 2. Phase 4 — verifiable dealing (closes the blind oracle)

### 2.1 Key commitments in H_0 (HAND_V2)

Each peer derives, per hand, `k_pocket`, `k_community` and publishes the commitments
`K_pocket = k_pocket·B`, `K_community = k_community·B` (32 B each). These are folded into the
`HandStateChain` H_0 preimage, sorted by player id, alongside the existing deck commitment:

```
H_0 = SHA-256(
        "HAND_V2\0" || HAND_ID || uint8(N) || sorted_player_ids
        || SHA-256(MEGAPACKET) || sorted( player_id || K_pocket || K_community )_concat
      )
```

So every peer's H_0 binds the exact public keys used. Tampering diverges the chain at H_0,
exactly like the deck commitment today (`HandStateChain` §5.2).

### 2.2 The de-locking chain

Dealing order is the fixed ring order (already deterministic). To open slot `i`
(owner `ring[i]`), peers `≠ i` strip their lock in ring order; the owner strips last, locally.

- Chain start, **committed**: `X₀ = MEGAPACKET[i]` (the slot's point, locked by every ring
  member's `k_pocket`).
- Peer `m` (m ≠ i), in order, receives the current residual `X_{m-1}` plus the accumulated
  proof chain `{X₀, (X₁,π₁), …, (X_{m-1},π_{m-1})}`, and BEFORE applying its key verifies:
  1. `X₀` equals the locally-committed `MEGAPACKET[i]` **byte-for-byte**;
  2. every link `π_j` is a valid DLEQ for `log_B(K_{ring[j]}) = log_{X_j}(X_{j-1})`
     (i.e. `X_{j-1} = k_{ring[j]}·X_j`), with `K_{ring[j]}` from H_0.
  Then it produces `X_m = k_m⁻¹·X_{m-1}` and `π_m = Dleq.prove(k_m, B, K_m, X_m, X_{m-1})`,
  and returns `(X_m, π_m)`. Any failed check ⇒ `triggerSecurityLockdown` (attributable).

### 2.3 Why this kills the blinded oracle

The attack fed a peer `r·P` (a blinded version of a single-locked pocket). Now the peer
refuses unless the chain **starts at the committed `MEGAPACKET[i]`** and every step has a
DLEQ under a **committed** key. The host cannot:
- start at `r·MEGAPACKET[i]` (≠ the committed bytes ⇒ check 1 fails), nor
- inject a factor `r` mid-chain (no peer committed `r`, so no valid DLEQ ⇒ check 2 fails).

A peer only ever applies its key to inputs provably descended from the committed deck.

### 2.4 Position + phase binding (closes early-board)

- POCKET phase uses `K_pocket` and may only anchor to pocket positions `0..2N-1` of the
  committed `MEGAPACKET`.
- FLOP/TURN/RIVER (+RABBIT) phases use `K_community` and anchor to community positions.
- The committed `MEGAPACKET` is **post-rotation** (community slots carry `k_community`). The
  early-board attack used **pre-rotation** material (community with `k_pocket`); that material
  is not equal to any committed `MEGAPACKET[pos]` ⇒ check 1 rejects it. The rotation itself is
  not an oracle (it re-locks with `k_community` before returning, verified in source); a DLEQ
  on the rotation step is **optional** (attribution only) and can be deferred.

### 2.5 Wire changes (additive only)

`REQ_SRA_UNLOCK_BATCH` / `RESP_SRA_UNLOCK_BATCH` per-item payload gains the proof chain
(`X₀ … X_{m-1}` + `π₁ … π_{m-1}` on request; `X_m` + `π_m` on response). **No new command, no
new round-trip, no state-machine change** — the existing GATEs (state machine, anti-reuse
tags, length, early-cascade wait) stay as defence-in-depth on top of the now-sound chain.

Cost: O(N) proofs per item, O(N²) per batch. For poker N (≤ ~10) this is ~100 proofs/round;
tolerable (not real-time). If the BigInteger backend proves too slow here, the radix-16
optimisation (separate, test-backed) addresses it.

---

## 3. Phase 4b — deck integrity (collision check)

At resolve time, accumulate every resolved card index for the whole hand into one global
`Set` — **pockets + board (+ second board if run-it-twice later)**. A collision ⇒ MISDEAL /
lockdown **before any chips move**. This closes the blind-duplication finding cheaply (no
proof-of-shuffle, per the author decision). Substitution with off-deck bytes is already caught
(`resolveCardIndex == -1`).

---

## 4. Phase 5 — fossil, recovery, version

- Fossil stores Ristretto encodings (32 B, same column schema) + the per-hand `K_*`
  commitments needed to re-verify chains on resume. Add a fossil/version tag.
- A hand fossilised under the old (Montgomery) format is not resumable under the new engine:
  document "finish in-progress hands before updating" (cross-version mid-hand resume is a
  rare corner; do not silently mis-decode — detect version and refuse cleanly).
- Recovery re-injects the local scalars from the local fossil (no network oracle — verified)
  and re-verifies the chain on the persisted MEGAPACKET.

---

## 5. Forward-compat hooks (already decided)

- **Spectators (Model A):** out of the ring. Land as the isolated, smoke-tested Phase 0.5
  (`getAnilloCriptografico` + the implicit `recibirMisCartas:5257` guard + recovery tests).
  Independent of this crypto work; can go before or after.
- **Run-it-twice:** model unlock phases as `(board_idx, street)` and keep the collision `Set`
  hand-global, so a second board is added later as data, not a redesign (design doc §6.5).

---

## 6. Suggested order & gates

| Step | Change | Gate |
|---|---|---|
| 3 | Group swap to RistrettoSRA | Engine cascade test + **table smoke + showdown + recovery** |
| 4 | K commitments in H_0 + chained DLEQ in unlock batch | Adversarial test: blinded-oracle PoC now **rejected**; **multi-peer smoke** |
| 4b | Global index collision check | Forced-collision unit test; smoke |
| 5 | Fossil/version/recovery | **Recovery smoke** (mid-hand resume) |
| 0.5 | Spectators out of ring (Model A) | recovery/rebuy/bust-mid-hand smoke |

Each step: own branch checkpoint, never merged to master until green **and** the author's
manual smoke passes. The blinded-oracle PoC (`CryptoSRABlindOracleTest`) becomes a
regression guard once Phase 4 is in: after the binding, the blinded extraction must fail.
