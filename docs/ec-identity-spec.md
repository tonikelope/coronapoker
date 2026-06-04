# EC-Identity — Specification

Cryptographic identity, action signing and hand-integrity layer for CoronaPoker. This document describes the layer **as it ships today**, after the EC-SRA dual-lock and EC-Identity work were merged into master. For the higher-level security overview see [`SECURITY.md`](SECURITY.md); this file is the deeper reference for the identity, action-record, hash-chain and receipt machinery.

**Status**: Implemented (shipped).
**Author**: tonikelope

---

## 1. Threat model

This layer addresses vectors that the raw mental-poker cascade alone cannot detect.

### What this layer covers

| Vector | Defense |
|---|---|
| Hostile host substituting another player's identity | Ed25519 long-term keys + silent TOFU pinning + optional OOB fingerprint comparison via identicons |
| Hostile host rewriting hand history (action injection, reordering) | Per-action Ed25519 signatures + `H_t` hash chain with `PREV_H` binding |
| Network MITM on the ECDH handshake | `Helpers.deriveChannelSecret` (HMAC-SHA512 binding with the shared password) + session-key identicon for OOB compare (waiting room, right-click your own nick) |
| Cross-recipient board fork (host announces different community cards to different peers) | Per-recipient encrypted community pieces decoded locally, cross-checked against a host-signed `ACTION_COMMUNITY` reveal absorbed into every peer's `H_t` |
| Post-game disputes about what actions occurred | Final receipts signed with Ed25519 identity keys, archivable as evidence |

### What this layer explicitly does NOT cover

- **Local trojan / Ring-0 attacker on a victim's machine.** Private keys live under `<user.home>/.coronapoker/` and in JVM heap. A sufficiently privileged local attacker reads them. Out of scope.
- **MITM during the first encounter between peers that never met before.** TOFU has no anchor on first contact. Mitigation is **optional, opt-in** OOB fingerprint comparison via the identicon dialog. Never forced, never blocking.
- **Collusion of N-1 peers against 1 victim.** Inherent to any consensus without a trusted third party. The victim sees the divergent receipt in the consensus log and can refuse to settle informally.
- **Cross-device identity portability.** Each install generates its own keypair per nick. A player using the same nick on two machines will present a different pubkey on each — that is the natural and correct behavior.
- **Identification by pubkey for stats/recover.** Nick remains the identifier for SQLite stats and recovery. No changes to the existing stats/recover schema.
- **Chain recovery on mid-hand reconnection.** A peer that drops mid-hand and reconnects cannot rejoin the in-progress hand. Its absent receipt makes that hand close as `MISSING` for it. See §11.

### Discarded design alternatives (and why)

- **Constant-time `modInverse` in SRA**: only matters against a local trojan, where nothing Java-pure saves you.
- **Subgroup low-order point check in SRA**: unnecessary — Ristretto255 is a prime-order group (no small-order points), `RistrettoSRA.decode` rejects malformed points, and `RistrettoSRA.resolveCardIndex == -1` invalidates tampered hands.
- **MAC swarm (Panoptes-style)**: same MITM exposure during pubkey exchange, but loses non-repudiation.
- **Direct P2P topology for game commands**: too invasive a refactor. Star topology stays; the signing layer makes the host's broker role harmless.
- **Mutual nonce challenge at join**: replay protection is delegated to the `session_id` baked into the join self-signature — same effect, zero extra round-trips.
- **Blocking modal on TOFU pubkey mismatch**: deliberately not used (UX). TOFU updates silently (last-write-wins); the user inspects identicons if they care.
- **Anti-spoofing nick canonicalization (zero-width chars, bidi, lookalikes)**: irrelevant if nobody modifies the client source. Only real cross-platform variations (Unicode NFC vs NFD) are canonicalized.
- **Formal PAKE (SPAKE2/OPAQUE)**: the existing `HMAC-SHA512(password, ECDH)` binding is sufficient with a non-trivial password; an entropy warning is shown at game creation.

---

