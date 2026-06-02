# Verifiable SRA dealing — design spec

Status: **DRAFT / Phase 0**. This document specifies the fix for the blinded
decryption-oracle vulnerability in the EC-SRA dealing path. Nothing here is wired
yet; it exists to be reviewed before the crypto engine is extended.

---

## 0. Security invariant

**No party — the host included — may learn a single bit about any card before the
protocol legitimately reveals it.** Pockets stay hidden from everyone but their owner
until showdown; community cards stay hidden from everyone until their street. This is the
whole point of the mental-poker layer: it must hold even against a peer (or host) running
a recompiled, hostile client.

The vulnerability below breaks this invariant in its strongest form — the host can
reconstruct the **entire shuffled deck, order included**, at will. "Sees the community
early" and "reads a rival's pocket" are just instances of that single failure.

---

## 1. The problem

During dealing, each peer is asked (via `REQ_SRA_UNLOCK_BATCH`) to strip its own
SRA lock from a point the host relays. The handler applies the peer's secret
`sra_unlock` to host-supplied bytes and returns the result. The only cryptographic
guard is **GATE 6**: refuse if the result resolves to a genesis card.

GATE 6 is defeated by **multiplicative blinding**. The host holds the victim's
single-locked pocket `P = k_V · G_c` (it broadcasts it as `POCKET_CARDS`). It sends
the victim `r · P` for a random scalar `r` it controls, labelled under any legitimate
`(phase, peer_idx)` tag. The victim applies `k_V⁻¹`:

```
k_V⁻¹ · (r · P) = k_V⁻¹ · r · k_V · G_c = r · G_c
```

`r · G_c` is not a genesis point, so GATE 6 passes and the victim returns it. The host
unblinds with `r⁻¹ mod L` and recovers `G_c` → the victim's hole card.

PoC: `tools/qa/.../sra/CryptoSRABlindOracleTest.java` (passes today).

### Attack surface

The oracle exists wherever a peer applies its unlock to host-chosen bytes and returns the
result. Audited:

| Site | Scalar | Oracle? | Notes |
|---|---|---|---|
| Pocket dealing (`REQ_SRA_UNLOCK_BATCH` POCKET) | `k_pocket⁻¹` | **Yes** | Reads any pocket |
| Community dealing (FLOP/TURN/RIVER + RABBIT_*) | `k_community⁻¹` | **Yes** | Street-gated but indistinguishable within the street |
| Early board via POCKET phase + pre-rotation material | `k_pocket⁻¹` | **Yes** | **Bypasses the early-cascade guard** — pre-rotation community pieces still carry `k_pocket`; the POCKET phase is always open |
| Showdown (`RESP_SHOWDOWN_KEY`) | whole signed key | No | Sound — revealer hands over its own key, signed |
| POTCARDS / SHOWCARDS | whole signed key | No | Sound |
| Rotation (`DECK_ROTATION_REQ`) | `uPocket` then re-lock `kCommunity` | No | Verified (WaitingRoomFrame:2220-2221): the peer applies its pocket-unlock **but re-locks with `kCommunity` before returning** — the intermediate never leaves, so even a blinded `r·piece` comes back as `kCommunity·uPocket·r·piece`, unreadable without the peer's community key |
| Recovery (`RECOVERDATA`/fossil) | — | No | Verified in-source: the fossil (`guardarFosilSRA`, 8276-8393) stores only the local peer's own scalars + single-locked residuals; resume restores from the local SQLite fossil, never from a host payload. No network oracle. |

Note for the redesign: the rotation also applies a pocket-unlock, so its DLEQ binding must
likewise tie the input to the committed `MEGAPACKET` community slice — but it is not an oracle
today because of the immediate `kCommunity` re-lock.

Two design consequences:

1. Chain verification must bind **position *and* phase/scalar**, not merely "descends from a
   deck": a POCKET-phase payload must not anchor to a community position, and **pre-rotation
   material must not be a valid `X₀`** — only the committed, post-rotation `MEGAPACKET`. This
   is what closes the early-board vector.
2. The attacker is **always the host** (star topology: every `REQ_*` originates from
   `writeCommandFromServer`; a peer cannot use another peer as an oracle directly).

### Why there is no cheap fix

This is the blind-signature property of commutative SRA, not an implementation slip.
Three "obvious" mitigations all hit a **theoretical** wall:

- **Inspect the output** (extend GATE 6): the result is `r · G_c`, an indistinguishable
  random group element. No local check on the result can separate it from a still-locked
  residual.
