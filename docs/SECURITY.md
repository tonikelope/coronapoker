# Security architecture

How CoronaPoker enforces the "nobody cheats, not even the host" promise. This document covers the cryptographic primitives, the data flows that bind them together, and the failure modes they detect. File and line references are kept current with the codebase; the deeper internal spec for the identity layer lives in [`ec-identity-spec.md`](ec-identity-spec.md).

---

## 1. Threat model

| Adversary | Defended? | How |
|---|---|---|
| Hostile host trying to peek at a peer's pocket cards | Yes | EC-SRA over **Ristretto255** + **verifiable dealing**: every de-lock carries a chained DLEQ proof anchored to the committed deck, so the host cannot use a peer as a blinded-decryption oracle (§2.5). Pockets stay multi-locked until the owner releases their key at showdown |
| Hostile host trying to peek at community cards before their street | Yes | Same verifiable-dealing chain applied to the community deal + GATE-6 genesis check; the street gate only opens in lockstep with local betting (§2.5, §8) |
| Hostile host reading the pocket of a player who **abandons** mid-hand | Partial (self-delating) | The only residual vector: it requires forging a board (misdeal), which is self-delating since legitimate misdeals are tightly enumerated. The rotation is single-use per cascade (anti-replay) and the client warns + recommends leaving the table. Full closure (signing the shuffle) deferred as not worth the cost — see §9 |
| Hostile host or peer rewriting hand history | Yes | Per-action Ed25519 signatures + `H_t` ratchet committed by every peer in a signed receipt |
| Network MITM on the ECDH handshake | Yes (with password) | HMAC-SHA512 binding of the shared secret to a table password; session-key identicon for OOB compare (waiting room, right-click your own nick) |
| Replay or tampering of game commands on the wire | Yes | AES-256-CBC with per-command IV + HMAC-SHA256 over `IV \|\| ciphertext` |
| Java deserialization gadgets via RECOVERDATA | Yes | Strict `ObjectInputFilter` whitelist (HashMap / String / numeric boxes only, 10 MB cap, depth 20) |
| Off-curve / weak-point attacks on the SRA cascade | Yes | Ristretto255 canonical-encoding validation on every received point (prime-order group ⇒ no small-order / non-canonical ambiguities), atomic security lockdown on failure |
| Card substitution at showdown | Yes | Pocket key reveals signed under a domain-separated context; mismatch aborts the hand |
| Cross-recipient board fork (host announces different community cards to different peers) | Yes | Host signs community-card announcements; signature absorbed into every recipient's `H_t` |
| Local trojan / Ring-0 attacker on the victim's machine | No | Identity keys live in `~/.coronapoker/identity_<player_id>.ed25519` with restricted ACLs; a sufficiently privileged local attacker reads them |
| Collusion of N-1 peers against 1 victim | Inherent | Victim sees the divergent receipt in the disputed-hands log and can refuse to settle |

---

## 2. Mental Poker — EC-SRA cascade

The deck is shuffled and locked **collectively**. No single peer ever sees a card before it is legitimately revealed, and no peer (host included) can bias the distribution.

### 2.1 Genesis deck

The 52 cards are mapped deterministically to **Ristretto255** group elements:

- For each card index a 64-byte seed is computed as `SHA-512(CARD_LABEL_PREFIX || index)`.
- The seed is mapped into the group with the RFC 9496 hash-to-group map (Elligator 2), producing a canonical 32-byte Ristretto encoding ([`RistrettoSRA.java`](../src/main/java/com/tonikelope/coronapoker/crypto/RistrettoSRA.java) — `getGenesisDeck`; [`Ristretto255.java`](../src/main/java/com/tonikelope/coronapoker/crypto/Ristretto255.java) — `hashToGroupEncoded`).
- Ristretto255 is a **prime-order** group over edwards25519: there is no cofactor to clear and every element has a single canonical encoding, so the off-curve / small-order / non-canonical-encoding ambiguities of the old x-only Curve25519 representation simply do not exist.
- The 52 encodings are the genesis deck. Every peer derives the same deck independently — nothing has to be sent.