## 2. Identity layer

Source: [`IdentityManager.java`](../src/main/java/com/tonikelope/coronapoker/IdentityManager.java).

### 2.1 Keypair

An Ed25519 keypair is generated on first use of a given nick on a given machine.

- **Algorithm**: `Ed25519` (RFC 8032), via `KeyPairGenerator.getInstance("Ed25519")` (native in JDK 15+).
- **Binding**: **per-nick**, not per-install. The keypair is bound to the NFC-canonicalized nick. A `playerIdHex` slug = the first 16 hex chars (8 bytes / 64 bits) of `SHA-256(NFC(nick) UTF-8)`.
- **Storage** under `<user.home>/.coronapoker/`:
  - Private key: `identity_<player_id_hex>.ed25519`, PKCS#8 encoded.
  - Public key: sidecar `identity_<player_id_hex>.ed25519.pub`, 32 raw bytes (no header).
- **File permissions**:
  - POSIX: `rw-------` (`0600`) on the private key.
  - Windows: `icacls /inheritance:r /grant:r <user>:(F)` — strip inheritance, grant full control to the current user only.
  - The `.pub` sidecar is public by definition and gets no restrictive ACL.
- **At-rest encryption**: deliberately not done. FS permissions are the user's responsibility. A leaked privkey ⇒ delete the file and re-pair via TOFU.
- **No rotation**: deleting the file generates a fresh keypair next launch; peers see a silent TOFU pubkey change on next connect.
- **Fail-loud at use**: if `<user.home>/.coronapoker/` cannot be created or the keypair cannot be written/loaded, the manager records the error and reports `isReady() == false`. Networked code paths check `isReady()`; any attempt to `sign()` without a ready identity throws. The app does not fall back to an ephemeral in-memory identity for networked games.
- **Singleton**: `IdentityManager.initializeForNick(nick)` reuses the existing instance when the canonical nick is unchanged and swaps to a fresh per-nick instance when the nick changes.

### 2.2 Identity vs nick semantics

The nick identifies a **player**; the keypair is bound to that nick **on a specific installation**.

Implications, all expected and correct:

- Two players sharing one machine: two nicks, two distinct keypair files. `known_identities` has one row per nick.
- One player using two machines: the same nick yields a different keypair on each machine, so peers see the pubkey change when the player switches devices. `verified_oob` resets to 0 (unverified) silently.
- An impostor claiming another player's nick is accepted silently into TOFU, but per-action signatures expose them at their first action: they cannot produce a signature matching the genuine pinned pubkey.

### 2.3 Fingerprint format

Both representations derive from `SHA-256(pubkey)`:

- **Short** (8 hex chars / 32 bits): `a3f9-1c4b`. Compact use.
- **Full** (16 bytes / 128 bits, 8 groups of 4 hex separated by spaces): `a3f9 1c4b 7e2d 9faa 8c12 4456 ef78 1234`. For OOB comparison.

Exposed via `getShortFingerprint()` / `getFullFingerprint()`.

### 2.4 Identicons

Two distinct identicons exist, both rendered by [`IdenticonDialog.java`](../src/main/java/com/tonikelope/coronapoker/IdenticonDialog.java) on a `SHA-256` hash over a `7×7` grid with horizontal symmetry, two foreground colors drawn from disjoint hash bytes and a transparent background:

- **Session identicon** — hashes the negotiated session **AES key**; for network-MITM detection. Reachable from the waiting room by **right-clicking your own nick** in the participant list: a **client** opens the AES identicon of its single channel with the host; the **host** opens the per-client mosaic of every channel ([`SessionIdenticonMosaicDialog.java`](../src/main/java/com/tonikelope/coronapoker/SessionIdenticonMosaicDialog.java)).
- **Identity identicon** — hashes the peer's **Ed25519 pubkey**; for identity OOB verification, with the full fingerprint hex shown in the title and a "Verify identity" button. Reachable at the table by clicking a human player's avatar.

---

## 3. Handshake protocol