- **Commit the residuals up front**: the host cannot pre-commit dealing residuals because
  it does not know them — stripping another peer's layer requires that peer's key.
- **Bound the oracle / detect after the fact**: the host always owns its own slot tag
  (`POCKET, host_idx`) against every peer, so it can read *every* pocket at the cost of
  only its own hand's knowledge — a brutal edge in poker, and cryptographically
  undetectable (only a statistical tell). Post-hoc detection would require revealing every
  key + shuffle seed, which destroys folded-card privacy.

The defence must come from **outside** the commutative operation: a zero-knowledge proof
that binds each de-locking to the committed cascade.

---

## 2. Approach — chained verifiable decryption

Two pieces:

1. **Move the SRA group from Montgomery x-only to Ristretto255.** Ristretto gives a
   prime-order group (cofactor eliminated by construction) with canonical, non-malleable
   encodings. We need point addition (which x-only lacks) for DLEQ proofs, and prime order
   so the proofs have no small-subgroup escape hatch. Bonus: this **removes the cofactor
   caveats** already documented in `CryptoSRA.isPointOnCurve` — they stop being a
   "defence-in-depth with an asterisk" and become impossible.

2. **DLEQ (Chaum–Pedersen) proof on every de-locking, chained from the committed deck.**

### 2.1 Commitments

Each peer derives, per hand, its SRA scalars `k_pocket`, `k_community` (uniform in
`[1, L)`; no clamping needed in a prime-order group) and publishes the public commitments

```
K_pocket    = k_pocket    · B
K_community = k_community  · B          (B = Ristretto255 base point)
```

These are folded into `H_0` alongside the existing deck commitment, sorted by player id,
so every peer's `H_0` binds the exact set of public keys used in the cascade. Tampering
with any `K` diverges the chain at `H_0` (same mechanism as the deck commitment today).

### 2.2 The chain

Dealing order is the fixed ring order (already the case). To open slot `j`, layers are
stripped in that fixed order by every peer `≠ j`; the owner strips its own layer last,
locally.

- Chain start, committed and public: `X₀ = MEGAPACKET[j]` =
  `(k_{p₁}·k_{p₂}·…·k_{p_N}) · G_{c_j}`.