> **Migration note.** The SRA group was migrated from a Montgomery x-only Curve25519 representation to Ristretto255. The new pure-Java engine lives under [`crypto/`](../src/main/java/com/tonikelope/coronapoker/crypto/) (`Fe25519`, `EdwardsPoint`, `Ristretto255`, `RistrettoSRA`, `Dleq`), anchored to the RFC 9496 test vectors. The ECDH **channel** handshake (§3) still uses Curve25519 — only the Mental-Poker group changed.

### 2.2 Lock-permute-rotate

Each peer holds two ephemeral scalars per hand: a **pocket** scalar `k_pocket` and a **community** scalar `k_community`. The cascade walks around the ring:

1. **Pocket lock pass.** Player 1 multiplies every card by their `k_pocket`, permutes the deck with a deterministic AES-256-CTR shuffle (see §2.4), and forwards. Player 2 does the same on top of player 1's output. By the time the deck returns to the dealer, every card has been locked once per ring member with their `k_pocket`, in a permutation nobody fully knows. This becomes the `MEGAPACKET`.
2. **Community rotation pass.** A separate pass walks the community-card slots and *rotates* each peer's lock: every peer strips its own `k_pocket` from the slot and re-locks it under its `k_community`. Pocket cards keep the full `k_pocket` chain untouched; community cards end up locked **only** under each peer's `k_community` — the pocket locks are gone. The rotation is **single-use per cascade** (anti-replay): a second rotation request without a fresh cascade is refused, since serving it would turn the rotation into a covert pocket-unlock oracle (§2.5, §8). (`DECK_ROTATION_REQ` handler in [`WaitingRoomFrame.java`](../src/main/java/com/tonikelope/coronapoker/WaitingRoomFrame.java); rotation scalars in [`Crupier.java`](../src/main/java/com/tonikelope/coronapoker/Crupier.java).)
3. **Pocket dealing (verifiable).** Each pocket card travels to its owner with all *other* peers' `k_pocket` already inverted — only the owner's own `k_pocket` remains, which the owner inverts locally. **Every de-lock step carries a Chaum-Pedersen DLEQ proof chained from the committed `MEGAPACKET` point** ([`DealChain.java`](../src/main/java/com/tonikelope/coronapoker/crypto/DealChain.java), [`VerifiableUnlock.java`](../src/main/java/com/tonikelope/coronapoker/crypto/VerifiableUnlock.java)), so a peer refuses to act as a blinded-decryption oracle — the host cannot feed in a blinded `r·P` to de-lock (§2.5).
4. **Community reveal per street (verifiable).** The community deal runs over the **same proof-chained de-lock** anchored to the `MEGAPACKET`, plus a genesis-check (GATE 6): if stripping our lock would reveal a card, it is extraction and is refused. The street gate only opens once the local betting machine has reached that street (anti early-cascade, §8). The host collects the unlocks, exposes the card to the table and absorbs a host-signed announcement into every peer's `H_t` (see §5).

Pockets never receive community-key unlocks. Community cards never receive pocket-key unlocks. The two locks live in disjoint scalar spaces.

### 2.3 Showdown reveal

When a player reaches showdown they broadcast `RESP_SHOWDOWN_KEY` containing their `k_pocket` and a 64-byte Ed25519 signature over `"SHOWDOWN_V1" || HAND_ID || nick || k_pocket`. Every peer:

- Validates the signature against the peer's pinned Ed25519 pubkey.
- Applies `k_pocket` to the single-locked residual the peer received during dealing.
- Decodes the resulting curve point to a card index by looking it up in the genesis deck.
- Cross-checks the result against the announcement the host broadcast.

Any mismatch — bad signature, unknown point, conflicting announcement — triggers `SECURITY_LOCKDOWN` (§8) and the hand is aborted before any chips move.

### 2.4 Deterministic shuffle