Identity is folded into the **existing waiting-room join payload** — there is no separate `JOIN_IDENTITY` command. Source: [`WaitingRoomFrame.java`](../src/main/java/com/tonikelope/coronapoker/WaitingRoomFrame.java).

### Join payload

After the ECDH session-key handshake, the connecting peer sends (over the already-encrypted channel) a 6-field payload:

```
<nick_b64> # <version> # <avatar_b64_or_*> # JOIN_V1 # <pubkey_b64> # <self_sig_b64>

self_sig = Ed25519.sign(privkey, "JOIN_V1\0" || session_id || nick_canonical_utf8 || pubkey)
```

Where:

- `JOIN_V1` is the literal marker field that flags this as an identity-bearing join.
- `pubkey_b64` is the 32-byte raw Ed25519 public key.
- `session_id` is 16 random bytes generated by the host (CSPRNG) at waiting-room construction and shipped to every client inside the ECDH handshake. All peers know this value.
- `nick_canonical_utf8` is `Normalizer.normalize(nick, NFC).getBytes(UTF_8)`.

The Ed25519 domain `"JOIN_V1\0"` is prepended to the signed message (the manager calls `Signature.update(domain)` then `Signature.update(data)`), as for every other signing context.

### Replay protection by `session_id`

Each game has a fresh `session_id`. A `self_sig` is valid only for the session whose `session_id` it encodes; replaying a join from a previous session fails verification against the current `session_id`. A failed self-sig is rejected with the same response as a version mismatch, to avoid giving an attacker an oracle.

### Version gate

Compatibility is enforced by **strict equality** of the client's `version` field against the host's `AboutDialog.VERSION`. A mismatch returns `BADVERSION#<host_version>` and the join is refused. There is no min-compatible range; any wire-incompatible change is gated simply by bumping the version string. (V1 receipts without the flags byte, for instance, are wire-incompatible with the current `RECEIPT_V2` format — §6.)

### TOFU resolution (silent, no user interaction)

After verifying the self-sig, each peer resolves `(nick, pubkey)` against its local `known_identities` ([`TOFUResolver.java`](../src/main/java/com/tonikelope/coronapoker/TOFUResolver.java)):

| Case | DB operation | `verified_oob` |
|---|---|---|
| `NEW` — unknown nick | `INSERT` row, `sessions_count=1`, `verified_oob=0` | 0 (unverified) |
| `MATCH` — known nick, pubkey byte-identical | `UPDATE last_seen`, `sessions_count++`; `verified_oob` untouched | unchanged (0 or 1) |
| `CHANGED` — known nick, pubkey differs | `UPDATE pubkey`, `last_seen`, `sessions_count++`, **`verified_oob = 0`** (last-write-wins) | reset to 0 |

**No blocking modal.** On `CHANGED` the new pubkey silently replaces the old one (a `WARNING` is logged); the user only discovers it by opening the identity identicon dialog, which reflects the current verification state. There is no passive at-a-glance indicator.

### Manual verification

The identity identicon dialog offers a **"Verify identity"** button. Clicking it runs `UPDATE known_identities SET verified_oob = 1 WHERE nick = ?` (guarded by a byte-exact check that the stored pubkey still matches the one being verified). `markVerified` / `isVerified` persist and read this flag; the dialog then shows "✓ Identity verified" instead of the button, and the flag stays set across sessions until the pubkey changes (which resets it to 0).

---

## 4. Canonical action record

Every hand-action mutation is serialized to a flat 92-byte record before being signed and absorbed into the hash chain. Source: [`CanonicalActionRecord.java`](../src/main/java/com/tonikelope/coronapoker/CanonicalActionRecord.java).

### 4.1 Layout (big-endian, no padding)