- Peer `p_m` (m ≠ j) receives residual `X_{m-1}`, produces
  `X_m = k_{p_m}⁻¹ · X_{m-1}`, and attaches a proof

  ```
  DLEQ_m :  log_B(K_{p_m}) = log_{X_m}(X_{m-1})   (= k_{p_m})
  ```

  i.e. Chaum–Pedersen equality of discrete logs over the pairs `(B, K_{p_m})` and
  `(X_m, X_{m-1})`, asserting `X_{m-1} = k_{p_m} · X_m`. Non-interactive via Fiat–Shamir
  over a domain-separated transcript (`"SRA_DLEQ_V1"` ‖ HAND_ID ‖ phase ‖ peer_idx ‖
  B ‖ K ‖ X_m ‖ X_{m-1}`).

### 2.3 What closes the attack

Before applying its own key, **every peer verifies the incoming partial chain**
`X₀ → … → X_{m-1}`, and the verifier **requires `X₀` to equal the committed
`MEGAPACKET[j]` byte-for-byte** (canonical Ristretto encoding).

A blinded start `X₀ = r · MEGAPACKET[j]` ≠ the committed value → the chain does not
begin at the commitment → rejected immediately, with an attributable lockdown. The host
cannot fabricate a proof turning `MEGAPACKET[j]` into `r · MEGAPACKET[j]` because that
would require a committed key equal to `r`, which no peer published. The oracle is dead:
a peer only ever applies its key to inputs provably descended from the committed deck.

---

## 3. Crypto engine additions (Ristretto255, pure Java)

New module (e.g. `CryptoGroup`/`Ristretto255`) providing:

- field arithmetic mod `2²⁵⁵-19` (reuse the existing radix-16 limbs where possible),
- Edwards point add / double / variable-base and base-point scalar mul,
- Ristretto encode / decode (canonical, rejects non-canonical & non-Ristretto inputs),
- hash-to-group (Elligator) for the genesis deck and for any map-to-point need,
- scalar arithmetic mod `L` (add, mul, inverse) for proof responses.

**Validation (Phase 1, highest risk, isolated):** official Ristretto255 test vectors
(encode/decode, base multiples, hash-to-group), plus add/double/scalarmul round-trips and
`k · k⁻¹ = identity` checks. This phase ships behind tests before anything else touches it.

---

## 4. DLEQ primitive

`prove(k, B, K, P, Q) -> (c, s)` and `verify(B, K, P, Q, proof)` for the statement
`K = k·B ∧ P = k·Q`, Fiat–Shamir as in §2.2.

**Validation (Phase 2):** valid proof verifies; wrong `k` fails; a blinded `P' = r·P`
fails; transcript/domain confusion fails; malleated `(c,s)` fails.

---

## 5. Protocol wiring

- `H_0` preimage gains the sorted `K_pocket`/`K_community` of every ring member
  (`HandStateChain` bump → `HAND_V2`).
- `REQ_SRA_UNLOCK_BATCH` item carries the accumulated chain (`X₀ … X_{m-1}` + the
  per-link DLEQ proofs) instead of a bare payload.
- The peer: verifies `X₀ == MEGAPACKET[peer_idx]`, verifies every incoming DLEQ link,
  then applies its key and appends `X_m` + `DLEQ_m` to the response. Any failure →
  `triggerSecurityLockdown` with an attributable reason.
- Same shape for the community pieces (`FLOP/TURN/RIVER` phases) using `K_community`.

The existing state machine, anti-reuse tags, on-curve gate and early-cascade wait are all
kept; they become defence-in-depth on top of the now-sound chain.

---

## 6. Phasing

| Phase | Deliverable | Gate |
|---|---|---|
| 0 | This design | Author review |
| 1 | Ristretto255 engine | Official test vectors + round-trips |
| 2 | DLEQ prove/verify | AAA proof tests (incl. blinded-input rejection) |
| 3 | SRA cascade ported to Ristretto | `CryptoSRACascadeTest` adapted, green |
| 4 | Verifiable dealing wired in | Blind-oracle PoC now **rejected**; smoke green |
| 5 | Protocol version bump, fossil, recovery | Recovery smoke green |

---

## 6.5 Forward-compatibility: run-it-twice (planned, not yet implemented)

Run-it-twice (a second board dealt from the unused deck when players are all-in with an
incomplete board) is **planned for after this rework**, so the design must accommodate it
now to avoid a second crypto change. Cryptographically it is just "reveal a second set of
community-locked cards from still-unused positions, with the same DLEQ + collision mechanism".

Key fact: the dual-lock rotation already converts the **entire** leftover block
(`Crupier.java:957-961`, positions `2N..51`) to `k_community`, so `52-2N` community-locked
cards are always available — far more than any second board needs. No change to cascade or
rotation.

To make run-it-twice a later *addition* rather than a *redesign*, this rework must bake in:

1. **A board/run index** in the DLEQ binding and the unlock-phase space — model phases as
   `(board_idx, street)`, not a single implicit board. Adding a second board then adds
   values, not new crypto.
2. **A hand-global index collision check** spanning pockets + board 1 + board 2 — one `Set`
   for the whole hand, never per-board.
3. **An extensible unlock state machine** — second-board phases gated at the right moment
   (post all-in), mirroring how `RABBIT_*` are gated by `show_time` today.

None of this touches Ristretto, the DLEQ primitive or the rotation; it only widens the phase
space by one axis.

## 6.6 Decision: spectators stay in the ring (no game-protocol change)

The ring is rebuilt fresh every hand (`getAnilloCriptografico`, `Crupier.java:867`; recovery
re-reads `ORDER@` only for the in-progress hand). Membership already changes hand-to-hand
(bust → spectator → rebuy → active next hand) and the system handles it.

`isActivo() == !exit && !spectator` (`LocalPlayer.java:3185`). Spectators (normal, not
`calentando`) **are** in the crypto ring (they contribute a lock+shuffle and act as decrypt
helpers) but **do not receive cards** and, for viewing the board, are treated as **observers**
trusting the host's signed `COMM_REVEAL` (`Crupier.java:7488-7491`) — they do *not* read the
board via SRA.

### Inconsistency found (to resolve in 0b)

The current spectator model is **internally inconsistent**:

- Spectators **are** in the ring and **contribute a lock+shuffle** to the cascade
  (`getAnilloCriptografico` includes them, `12457`; the cascade loop calls
  `requestRemoteCascade` for any non-local, non-CPU, non-exit member, `903-938`).
- Yet they are **excluded from the consensus/receipt** (`computeExpectedConsensusSigners`,
  `6341-6343`) and **do not read the board via SRA** (observer, `7488-7491`).
- The consensus Javadoc even claims spectators *"did not participate in the SRA cascade"*
  (`6302-6303`) — **stale/incorrect**: they did contribute a lock.

So a spectator injects a blind cipher layer but verifies nothing and signs nothing. For
**money security this is irrelevant** (active players verify the board and sign receipts;
mental-poker safety holds on one honest *active* player), and the DLEQ binding + collision
check cover a spectator like any other ring peer. But the asymmetry must be made coherent
before wiring the redesign.

Two coherent models — **decide in 0b with an impact map, not blind**:

- **Model A — spectator = pure observer:** out of the ring, contributes no lock, trusts the
  host's signed reveal (like `calentando`). Simplest. Removal does **not** cause a rebuy
  "jaleo": the ring is rebuilt fresh each hand, so a returning player rejoins as active next
  hand (today's normal flow).
- **Model B — spectator = full participant:** in the ring, contributes a lock, **reads the
  board via SRA and signs a receipt**. More cryptographic witnesses (defence-in-depth), but
  more complexity in recovery/testament.

**DECISION (author): Model A — spectators are passive observers, out of the crypto ring.**

Consequence: the ring becomes exactly the `isActivo()` set → *ring member ⟺ card recipient*.
This also simplifies the redesign (every slot resolves; every ring member signs a receipt and
reads the board via SRA — no "non-resolving slot" special case).

Model A is a **game-protocol change** and must land as an **isolated, tested step before the
crypto engine** (its own branch checkpoint, with recovery/rebuy/bust-mid-hand non-regression
tests). It does NOT cause a rebuy "jaleo" (ring is rebuilt fresh each hand). Scope to map in
0b — every site that assumes a spectator is in the ring:

- `getAnilloCriptografico` (`12457`): exclude `isSpectator()` (ring = `isActivo()` only).
- Pocket dealing (`1055-1166`): no spectator slots; the `isActivo()` guard at `1159` becomes
  redundant (harmless).
- Consensus (`6341-6343`): already excludes spectators → now consistent; fix the stale Javadoc
  (`6302`).
- Community reveal (`7488-7491`): spectator already an observer → consistent.
- Recovery `ORDER@`, testament, decrypt-helper duties: spectators no longer contribute a lock,
  so they drop out of all three — verify nothing assumed their presence.
- Bust-mid-hand: ring is fixed at hand start, so a player who busts mid-hand stays in that
  hand's ring and leaves the ring next hand (unchanged) — confirm.

**Impact-map result (0b) — REVISED after an attempted 0.5:** Model A is **NOT** a one-line
change, and the grep-based impact map was **incomplete**. Excluding spectators from
`getAnilloCriptografico` makes a spectator client hang: `recibirMisCartas()` is still called
for any non-`calentando` client (`Crupier.java:5257-5259`, **no `isSpectator` guard**) and
blocks forever waiting for a `POCKET_CARDS` the host no longer sends once the spectator is out
of the ring. This is an **implicit** dependency ("in the ring ⇒ receives cards") that a grep
for `isSpectator` cannot find — so **more such hidden points may exist**, and only a manual
smoke of a *table-with-spectator* (+ recovery) confirms they are all caught.

Consequence for sequencing: **0.5 touches the live game flow and its validation requires
manual smoke that is currently unavailable.** The change was reverted. Recommendation: **do
Phase 1 (Ristretto engine) first** — it is *isolated* new code validated against official test
vectors, with **zero game-flow regression risk**, so it is safe to build autonomously. Defer
0.5 until it can be done with a full trace of the spectator client flow + a table-with-spectator
smoke. (Model A remains the chosen end-state; only its scheduling moves.)

## 7. Migration & compatibility

- New protocol/cascade version; a mesa runs all-old or all-new (no mixed interop —
  the group changes, so old/new cannot share a cascade). Surface a clear version-mismatch
  message instead of a silent divergence.
- Fossil (`guardarFosilSRA`) stores Ristretto-encoded points/scalars; recovery re-injects
  and re-verifies the chain on resume.

---

## 8. Threat model after the fix

- **Blinded decryption oracle (host reads pockets): closed.** A peer never applies its key
  to an input not provably descended from the committed `MEGAPACKET`.
- **Cofactor / small-subgroup caveats: gone** — prime-order group by construction.
- Unchanged inherent limits (N-1 collusion, local trojan, first-contact MITM without
  password) remain as documented in `SECURITY.md §9`.