The permutation each peer applies is generated with **AES-256-CTR** seeded by a per-hand key, then converted to a Fisher-Yates shuffle using **rejection sampling** to eliminate modulo bias ([`CryptoSRA.java`](../src/main/java/com/tonikelope/coronapoker/CryptoSRA.java) — `shuffleDeck`, with the unbiased index draw in `DeterministicStream.getUnbiasedInt`). The seed is fresh per hand, so reordering carries no information across hands.

### 2.5 Verifiable dealing — closing the blinded-decryption oracle

An SRA cascade has a subtle, devastating weakness if de-locks are unauthenticated: the host can use any peer as a **blinded-decryption oracle**.

**The attack.** The host holds a peer's single-locked pocket point `P = k·G_card` (broadcast as `POCKET_CARDS`). It picks a random blinding scalar `r`, sends the peer `r·P` (relabelled as another slot, fresh tag) and asks it to "unlock". The peer applies `k⁻¹` → `r·G_card`, which is **not** a genesis card (it is blinded), so a genesis-only check passes and the peer returns it. The host multiplies by `r⁻¹` and recovers `G_card` — the hole card. Multiplicative blinding slips straight past a genesis-only check.

**The fix — chained de-locking with DLEQ proofs.** Each peer commits, per hand, to `K = k·B` for both its pocket and community scalars; these commitments travel in the `MEGAPACKET` and are **bound into `H_0`** (HAND_V2, §5.2). When a peer strips its lock it publishes, alongside the result, a Chaum-Pedersen **DLEQ proof** that it used exactly its committed `k` ([`Dleq.java`](../src/main/java/com/tonikelope/coronapoker/crypto/Dleq.java), [`VerifiableUnlock.java`](../src/main/java/com/tonikelope/coronapoker/crypto/VerifiableUnlock.java)). De-locks form a **chain** that must start at the committed `MEGAPACKET` point and where every link carries a valid proof under a committed key ([`DealChain.java`](../src/main/java/com/tonikelope/coronapoker/crypto/DealChain.java)).

A blinded `r·P` cannot anchor: the chain would not start at the committed bytes, and **nobody committed the factor `r`**, so no valid proof exists — the peer refuses. The host can only ask peers to de-lock points that provably descend from the committed deck, i.e. the legitimate dealing.

Two further guards close the back doors found while hardening this:

- **Self-strip guard (pockets).** A peer reads `megapacket[offsetBase]` *locally by index* and never de-locks a point inside its own pocket slot `[mySlot·2, mySlot·2+1]`, so a host that decouples the labelled `peerIdx` from the stripped point cannot make a peer reveal its own cards.
- **GATE 6 (community).** After a community de-lock, a residual that resolves to a genesis card is extraction (the host presented the "all-locks-but-mine" chain to make us reveal the board early) → refused. With the binding making blinding impossible, this genesis-check can no longer be evaded.

The legacy unauthenticated batch path (`REQ_SRA_UNLOCK_BATCH`) is now **obsolete and refused for every phase**, so the oracle cannot be reached through the old door either.

**Residual (rotation / "exiter").** The dual-lock *rotation* (step 2 of §2.2) is the one de-lock not yet under the verifiable chain — it precedes any committed anchor. It is mitigated by **anti-replay** (one rotation per cascade): abusing it forces a corrupted board, i.e. a self-delating misdeal. The full fix (a verifiable rotation anchored to a signed pre-rotation deck — the `RotationChain` engine is built and tested) is deferred as not worth the cost; see §9.

---

## 3. Channel security

### 3.1 Handshake

When a client connects:

1. The two endpoints exchange ephemeral Curve25519 pubkeys and run ECDH to derive a raw 32-byte shared secret.
2. The shared secret is passed through `Helpers.deriveChannelSecret` ([`Helpers.java`](../src/main/java/com/tonikelope/coronapoker/Helpers.java) — `deriveChannelSecret`), which HMAC-SHA512s it together with the table password (empty string when no password). The output is split into a 32-byte AES key and a 32-byte HMAC key.
3. If the password is configured but weak (`< 60 bits` entropy estimate) the host is warned before the table is created.