```
Offset  Size  Field           Type        Notes
------  ----  --------------  ----------  ----------------------------------------
  0     32   PREV_H          byte[32]    H_{t-1} — pins this action's position
 32     16   HAND_ID         byte[16]    Random bytes generated by host at hand start
 48     32   PLAYER_ID       byte[32]    SHA-256(NFC-normalized nick UTF-8)
 80      1   STREET          uint8       Wire enum (§4.3)
 81      1   ACTION_TYPE     uint8       Wire enum (§4.3)
 82      8   AMOUNT_CENTS    int64 BE    Bet/raise amount in cents. 0 for FOLD/CHECK.
                                          Repurposed to packed card ordinals for
                                          ACTION_COMMUNITY (§4.7).
 90      2   FLAGS           uint16 BE   bit0 = is_allin, bit1 = is_voluntary, rest = 0
                                          Total: 92 bytes
```

### 4.2 Canonicalization rules — protection against JVM/OS variations

All defensive against honest cross-platform bugs, none against malicious source-modified clients.

1. **NFC nick normalization**:
   ```java
   nick_utf8 = Normalizer.normalize(nick, Normalizer.Form.NFC).getBytes(StandardCharsets.UTF_8);
   ```
   Defends against precomposed vs decomposed Unicode (macOS vs Windows). Identity for ASCII nicks.

2. **Float-to-cents conversion**:
   ```java
   amount_cents = Math.round((double) amount_float * 100.0);
   ```
   Widening to double before multiplication kills `0.1f + 0.2f` jitter. Single canonical implementation; reproducing the layout elsewhere is an integrity bug.

3. **Locale-independent parsing**: `Float.parseFloat` / `Long.parseLong` are already locale-independent; any `NumberFormat` for amounts must use `Locale.ROOT`.

4. **Encoder as single source of truth**: `CanonicalActionRecord.encode(...)` is the only path that produces the 92-byte buffer, with strict argument validation and fail-loud on out-of-range inputs.

### 4.3 Wire enum (independent of Java internal constants)

```
STREET (uint8):       ACTION_TYPE (uint8):
  0 = preflop           0 = FOLD
  1 = flop              1 = CHECK
  2 = turn              2 = CALL
  3 = river             3 = BET
  4 = showdown          4 = RAISE
                        5 = ALLIN
                        6 = COMMUNITY   (§4.7)
```

Translation Java↔wire is isolated in `HandStateChain` and `CanonicalActionRecord`, so refactoring the Java enums never moves the wire byte.

### 4.4 Per-action signature

```
sig_t = Ed25519.sign(player_privkey, "ACTION_V1\0" || record_t)
```

### 4.5 Departed peers and the `is_voluntary` bit

When a peer leaves mid-hand the host broadcasts `EXIT`, every receiver flips `Participant.isExit`, and on the next betting iteration to that slot a local FOLD synth is produced with `record = sig = null` and `is_voluntary = 0`. That slot's wire `ACTION` broadcast and chain `absorb` are both skipped: **no record is contributed for that slot on any peer**, and the chain converges by mutual omission.

Consequently `is_voluntary = 1` is the only value that travels on the wire today (humans signing their own actions; bot actions signed by the host — §10). `is_voluntary = 0` is **reserved**: it is set on the in-memory synth but never reaches the wire and never lands in `H_t`.

Honest player timeouts are resolved client-side: each peer's local `auto_action` timer auto-clicks CHECK/FOLD and sends a regular signed `ACTION` (`is_voluntary = 1`). There is no host-side timeout autofold.

### 4.6 Receiver verification table

| FLAGS.is_voluntary | Actor type | Expected signer pubkey |
|---|---|---|
| 1 | Human | Actor's own pinned pubkey |
| 1 | Bot (`Participant.isCpu()`) | **Host's** pinned pubkey (§10) |
| 0 | — | Reserved (never on wire; reject if seen) |

### 4.7 Host-signed community card reveals (EC-SRA v3)

Community cards are never sent in the clear. After the SRA cascade and the community rotation pass (see [`SECURITY.md`](SECURITY.md) §2.2), each community slot is locked **only** under each peer's `k_community`. Per street, the host:

