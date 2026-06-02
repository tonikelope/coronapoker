# EC-SRA security audit (Phase 0a)

Status: **DRAFT, for author review.** Exhaustive threat sweep of the current EC-SRA
mental-poker scheme, run *before* committing to a target cryptographic scheme — so the
redesign closes every known family at once and the foundation is chosen only once. Findings
are tagged with their verification state. The fix design lives in
[`sra-verifiable-dealing-design.md`](sra-verifiable-dealing-design.md).

Method: code located via focused searches; **every security conclusion re-read and verified
in-source by hand** (file:line below). Sub-agents only located code; they did not adjudicate.

---

## Verdict by threat family

| Family | Result | Severity |
|---|---|---|
| CSPRNG / seeds | Sound | — |
| Confidentiality — blinded decryption oracle | **Broken** | **Critical** |
| Confidentiality — recovery / fossil path | Sound | — |
| Integrity — card duplication (deck is not verified to be a permutation) | **Gap** | **Medium** |
| Integrity — substitution with off-deck garbage | Covered | — |
| Group math — cofactor / twist / encoding | Bounded today; removed by Ristretto | Low (asterisk) |
| Shuffle fairness | Sound | — |
| Lockdown / cascade-restart abuse | Local-only DoS (inherent) | Low |

---

## 1. CSPRNG / seeds — SOUND

`Helpers.CSPRNG_GENERATOR` is a single static `SecureRandom` initialised at startup to
`DRBG` (Hash_DRBG, SHA-512, 256-bit), fallback `new SecureRandom()` (`Init.java:1072`).
All SRA randomness draws from it: lock scalars (`CryptoSRA.generateLockScalar`),
`local_hand_seed` (48 B, **fresh per hand**, `Crupier.java:4388`), bot seeds (48 B per bot
per hand, `Crupier.java:910`), `HAND_ID` (16 B fresh per hand, `Crupier.java:841`). No reuse
across hands. No `java.util.Random`/`Math.random` anywhere in the card/seed/scalar path (the
only `java.util.Random` is `Helpers.genRandomString` for temp filenames — non-crypto).

## 2. Confidentiality — blinded decryption oracle — CRITICAL (known)

Documented in detail in the design spec §1. `REQ_SRA_UNLOCK_BATCH` applies the peer's
`sra_unlock`/`sra_unlock_community` to host-chosen bytes and returns the result; GATE 6
("result must not resolve to genesis") is defeated by multiplicative blinding `r·P`. The
host can peel every layer of any deck position → **reconstruct the entire shuffled deck at
will**. Pockets, community early-reveal (via the always-open POCKET phase over pre-rotation
material) and full-deck read are all instances of this one root flaw. PoC:
`CryptoSRABlindOracleTest` (passes).

## 3. Confidentiality — recovery / fossil — SOUND

Verified: the fossil (`guardarFosilSRA`, `Crupier.java:8276`) stores only the **local** peer's
own scalars (`SRAKEYS@`, `SRAKEYS_COMMUNITY@`) plus `POCKETS@` single-locked residuals —
never another peer's scalar. On resume, a client restores `local_sra_unlock(_community)` and
`single_locked_pocket_cards` **only from its own SQLite fossil** (`Crupier.java:3493–3590`),
not from the host's `RECOVERDATA` (which carries only game metadata, no scalars). The only
`applyCommutativeLock` in the post-recovery path (`Crupier.java:10712`) operates on the
locally-restored `pocketCards` with a peer-signed `sraKey` that is Ed25519-verified first
(`Crupier.java:10685`). Spectators/`calentando` accept host plaintext, but only when they
hold no cipher and have no money in the hand, and the reveal is still signature-checked.
**No network oracle in recovery.**

## 4. Integrity — card duplication — MEDIUM GAP (new finding)

Verified: nothing checks that the cascaded deck is a permutation of **52 distinct** genesis
points. Validation on every received deck is `length == 1664 && arePointsOnCurve(...)`
(`Crupier.java:594`, `WaitingRoomFrame.java:2113` cascade, `:2213` rotation) — on-curve only.
At resolve time, pocket dealing (`Crupier.java:1151–1153`), community
(`Crupier.java:7431–7440`) and showdown (`Crupier.java:8702–8715`) check each card resolves
to a genesis index but **never** check `id1 != id2` or a global index `Set` for collisions.