A passive MITM cannot complete the handshake without the password: the shared secret is sealed by HMAC-SHA512(`password \|\| dh_secret`), and a wrong password yields a wrong key on both sides — every subsequent command fails HMAC verification on receipt.

### 3.2 Frame format

Every game command on the wire is wrapped as:

```
Base64( HMAC-SHA256(IV || ciphertext, key_hmac)(32) || IV(16) || AES-256-CBC(plaintext, key_aes, IV) )
```

PKCS5 padding for the CBC layer; the MAC covers `IV || ciphertext` (encrypt-then-MAC) and is prepended to the frame. On receipt the HMAC is verified **first**; a bad HMAC raises `KeyException` and the receiver drops the frame ([`Helpers.java`](../src/main/java/com/tonikelope/coronapoker/Helpers.java) — `decryptCommand`). This blocks ciphertext tampering and replay of frames with mutated IVs.

### 3.3 Session-key identicon

An "identicon" dialog ([`IdenticonDialog.java`](../src/main/java/com/tonikelope/coronapoker/IdenticonDialog.java) `Mode.SESSION`) renders a deterministic mosaic from `SHA-256(AES key)`, reachable from the waiting room by **right-clicking your own nick** in the participant list. A client opens the mosaic of its single channel with the host; the host opens the per-client grid of all channels ([`SessionIdenticonMosaicDialog.java`](../src/main/java/com/tonikelope/coronapoker/SessionIdenticonMosaicDialog.java)). Peers compare the mosaic visually out-of-band (voice call, in-person glance) — different mosaics mean the handshake was MITM'd, and both sides should disconnect.

---

## 4. Per-nick cryptographic identity

### 4.1 Keypair storage

Each install carries an `Ed25519` keypair per nick, stored at:

```
~/.coronapoker/identity_<player_id_hex>.ed25519
```

Where `player_id_hex` is the first 8 bytes of `SHA-256(NFC(nick) UTF-8)` ([`IdentityManager.java`](../src/main/java/com/tonikelope/coronapoker/IdentityManager.java) — `playerIdHex`). The file is created with:

- **POSIX**: `Files.setPosixFilePermissions(rw-------)` (read/write owner only).
- **Windows**: ICACLS reset → grant FULL CONTROL only to the current SID → strip inheritance.

Identity binding is per-nick, not per-machine: re-using the same nick on the same machine reuses the same Ed25519 key.

### 4.2 Domain-separated signing contexts

Four application-level contexts are signed under distinct prefixes so a signature collected in one context cannot be replayed in another:

| Context | What it signs |
|---|---|
| `"ACTION_V1"` | A `CanonicalActionRecord` (see §5) — every bet, check, call, fold, raise, all-in, community announce |
| `"RECEIPT_V2"` | `HAND_ID \|\| H_final \|\| flags` — the final receipt sent to every peer at the end of the hand |
| `"SHOWDOWN_V1"` | `HAND_ID \|\| nick \|\| k_pocket` — releasing one's pocket key at showdown |
| `"JOIN_V1"` | The join handshake commitment that pins the pubkey on first contact |

The internal spec [`ec-identity-spec.md`](ec-identity-spec.md) covers each context in full detail (replay defenses, encoding rules, what each field commits to).

### 4.3 TOFU & identicon verification

The first time a pubkey is observed for a given nick the resolver pins it ([`TOFUResolver.java`](../src/main/java/com/tonikelope/coronapoker/TOFUResolver.java)). A `Mode.IDENTITY` identicon dialog renders the same deterministic mosaic over `SHA-256(pubkey)` for OOB comparison; clicking "verify" upgrades the entry from TOFU to "user-verified", remembered across sessions.

---

## 5. Canonical action records & `H_t` ratchet

### 5.1 Action record

Every hand action is encoded into a fixed 92-byte record ([`CanonicalActionRecord.java`](../src/main/java/com/tonikelope/coronapoker/CanonicalActionRecord.java)):