1. Sends every ring member its **own per-recipient encrypted piece** — `FLOP_PIECE` / `TURN_PIECE` / `RIVER_PIECE` (and `RABBIT_*_PIECE` for rabbit hunting), wire-shaped as `<PIECE>#<recipient_nick_b64>#<payload_b64>`. The recipient applies its `local_sra_unlock_community` to decode the piece **locally** and maps each 32-byte chunk to a card index via `RistrettoSRA.resolveCardIndex`.
2. Broadcasts a single signed announcement to everyone:
   ```
   COMM_REVEAL # <record_b64> # <sig_b64>
   ```
   The record is a canonical `ACTION_COMMUNITY` record:
   - `PLAYER_ID = SHA-256(NFC(host_nick))`
   - `STREET` = the street being revealed (flop=1, turn=2, river=3)
   - `AMOUNT_CENTS` = the revealed card ordinals packed one byte per card, little-endian within the field (`packCommunityCards`, 1–3 cards)
   - `FLAGS.is_voluntary = 1`
   - `sig_t = Ed25519.sign(host_privkey, "ACTION_V1\0" || record_t)`

Each ring peer verifies the host signature, then **cross-checks the announced ordinals against the indices it decoded from its own piece**; any mismatch is a cross-recipient fork and triggers `SECURITY_LOCKDOWN`. Only after the cross-check passes is the `(record, sig)` pair absorbed into `H_t` (with the host as the actor). Because every peer absorbs the identical signed announcement, a host that announces different boards to different peers produces divergent receipts and is caught both in real time (lockdown on mismatch vs. the local piece) and post-hand (receipt divergence).

A peer also drops any `COMM_REVEAL` whose `STREET` doesn't match the street it is currently draining, to avoid stale-street replay. Pure observers (not in the ring, no piece to decode) paint the board from the signed announcement but do not absorb into a chain.

### 4.8 Events NOT in the chain

| Event | Handling |
|---|---|
| Showdown card reveal | Transported outside the 92-byte record (carries `k_pocket` / cards). It **is** individually signed under the `"SHOWDOWN_V1"` domain (`HAND_ID \|\| nick \|\| k_pocket`) and cross-checked, but lives outside `H_t`. |
| EXIT | Session-level event on the regular encrypted/HMAC'd channel (no per-event Ed25519 signature). A forged EXIT desyncs the spoofed peer's chain against a missing slot — still detectable post-hand; OOB identity verification is the actual defence against host impersonation. |
| REBUY | Between hands; doesn't affect the current `H_t`. |

### 4.9 Wire encoding of a signed action

```
GAME # <hand_seq> # <ACTION_TYPE_STR> # <nick> # <amount_str> # <record_b64> # <sig_b64>
```

The human-readable fields (`hand_seq`, `ACTION_TYPE_STR`, `nick`, `amount_str`) are retained for logs and parser compatibility. **Only `<record_b64>` and `<sig_b64>` are cryptographically meaningful** — they are the canonical values fed to the chain and verifier.

---

## 5. Hash chain `H_t`

Source: [`HandStateChain.java`](../src/main/java/com/tonikelope/coronapoker/HandStateChain.java).

### 5.1 Initial state `H_0`

Each peer computes locally after `MEGAPACKET` processing — no broadcast:

```
H_0 = SHA-256(
        "HAND_V2\0"                          ||  //  8B domain separator
        HAND_ID                              ||  // 16B random from host
        num_players (uint8)                  ||  //  1B
        per peer, sorted by id:                  // 96B × N
          PLAYER_ID(32) || K_pocket(32) || K_community(32)
        || SHA-256(cascaded_deck_bytes)          // 32B deck commitment
      )
```

Each peer's block — its `PLAYER_ID` (`SHA-256(nick_canonical_utf8)`) followed by its per-hand commitments `K_pocket = k_pocket·B` and `K_community = k_community·B` (Ristretto255 encodings) — is sorted by `PLAYER_ID` as a 32-byte unsigned integer, so `H_0` is identical across peers regardless of join order. Binding the `K` commitments here (**HAND_V2**) is what the verifiable dealing checks its DLEQ de-lock proofs against (see [`SECURITY.md`](SECURITY.md) §2.5); the **HAND_V1** layout without them remains as a compatibility fallback. The deck commitment binds the chain to the exact cascade permutation: peers that walked a different cascade diverge on the very first absorb.