Consequence: a peer can, during its blind lock+shuffle, **duplicate** a legitimate ciphertext
(two positions → same card) undetected. Note the bounds:

- **Substitution** with an off-deck point is already caught (`resolveCardIndex == -1` →
  lockdown).
- **Duplication** is *blind sabotage*: the attacker cannot see which card it duplicates, so it
  gains no information — it can only corrupt the hand.

Cheap, proportionate fix: a global index `Set` across all resolved cards → MISDEAL/lockdown on
collision, before any chips move. **Proof-of-shuffle is not warranted** (see §9).

## 5. Integrity — off-deck substitution — COVERED

As above: any point that does not resolve to a genesis card yields `-1` and triggers a
lockdown at deal/showdown time. Only the (information-free) duplication case slips through.

## 6. Group math — cofactor / twist / encoding — LOW (asterisk, removed by Ristretto)

`isPointOnCurve` (Legendre) rejects twist points; the genesis ×8 cofactor multiply +
clamped scalars bound small-subgroup leakage to ~3 bits on Curve25519 (already reasoned in
`CryptoSRA.isPointOnCurve` comments). Shuffle uses rejection sampling — no modulo bias
(`DeterministicStream.getUnbiasedInt`). This is the one "defence-in-depth with an asterisk";
moving to Ristretto255 (prime-order, canonical encodings) **removes it entirely**.

## 7. Shuffle fairness — SOUND

Verified: the crypto ring is sorted **alphabetically by nick** (`getAnilloCriptografico`,
`Crupier.java:12452`) — deterministic and identical on every client; the host cannot pick the
order or place itself last. Each peer shuffles with its own secret seed over the previous
output (`Crupier.java:899–923`, client side `WaitingRoomFrame.java:2130`). One honest
shuffler suffices to make the permutation uniform and hidden (standard mental-poker result).

## 8. Lockdown / cascade-restart — LOW (inherent DoS)

Verified: `SECURITY_LOCKDOWN` is a process-local `static volatile` flag (`Crupier.java:350`);
**it is never sent over the wire** (no `LOCKDOWN#` command, no broadcast — all 14 uses are
local reads). A peer that trips it merely closes its own socket and self-exits
(`Crupier.java:371`) → host sees a drop → MISDEAL → the table continues without it. The
cascade restart loop (`Crupier.java:902–943`) has no retry cap but converges by dropping
exited peers. Net: a malicious peer can force MISDEALs — which it can already do by
disconnecting. No new exposure.

---

## 9. Target scheme — closes everything, foundation chosen once

The sweep finds exactly **two** things to close, and both fit one foundation with **no hidden
expensive component**:

- **Foundation (chosen once): Ristretto255** — prime-order group, canonical encodings. Wire
  stays 32 bytes/point. Also retires §6.
- **Component 1 — chained DLEQ verifiable decryption** → closes the blinded oracle (§2).
- **Component 2 — global card-index collision check at resolve time** → closes duplication
  (§4). **Proof-of-shuffle is explicitly rejected (author-confirmed, option (a))**: duplication
  is information-free sabotage (the shuffle is blind to everyone, so no peer can *direct* a
  card), detectable a posteriori for a few lines of code, with MISDEAL before any settlement.
  The residual gap — a blind duplicate whose copy lands in a never-revealed position — is not
  exploitable for gain. A full proof-of-permutation (Bayer–Groth) would be disproportionate,
  and being additive over the same Ristretto foundation it can be added later if ever wanted.

If a future finding appears, it is an **additive** component over the same Ristretto
foundation — not a scheme change. That is the property that prevents a second rewrite.

---

## 10. Honest limits

No audit proves the absence of all future bugs. This sweep covers the known attack families
for SRA/ElGamal mental poker (group math, decryption oracles, deck integrity/permutation,
shuffle fairness, RNG, DoS/attribution). Inherent limits unchanged and already documented in
`SECURITY.md §9`: N-1 collusion, local trojan, first-contact MITM without password.