```
Offset Size  Field           Notes
  0    32    PREV_H          H_{t-1}
 32    16    HAND_ID         16 random bytes from host at hand start
 48    32    PLAYER_ID       SHA-256(NFC(nick) UTF-8)
 80     1    STREET          preflop/flop/turn/river/showdown
 81     1    ACTION_TYPE     bet/call/raise/fold/check/all-in/community
 82     8    AMOUNT_CENTS    int64 BE, cents (host-independent)
 90     2    FLAGS           bit0 is_allin, bit1 is_voluntary
```

For an `ACTION_COMMUNITY` record (a host-signed community-card reveal, §5.3) the `AMOUNT_CENTS` field is repurposed to carry the revealed card ordinals: each card is packed into one byte (`packCommunityCards` / `unpackCommunityCards` in [`CanonicalActionRecord.java`](../src/main/java/com/tonikelope/coronapoker/CanonicalActionRecord.java)), up to 3 cards for the flop. The layout is otherwise identical.

Cross-platform reproducibility relies on:

- NFC normalization of the nick before UTF-8 (precomposed vs decomposed unicode parity across OSes).
- Float → cents through `amountToCents` (widens to double before scaling, kills `0.1f + 0.2f` jitter).
- Big-endian for every multi-byte integer.

This encoder is the **single source of truth** for action serialization; any other path that reproduced the layout inline would be an integrity bug.

### 5.2 Chain

For each hand:

```
H_0 = SHA-256(
        "HAND_V1\0"                   // 8-byte domain separator
        || HAND_ID                    // 16 random bytes from host
        || uint8(num_players)
        || sorted_player_ids_concat   // 32 × N, lexicographic
        || SHA-256(cascaded_deck)     // deck commitment after SRA
      )

H_{t+1} = SHA-256(record_t || sig_t)
```

The deck commitment `SHA-256(cascaded_deck)` makes the chain bind to the *exact* permutation produced by the cascade — peers that walked a different cascade end up with a different `H_0` and their chains diverge on the very first absorb.

Signatures land **inside** the ratchet: tampering with a record OR with the signature breaks `H_{t+1}` for every observer.

### 5.3 Host-signed community announcements

Phase 3 of the EC-Identity work extends the same record format to community-card reveals (`ACTION_COMMUNITY`). The host signs the announcement; every recipient verifies and absorbs. Without this, a hostile host could announce a different flop to different peers and leave no chain-level evidence — with this, the announcement is part of `H_t`, so the divergence becomes observable in the receipt.

---

## 6. Receipts & consensus

At the end of the hand every peer emits a **receipt**:

```
HAND_ID(16) || H_final(32) || flags(1) || sig(64)   =  113 bytes
```

Where:

- `H_final` is the final value of the `H_t` ratchet.
- `flags.bit0` is set if the peer observed any invalid Ed25519 signature during the hand.
- `sig` is `Ed25519(privkey, "RECEIPT_V2" || HAND_ID || H_final || flags)`.

The host gathers the receipts from every peer and relays them to every other peer. The consensus check ([`Crupier.java`](../src/main/java/com/tonikelope/coronapoker/Crupier.java) — `waitForHandverifyTrigger` and the surrounding consensus loop) passes only when:

- Every `sig` verifies under the expected peer's pinned pubkey.
- Every `H_final` is identical.
- No `flags.bit0` is set anywhere on the table.

Any failure is logged into the `disputed_hands` table of the local SQLite. The hand is not unwound — chips already moved — but the dispute is archivable evidence with cryptographic signatures attached.

---

## 7. Crash recovery & late joiners

### 7.1 SQLite checkpoint

Every action, every balance change and every cascade outcome is persisted to a local SQLite database (`~/.coronapoker/coronapoker.db` typically). The tables of interest:

- `game`, `hand` — game and hand metadata, including BUYIN, blinds, dealer/SB/BB nicks, `hand_id_b64` (cryptographic HAND_ID), and `preflop_players` (the b64-encoded nick list of who sat down preflop).
- `balance` — per-hand player stack snapshots.
- `action` — every action as a canonical record, with the signing peer's signature alongside.
- `showdown`, `showcards`, `permutationkey` — showdown evidence (signed pocket-key reveals included).
- `hand_state` — the **hand fossil**, see §7.2.
- `disputed_hands` — receipt anomalies (see §6).
- `known_identities` — TOFU-pinned Ed25519 pubkeys.