### 5.2 Per-action ratchet

```
H_{t+1} = SHA-256(record_t || sig_t)
```

The signature is included so that verifying `H_final` implicitly verifies that each constituent action was correctly signed.

Intermediate `H_t` values are **not** broadcast in production; the chain is verified only at hand close, via receipts (§6). A development-only assertion mode can broadcast and compare intermediate `H_t` across peers, but it is never enabled in release builds (broadcasting intermediate values would correlate chain state with public actions).

---

## 6. Receipt and consensus

Replaces the no-op `HANDVERIFY` semantics; the command name `HANDVERIFY` is **kept** with a dual-form payload. Source: [`Crupier.java`](../src/main/java/com/tonikelope/coronapoker/Crupier.java), [`IdentityManager.java`](../src/main/java/com/tonikelope/coronapoker/IdentityManager.java).

### 6.1 Wire form

```
Host → all:      SERVER # <dest> # HANDVERIFY                       (no payload — trigger)
Each peer → all: SERVER # <dest> # HANDVERIFY # <nick_b64> # <receipt_b64>
```

The parser distinguishes the two by field count.

### 6.2 Receipt format

```
receipt = HAND_ID(16) || H_final(32) || flags(1) || sig(64)        = 113 bytes

sig = Ed25519.sign(my_privkey, "RECEIPT_V2\0" || HAND_ID || H_final || flags)
```

- `flags.bit0` (`RECEIPT_FLAG_BIT_INVALID_SIG_SEEN`) is set when the issuer observed at least one invalid Ed25519 action signature during the hand.
- `flags.bit1` (`RECEIPT_FLAG_BIT_DECK_UNVERIFIED`) is set when the issuer could not confirm the honest-shuffle proof (`DUALLOCK_BUNDLE`) for this hand's deck.
- `flags.bit2` (`RECEIPT_FLAG_BIT_NO_SHUFFLE_PROOF`) qualifies bit1: set only when a proof was expected (fresh deal) and none arrived — host withholding, as opposed to a slow local verifier. See [`SECURITY.md`](SECURITY.md) §6 for the full bit1/bit2 policy.

### 6.3 Consensus check

After collecting receipts (timeout reused from master), each peer's `runConsensusCheck` classifies every expected peer and reports OK only when **all three** hold:

1. Every receipt signature verifies against the claimed signer's pinned pubkey (and the receipt's `HAND_ID` matches the local hand).
2. Every `H_final` is byte-identical to the local `H_final`.
3. No receipt has any `flags` bit set anywhere on the table.

The outcomes, in descending priority (only the strongest is surfaced):

| Outcome | Meaning | Severity | `disputed_hands` |
|---|---|---|---|
| `DIVERGENT` | a receipt's sig fails or its `H_final` differs | SEVERE (interpreted as host manipulation) | `reason='DIVERGENT'` |
| `MISSING` | a peer's receipt is absent / wrong length / stale `HAND_ID` / pubkey unavailable | WARNING (ambiguous: network or crash) | `reason='MISSING'` |
| `INVALID_SIG_SEEN` | all sigs valid and `H_final`s match, but some peer flagged an invalid action sig (bit0) | SEVERE | `reason='INVALID_SIG_SEEN'` |
| `DECK_NO_PROOF` | otherwise clean, but some peer never received the shuffle proof (bit1+bit2) — host may be withholding it | WARNING (popup to the table) | `reason='DECK_NO_PROOF'` |
| `DECK_UNVERIFIED` | otherwise clean, but some peer's proof is still verifying (bit1 alone, slow peer) | WARNING (silent: JUL + row, no popup) | `reason='DECK_UNVERIFIED'` |
| `OK` | all present, unanimous, no flag bit set | INFO | No |