### 7.2 Hand fossil

The fossil is a single row keyed by `id_game` (INSERT OR REPLACE) containing everything the peer needs to resume mid-hand:

| Tag | Contents |
|---|---|
| `ORDER@` | b64-encoded nick list defining the ring permutation |
| `FULLMEGAPACKET@` | The cascaded deck after every peer's lock pass |
| `SRAKEYS@` / `SRAKEYS_COMMUNITY@` | The local peer's pocket / community unlocks |
| `BOTKEYS@`, `BOTKEYS_COMMUNITY@`, `BOTVISUAL@` | Equivalents for any local bots in the ring |
| `POCKETS@` | Single-locked pocket residuals received from peers (for showdown verification) |
| `VISUAL@` | The local peer's visible hole cards |

Source: [`Crupier.java`](../src/main/java/com/tonikelope/coronapoker/Crupier.java) — `guardarFosilSRA`. The fossil never contains another peer's pocket scalars, so reading it does not leak anything that would not be revealed at showdown anyway.

### 7.3 Resume flow

On `continue-last-game`:

- The host reads SQLite, rebuilds dealer/SB/BB and re-derives blinds, and broadcasts a `RECOVERDATA` payload to every reconnecting client.
- Each client reads its own SQLite, loads its fossil (if any), re-injects the SRA state, then absorbs the persisted actions to rebuild the `HandStateChain` and verify signatures along the way.
- Recovery is **per-peer**: a late joiner who was never in the in-progress hand watches it as a passive observer (no cards dealt, no actions requested) and joins normally on the next hand. Cross-checking `preflop_players` from the host map against the local nick is what distinguishes a returning participant from an observer ([`Crupier.java`](../src/main/java/com/tonikelope/coronapoker/Crupier.java) `recuperarDatosClavePartida` client branch).
- The RECOVERDATA payload is deserialized with a strict `ObjectInputFilter`: only `HashMap`, `String`, `Integer`, `Long`, `Float`, `Double`, `Boolean`; 10 MB max; 20 levels deep; 10 000 array elements max. This blocks classic Java deserialization gadget chains.

---

## 8. SECURITY_LOCKDOWN

The client distinguishes two severities. Both **presume good faith**: an anomaly could be a software bug, not necessarily an attack, so the client never accuses anyone — but it protects the user.

### 8.1 Hard lockdown (fatal)

`triggerSecurityLockdown` ([`Crupier.java`](../src/main/java/com/tonikelope/coronapoker/Crupier.java)) ends the hand and **freezes** the moment a *fatal* anomaly fires — one where continuing could move chips against a manipulated state:

- A received deck point is invalid (off-group / non-canonical Ristretto encoding).
- A genesis-deck card resolution fails — somebody returned a point the deck does not contain.
- A **verifiable-dealing chain fails**: a de-lock step does not anchor to the committed `MEGAPACKET` point or carries an invalid DLEQ proof (§2.5).
- A peer is asked to de-lock a point inside its own pocket, or a community de-lock resolves to genesis (extraction — §2.5).
- An Ed25519 signature on an action, community reveal or showdown reveal does not verify.
- A community-card announcement disagrees with the value re-derived from the unlocks.
- An "early cascade" attack is detected (a peer/host trying to advance the cascade or reveal a street out of order).

It sets a `volatile` flag, blacklists the offending peer at the command-dispatch layer, closes the client socket, shows a critical popup that **recommends leaving the table**, and ends the hand for this peer. The reason string is logged at SEVERE so post-mortem is trivial.

### 8.2 Soft warning (non-fatal — warn but continue)