**The hand always settles** in every case — consensus is **signaletic, not gating**. The existing `cancelarManoYDevolverApuestas` / `MISDEAL` flow is untouched and reserved for genuine SRA-level catastrophes.

#### Why divergence is essentially proof of a malicious host

Every action is individually Ed25519-signed by its emitter and the `H_t` chain is computed locally by each peer from the actions received over TCP. For two peers to reach byte-different `H_final`, the host must have sent different/omitted/reordered actions to different peers (or a serious relay bug did). TCP guarantees in-order delivery or breaks the connection, so packet loss manifests as a **missing** receipt, never a silently **divergent** one. Hence `DIVERGENT` is smoking-gun evidence of host manipulation; `MISSING` is ambiguous and treated more mildly.

#### Why not abort on divergence

Aborting would be a trivial griefing vector (any peer could rage-quit mid-river to cancel an unwanted hand). Settling and warning fits the friendly home-game model; humans at the table discuss offline, and the strong wording of the divergent alert makes the pattern impossible to ignore.

### 6.4 User-facing messaging

The Crupier log shows one of the verification outcomes at hand close; on divergence / invalid-sig / missing a modal `Helpers.mostrarMensajeInformativo` is shown (none on success), and the JUL `LOGGER` records INFO / WARNING / SEVERE accordingly. The translatable strings live under the `game.mano_verificada_consenso`, `game.mano_verificacion_divergente`, `game.mano_verificacion_jugador_ausente`, `game.popup_verificacion_firma_invalida`, `game.popup_verificacion_divergente` and `game.popup_verificacion_ausente` keys in `messages_es.properties` / `messages_en.properties`.

---

## 7. SQLite schema additions

All additive — no existing tables touched. Created with `CREATE TABLE IF NOT EXISTS` on startup ([`Helpers.java`](../src/main/java/com/tonikelope/coronapoker/Helpers.java)).

```sql
CREATE TABLE IF NOT EXISTS known_identities (
    nick           TEXT    PRIMARY KEY,
    pubkey         BLOB    NOT NULL,            -- 32 raw bytes Ed25519
    first_seen     INTEGER NOT NULL,            -- epoch seconds
    last_seen      INTEGER NOT NULL,
    sessions_count INTEGER NOT NULL DEFAULT 0,
    verified_oob   INTEGER NOT NULL DEFAULT 0   -- 1 if user clicked "Verify identity"
);

CREATE TABLE IF NOT EXISTS disputed_hands (
    id        INTEGER PRIMARY KEY,
    id_hand   INTEGER NOT NULL,
    timestamp INTEGER NOT NULL,                 -- epoch seconds
    receipts  BLOB    NOT NULL,                 -- concatenation of every receipt collected this hand
    local_h   BLOB    NOT NULL,                 -- our local H_final at dispute time
    reason    TEXT,                             -- 'MISSING' or 'DIVERGENT'
    FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE
);
```

The `receipts` blob is stored **as collected** (the receipts are already signed; they are not separately encrypted at rest).

---

## 8. Wire protocol summary

| Command | Direction | Notes |
|---|---|---|
| Join payload with `JOIN_V1` marker field | New peer → host (relayed) | Identity announcement folded into the existing join; self-sig binds `session_id` |
| `GAME # … # <record_b64> # <sig_b64>` | Player → host → all | Signed canonical action |
| `<PIECE> # <nick_b64> # <payload_b64>` | Host → each recipient | Per-recipient encrypted community piece (`FLOP/TURN/RIVER_PIECE`, `RABBIT_*_PIECE`) |
| `COMM_REVEAL # <record_b64> # <sig_b64>` | Host → all | Signed community-card announcement, absorbed into `H_t` |
| `HANDVERIFY` (no payload) | Host → all | Consensus trigger |
| `HANDVERIFY # <nick_b64> # <receipt_b64>` | Each peer → all | Signed end-of-hand receipt |