`warnSuspiciousHost` ([`Crupier.java`](../src/main/java/com/tonikelope/coronapoker/Crupier.java)) is used when the anomaly has **already been neutralised by refusing the operation** and the game can safely continue — freezing would be disproportionate if it turned out to be a bug. It informs the user **once**, presuming good faith, and **strongly recommends leaving the table**, but does **not** freeze, close the socket or end the hand. Current use: a **repeated rotation** in the same cascade (anti-replay, §2.2/§2.5) — the extra rotation is refused (the oracle gets nothing) and play continues with the legitimate one.

**Policy: warn but continue, unless the fault is fatal — then lock down.**

---

## 9. What this does NOT defend

For honesty's sake:

- **Reading the pocket of a player who abandons mid-hand (rotation residual).** The dual-lock rotation (§2.2 step 2, §2.5) is the one de-lock not yet under the verifiable chain — it precedes any committed anchor. A hostile host *could* feed a still-locked pocket into the rotation to read it, but only at a steep, self-defeating price: it requires corrupting the board, which produces an **unjustified misdeal** (legitimate misdeals are tightly enumerated, so an unexplained one is a red flag — the client warns and recommends leaving, §8.2); the rotation is **single-use per cascade** (anti-replay); and it only ever yields the cards of a player who has *already left the table*. Net: self-delation + a broken hand for information of marginal value — no rational cheat would do it. Full closure (a verifiable rotation anchored to a signed pre-rotation deck) is designed and the `RotationChain` engine is built and tested, but deferred as not worth the cost — see [`sra-phase4-3-design.md`](sra-phase4-3-design.md).
- **Local trojan on a peer's machine.** Anyone with read access to `~/.coronapoker/identity_*.ed25519` becomes that peer cryptographically.
- **Coercion / shoulder-surfing.** A peer who shows their screen to someone behind them is leaking pockets out-of-band.
- **N-1 collusion.** Pure consensus systems cannot detect this; the victim will see divergent receipts and can choose not to settle.
- **First-contact MITM without password.** TOFU has no anchor on first contact; the identicon dialog exists precisely to give you an OOB comparison option. Always pin a password for first-time games with new peers.
- **The optional Google Translate TTS** for fast-chat readout makes outbound requests to `translate.google.com`. Disable TTS if you want a fully air-gappable session.

---

## 10. Reading the code

If you want to follow any of the above end-to-end:

- SRA group + verifiable-dealing engine (Ristretto255): [`crypto/`](../src/main/java/com/tonikelope/coronapoker/crypto/) — `Fe25519`, `EdwardsPoint`, `Ristretto255`, `RistrettoSRA`, `Dleq`, `VerifiableUnlock`, `DealChain`, `RotationChain`
- Shuffle primitive: [`CryptoSRA.java`](../src/main/java/com/tonikelope/coronapoker/CryptoSRA.java) (`shuffleDeck`)
- Cascade orchestration, recovery, lockdown, soft warning: [`Crupier.java`](../src/main/java/com/tonikelope/coronapoker/Crupier.java)
- Channel encryption: [`Helpers.java`](../src/main/java/com/tonikelope/coronapoker/Helpers.java) (`encryptCommand`, `decryptCommand`, `deriveChannelSecret`)
- Identity layer: [`IdentityManager.java`](../src/main/java/com/tonikelope/coronapoker/IdentityManager.java), [`TOFUResolver.java`](../src/main/java/com/tonikelope/coronapoker/TOFUResolver.java)
- Action record format: [`CanonicalActionRecord.java`](../src/main/java/com/tonikelope/coronapoker/CanonicalActionRecord.java)
- Hash chain: [`HandStateChain.java`](../src/main/java/com/tonikelope/coronapoker/HandStateChain.java)
- Identicons: [`IdenticonDialog.java`](../src/main/java/com/tonikelope/coronapoker/IdenticonDialog.java), [`SessionIdenticonMosaicDialog.java`](../src/main/java/com/tonikelope/coronapoker/SessionIdenticonMosaicDialog.java)

Deepest spec (motivation, design rationale, wire encoding tables) for the identity layer: [`ec-identity-spec.md`](ec-identity-spec.md).