Wire-incompatible changes are gated by the strict `AboutDialog.VERSION` equality check at join (§3).

---

## 9. UI summary

| Where | Element | Behavior |
|---|---|---|
| Table, `LocalPlayer` (human) | Clickable avatar | Opens the identity identicon of own pubkey + full fingerprint + "Verify identity". No passive verification indicator on the table. |
| Table, `RemotePlayer` (human) | Clickable avatar | Opens the identity identicon of the peer's pubkey + fingerprint + "Verify identity" (sets `verified_oob=1` for that `(nick, pubkey)`). No passive verification indicator on the table. |
| Table, `RemotePlayer` (bot) | Avatar click is a no-op | Bots have no identity; the avatar click does nothing for them. |
| Waiting room, own nick (right-click) | Session-key identicon | Client opens the AES identicon of its channel with the host; host opens the per-client session-identicon mosaic (`SessionIdenticonMosaicDialog`). |
| `NewGameDialog` (host) | Non-blocking warning | If estimated password entropy `< 60 bits`, warn; the host may proceed. |
| Crupier log / modal at hand close | Verification outcome | `✓ verified` / `⚠ divergent` / `⚠ missing`; modal only on a non-OK outcome. |

---

## 10. Bot identity

**Bots have no cryptographic identity of their own.** The host operates each bot and **signs bot actions with the host's own Ed25519 private key**. Receivers therefore verify a bot's actions against the **host's** pinned pubkey (the signer resolution maps any `Participant.isCpu()` actor to the host's identity). Bots are not inserted into `known_identities` and expose no identity affordance in the UI (their avatar click is a no-op).

A malicious host can of course abuse the bots it operates, but every bot action still carries the host's signature and lands in `H_t` like any other action, so it is independently verifiable and attributable to the host in the chain.

### Host identity is a regular peer

The host has the same kind of per-nick Ed25519 identity as any other peer, is stored in `known_identities` like everyone else, and signs its own actions like everyone else. Its only cryptographic specialisations are:

- Signing community-card reveals (`ACTION_COMMUNITY`, §4.7) so every peer absorbs identical reveal records into `H_t`.
- Signing the actions of the bots it operates (above).

No special "host pubkey" exists in the protocol: host == player + extra responsibilities.

---

## 11. Future work

1. **Cross-device identity import/export** via an encrypted, passphrase-protected bundle.
2. **Identity revocation broadcast** — a signed revocation that forces peers to re-pin on next encounter.
3. **Hardware-backed identity storage** (macOS Keychain, Windows DPAPI, Linux libsecret).
4. **At-rest encryption** of the private key file with a user passphrase.
5. **Standalone verifier tool** that takes a `disputed_hands.receipts` blob + a list of pubkeys, verifies signatures and prints the chain.
6. **Threshold receipts** for very large meshes (relaxing strict unanimity beyond some table size).
7. **Chain replay on mid-hand reconnection** so a reconnecting peer can rejoin and sign a receipt for the in-progress hand (today that hand closes as `MISSING` for them and the next hand starts clean).

---

## 12. Glossary

- **TOFU** — Trust On First Use. Accept a key the first time, pin it. SSH-style.
- **PAKE** — Password-Authenticated Key Exchange. Authenticate with a shared password without revealing it.
- **Domain separator** — Unique string prefix in every signature so a signature for one purpose cannot be replayed in another (`ACTION_V1`, `RECEIPT_V2`, `SHOWDOWN_V1`, `JOIN_V1`, plus the `HAND_V2` chain domain — `HAND_V1` as compatibility fallback).
- **Ratchet** — One-way state update where each step depends on the previous; reordering is impossible without breaking the chain.
- **Receipt** — Signed commitment by a peer to a final chain state, archivable as evidence.
- **OOB (Out-of-Band)** — A channel separate from the system being secured (e.g. a phone call to compare a fingerprint shown in the UI).
- **NFC / NFD** — Unicode Normalization Forms (Composed / Decomposed); the same logical character can be byte-different across systems unless normalized.
