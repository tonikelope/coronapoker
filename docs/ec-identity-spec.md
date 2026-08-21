# Identity: Specification

Cryptographic identity, action signing and hand-integrity layer for CoronaPoker. For the higher-level security overview see [`SECURITY.md`](SECURITY.md). This file is the deeper reference for the identity, action-record, hash-chain and receipt machinery.

All peers in a game run the same exact version (the handshake refuses any other, §3), so the protocol is single-version throughout: there are no compatibility modes or fallbacks.

---

## 1. Threat model

This layer addresses vectors that the raw mental-poker cascade alone cannot detect.

### What this layer covers

| Vector | Defense |
|---|---|
| Hostile host substituting another player's established identity | Ed25519 long-term keys + TOFU pinning; a changed key is reported as `CHANGED`, replaces the stored pin and clears OOB verification, so continuity requires an OOB fingerprint comparison |
| Hostile host rewriting hand history (action injection, reordering) | Per-action Ed25519 signatures + `H_t` hash chain with `PREV_H` binding |
| Hostile host or peer reporting a false pot payout | Each peer recomputes and conserves the settlement independently, binds it into `H_final`, and refuses durable close when receipts diverge (§5.3, §6) |
| Network MITM on the ECDH handshake | `Helpers.deriveChannelSecret` (HMAC-SHA512 binding with the shared password) + session-key identicon for OOB compare (waiting room, right-click your own nick) |
| Cross-recipient board fork (host announces different community cards to different peers) | Per-recipient encrypted community pieces decoded locally, cross-checked against a host-signed `ACTION_COMMUNITY` reveal absorbed into every peer's `H_t` |
| Post-game disputes about what actions occurred | Final receipts signed with Ed25519 identity keys, archivable as evidence |

### What this layer explicitly does NOT cover

- **Local trojan / Ring-0 attacker on a victim's machine.** Private keys live under `<user.home>/.coronapoker/` and in JVM heap. A sufficiently privileged local attacker reads them. Out of scope.
- **MITM during the first encounter between peers that never met before.** TOFU has no anchor on first contact. Mitigation is **optional, opt-in** OOB fingerprint comparison via the identicon dialog. Never forced, never blocking.
- **Collusion of N-1 peers against 1 victim.** Inherent to any consensus without a trusted third party. Divergent/missing receipts automatically block durable close, but colluders can still deny service or present one consistent dishonest view.
- **Cross-device identity portability.** Each install generates its own keypair per nick. A player using the same nick on two machines will present a different pubkey on each. That is the natural and correct behavior.
- **Identification by pubkey for stats/recover.** Nick remains the logical identity for SQLite stats and recovery. The balance table is migrated to exactly one row per `(id_hand, player)`.
- **Chain recovery on mid-hand reconnection.** A peer that drops mid-hand and reconnects cannot rejoin the in-progress hand. An absent receipt is classified `MISSING` and prevents durable close/advance.

---

## 2. Identity layer

Source: [`IdentityManager.java`](../src/main/java/com/tonikelope/coronapoker/IdentityManager.java).

### 2.1 Keypair

An Ed25519 keypair is generated on first use of a given nick on a given machine.

- **Algorithm**: `Ed25519` (RFC 8032), via `KeyPairGenerator.getInstance("Ed25519")` (native in JDK 15+).
- **Binding**: **per-nick**, not per-install. The keypair is bound to the canonicalized nick (`IdentityManager.canonicalNick`: surrounding whitespace trimmed, then NFC-normalized). A `playerIdHex` slug = the first 16 hex chars (8 bytes / 64 bits) of `SHA-256(NFC(trim(nick)) UTF-8)`.
- **Storage** under `<user.home>/.coronapoker/`:
  - Private key: `identity_<player_id_hex>.ed25519`, PKCS#8 encoded.
  - Public key: sidecar `identity_<player_id_hex>.ed25519.pub`, 32 raw bytes (no header).
- **File permissions**:
  - POSIX: `rw-------` (`0600`) on the private key.
  - Windows: `icacls /inheritance:r /grant:r <user>:(F)`: strip inheritance, grant full control to the current user only.
  - The `.pub` sidecar is public by definition and gets no restrictive ACL.
- **At-rest encryption**: deliberately not done. FS permissions are the user's responsibility. A leaked privkey ⇒ delete the file and treat the next encounter as a fresh TOFU transition (`CHANGED` for peers that still have the old pin).
- **No automatic rotation while the key files exist**: deleting the file generates a fresh keypair next launch. Peers with an existing pin accept the changed key, replace their stored pin and clear `verified_oob`; users who need continuity must verify the new fingerprint out of band.
- **Fail-loud at use**: if `<user.home>/.coronapoker/` cannot be created or the keypair cannot be written/loaded, the manager records the error and reports `isReady() == false`. Networked code paths check `isReady()`. Any attempt to `sign()` without a ready identity throws. The app does not fall back to an ephemeral in-memory identity for networked games.
- **Singleton**: `IdentityManager.initializeForNick(nick)` reuses the existing instance when the canonical nick is unchanged and swaps to a fresh per-nick instance when the nick changes.

### 2.2 Identity vs nick semantics

The nick identifies a **player**. The keypair is bound to that nick **on a specific installation**.

Implications, all expected and correct:

- Two players sharing one machine: two nicks, two distinct keypair files. `known_identities` has one row per nick.
- One player using two machines: the same nick yields a different keypair on each machine, so peers with an existing pin record `CHANGED`, replace the stored key and clear `verified_oob`; they do not reject the second machine automatically.
- An impostor claiming an already-pinned nick can pass the cryptographic JOIN self-signature with its own key and is admitted as `CHANGED`; only an OOB comparison can establish that the new key is really the expected one. First contact remains ordinary TOFU and therefore still needs password/OOB verification when that risk matters.

### 2.3 Fingerprint format

Both representations derive from `SHA-256(pubkey)`:

- **Short** (8 hex chars / 32 bits): `a3f9-1c4b`. Compact use.
- **Full** (16 bytes / 128 bits, 8 groups of 4 hex separated by spaces): `a3f9 1c4b 7e2d 9faa 8c12 4456 ef78 1234`. For OOB comparison.

Exposed via `getShortFingerprint()` / `getFullFingerprint()`.

### 2.4 Identicons

Two distinct identicons exist, both rendered by [`IdenticonDialog.java`](../src/main/java/com/tonikelope/coronapoker/IdenticonDialog.java) on a `SHA-256` hash over a `7×7` grid with horizontal symmetry, two foreground colors drawn from disjoint hash bytes and a transparent background:

- **Session identicon**: hashes the negotiated session **AES key**, for network-MITM detection. Reachable from the waiting room by **right-clicking anywhere in the participant list** (the opened dialog does not depend on where the click lands): a **client** opens the AES identicon of its single channel with the host. The **host** opens the per-client mosaic of every channel ([`SessionIdenticonMosaicDialog.java`](../src/main/java/com/tonikelope/coronapoker/SessionIdenticonMosaicDialog.java)).
- **Identity identicon**: hashes a peer's **Ed25519 pubkey**, for identity OOB verification, with the full fingerprint hex shown in the title. Reachable at the table by clicking a human player's avatar. Clicking a **remote** human's avatar shows the "Verify identity" button; clicking **your own** avatar shows the same identicon and fingerprint with a copy-to-share hint instead, since there is no peer to verify against.

---

## 3. Handshake protocol

Identity is folded into the **existing waiting-room join payload**. There is no separate `JOIN_IDENTITY` command. Source: [`WaitingRoomFrame.java`](../src/main/java/com/tonikelope/coronapoker/WaitingRoomFrame.java).

### Join payload

After the ECDH session-key handshake, the connecting peer sends (over the already-encrypted channel) a 6-field payload:

```
<nick_b64> # <version> # <avatar_b64_or_*> # JOIN # <pubkey_b64> # <self_sig_b64>

self_sig = Ed25519.sign(privkey, "JOIN\0" || session_id || nick_canonical_utf8 || pubkey)
```

Where:

- `JOIN` is the literal marker field that flags this as an identity-bearing join.
- `pubkey_b64` is the 32-byte raw Ed25519 public key.
- `session_id` is 16 random bytes generated by the host (CSPRNG) at waiting-room construction and shipped to every client inside the ECDH handshake. All peers know this value.
- `nick_canonical_utf8` is `Normalizer.normalize(nick, NFC).getBytes(UTF_8)`.

The Ed25519 domain `"JOIN\0"` is prepended to the signed message (the manager calls `Signature.update(domain)` then `Signature.update(data)`), as for every other signing context.

### Replay protection by `session_id`

Each game has a fresh `session_id`. A `self_sig` is valid only for the session whose `session_id` it encodes. Replaying a join from a previous session fails verification against the current `session_id`. A cryptographically invalid self-sig closes the socket silently with no reply at all (`WaitingRoomFrame.verifyJoinSelfSig`), so a replayed or tampered join gets no distinguishing message back. The one join failure that does answer, with `BADVERSION#<host_version>` exactly as a version mismatch would, is a structurally malformed JOIN (wrong field count or missing `JOIN` marker field).

### Version gate

Compatibility is enforced by **strict equality** of the client's `version` field against the host's `AboutDialog.VERSION`. A mismatch returns `BADVERSION#<host_version>` and the join is refused. There is no min-compatible range, so every peer in a game runs the identical wire format. Any change to it is gated simply by bumping the version string.

### TOFU resolution (silent, non-blocking)

After verifying the self-sig, each peer resolves `(nick, pubkey)` against its local `known_identities` ([`TOFUResolver.java`](../src/main/java/com/tonikelope/coronapoker/TOFUResolver.java)):

| Case | DB operation | `verified_oob` |
|---|---|---|
| `NEW`: unknown nick | `INSERT` row, `sessions_count=1`, `verified_oob=0` | 0 (unverified) |
| `MATCH`: known nick, pubkey byte-identical | `UPDATE last_seen`, `sessions_count++`. `verified_oob` untouched | unchanged (0 or 1) |
| `CHANGED`: known nick string, pubkey differs | `UPDATE pubkey, last_seen, sessions_count, verified_oob=0`; return `CHANGED` | 0 (the new key must be checked OOB again) |

**TOFU is not an admission gate, and there is no modal.** The host-side initial `JOIN` requires the six-field payload and a valid `JOIN` self-signature; after that structural/signature check, the participant is admitted and the `NEW`/`MATCH`/`CHANGED` result is logged. On a client, server intro and `NEWUSER`/`USERSLIST` roster entries are admitted by their separate capacity, exact/NFC-collision and avatar checks. Identity fields are verified when present, but missing/malformed self-signatures and TOFU outcomes are logged without closing the channel or removing the roster entry. The resolver indexes the nick string it receives; `IdentityManager` still NFC-normalizes the local key binding and JOIN signature, but `TOFUResolver` does not perform legacy alias migration. There is still no passive at-a-glance indicator.

### Manual verification

The identity identicon dialog offers a **"Verify identity"** button. Clicking it runs `UPDATE known_identities SET verified_oob = 1 WHERE nick = ?` (guarded by a byte-exact check that the stored pubkey still matches the one being verified). `markVerified` / `isVerified` persist and read this flag. The dialog then shows "✓ Identity verified" instead of the button. A later `CHANGED` transition replaces the stored pubkey and clears `verified_oob`, so the new key must be checked again.

---

## 4. Canonical action record

Every hand-action mutation is serialized to a flat 92-byte record before being signed and absorbed into the hash chain. Source: [`CanonicalActionRecord.java`](../src/main/java/com/tonikelope/coronapoker/CanonicalActionRecord.java).

### 4.1 Layout (big-endian, no padding)

```
Offset  Size  Field           Type        Notes
------  ----  --------------  ----------  ----------------------------------------
  0     32   PREV_H          byte[32]    H_{t-1}, pins this action's position
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

### 4.2 Canonicalization rules: protection against JVM/OS variations

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
   Widening to double before multiplication kills `0.1f + 0.2f` jitter. Single canonical implementation. Reproducing the layout elsewhere is an integrity bug.

3. **Locale-independent parsing**: `Float.parseFloat` / `Long.parseLong` are already locale-independent. Any `NumberFormat` for amounts must use `Locale.ROOT`.

4. **Encoder as single source of truth**: `CanonicalActionRecord.encode(...)` is the only path that produces the 92-byte buffer, with strict argument validation and fail-loud on out-of-range inputs.

### 4.3 Wire enum (independent of Java internal constants)

```
STREET (uint8):       ACTION_TYPE (uint8):
  0 = preflop           0 = FOLD
  1 = flop              1 = CHECK
  2 = turn              2 = CALL      (reserved, never emitted)
  3 = river             3 = BET
  4 = showdown          4 = RAISE     (reserved, never emitted)
  11 = rit2_flop        5 = ALLIN
  12 = rit2_turn        6 = COMMUNITY   (§4.7)
  13 = rit2_river
```

Codes `2 = CALL` and `4 = RAISE` are **reserved but never emitted**. The producer (`Crupier.mapJavaActionToWire`) mirrors the Java `Player` model, which has no separate CALL or RAISE: a call is encoded as `CHECK` and a raise as `BET`, with the `AMOUNT_CENTS` field carrying the amount that distinguishes them. An auditor reconstructing the signed chain therefore sees `CHECK` / `BET` records, never `CALL` / `RAISE`.

The `rit2_*` codes (11 to 13) tag the run-it-twice second board's community reveals, so a side-A and a side-B reveal of the same street hash to distinct records (§4.7).

Translation Java↔wire is isolated in `HandStateChain` and `CanonicalActionRecord`, so refactoring the Java enums never moves the wire byte.

### 4.4 Per-action signature

```
sig_t = Ed25519.sign(player_privkey, "ACTION\0" || record_t)
```

### 4.5 Departed peers and the `is_voluntary` bit

When a peer leaves mid-hand the host broadcasts `EXIT`, every receiver flips `Participant.isExit`, and on the next betting iteration to that slot a local FOLD synth is produced with `record = sig = null` and `is_voluntary = 0`. That slot's wire `ACTION` broadcast and chain `absorb` are both skipped: **no record is contributed for that slot on any peer**, and the chain converges by mutual omission.

The `is_voluntary` bit still travels on the wire in both states. It is `1` when the actor signs its own action (humans, and bot actions the host signs on their behalf, §10) and `0` when the **host** signs a record in its own name, which today is the community card reveal (§4.7). The bit tells the receiver which pubkey to verify against (§4.6), so it is load-bearing. The one `is_voluntary = 0` record that never reaches the wire is the departed-peer auto-fold synth above, dropped by mutual omission.

Honest player timeouts are resolved client-side: each peer's local `auto_action` timer auto-clicks CHECK/FOLD and sends a regular signed `ACTION` (`is_voluntary = 1`). There is no host-side timeout autofold.

### 4.6 Receiver verification table

| FLAGS.is_voluntary | Actor type | Expected signer pubkey |
|---|---|---|
| 1 | Human | Actor's own pinned pubkey |
| 1 | Bot (`Participant.isCpu()`) | **Host's** pinned pubkey (§10) |
| 0 | Host-signed record (community reveal, §4.7) | **Host's** pinned pubkey |

### 4.7 Host-signed community card reveals

Community cards are never sent in the clear. After the SRA cascade and the community rotation pass (see [`SECURITY.md`](SECURITY.md) §2.2), each community slot is locked **only** under each peer's `k_community`. Per street, the host:

1. Sends every ring member its **own per-recipient encrypted piece**, `FLOP_PIECE` / `TURN_PIECE` / `RIVER_PIECE` (and `RABBIT_*_PIECE` for rabbit hunting), wire-shaped as `<PIECE>#<recipient_nick_b64>#<payload_b64>`. The recipient applies its `local_sra_unlock_community` to decode the piece **locally** and maps each 32-byte chunk to a card index via `RistrettoSRA.resolveCardIndex`.
2. Broadcasts a single signed announcement to everyone:
   ```
   COMM_REVEAL # <record_b64> # <sig_b64>
   ```
   The record is a canonical `ACTION_COMMUNITY` record:
   - `PLAYER_ID = SHA-256(NFC(host_nick))`
   - `STREET` = the street being revealed (flop=1, turn=2, river=3 on the live board, or 11/12/13 for the run-it-twice second board, §4.3)
   - `AMOUNT_CENTS` = the revealed card ordinals packed one byte per card, little-endian within the field (`packCommunityCards`, 1 to 3 cards)
   - `FLAGS.is_voluntary = 0` (the host signs this record in its own name, so it verifies against the host pubkey per §4.6)
   - `sig_t = Ed25519.sign(host_privkey, "ACTION\0" || record_t)`

Each ring peer verifies the host signature, then **cross-checks the announced ordinals against the indices it decoded from its own piece**. Any mismatch is a cross-recipient fork and triggers `SECURITY_LOCKDOWN`. Only after the cross-check passes is the `(record, sig)` pair absorbed into `H_t` (with the host as the actor). Because every peer absorbs the identical signed announcement, a host that announces different boards to different peers produces divergent receipts and is caught both in real time (lockdown on mismatch vs. the local piece) and post-hand (receipt divergence).

A peer also drops any `COMM_REVEAL` whose `STREET` doesn't match the street it is currently draining, to avoid stale-street replay. Pure observers (not in the ring, no piece to decode) paint the board from the signed announcement but do not absorb into a chain.

### 4.8 Events NOT in the chain

| Event | Handling |
|---|---|
| Showdown card reveal | Transported outside the 92-byte record. Remote keys are gathered by `REQ_SHOWDOWN_KEY` / `RESP_SHOWDOWN_KEY` (unicast to the host), then the host re-broadcasts every revealer's key in one atomic `POTCARDS` message. The same signature also rides the voluntary mid-hand `SHOWCARDS`. It **is** individually signed under the `"SHOWDOWN\0"` domain (payload `HAND_ID \|\| nick \|\| k_pocket`) and cross-checked, but lives outside `H_t`. `HAND_ID` is bound inside the signature, not carried as a wire field. |
| EXIT | Session-level event on the regular encrypted/HMAC'd channel (no per-event Ed25519 signature). A forged EXIT desyncs the spoofed peer's chain against a missing slot, still detectable post-hand. OOB identity verification is the actual defence against host impersonation. |
| REBUY | Between hands, doesn't affect the current `H_t`. |

### 4.9 Wire encoding of a signed action

```
GAME # <command_id> # ACTION # <nick_b64> # <decision> # <bet> # <cinematic_or_*> # <record_b64> # <sig_b64>
```

The outer `GAME # <command_id> #` envelope is prepended by `broadcastGAMECommandFromServer` / `sendGAMECommandToServer`, where `command_id` is a random per-command int (`Helpers.CSPRNG_GENERATOR.nextInt()`) used only for CONF de-duplication, not a hand sequence number. The inner `ACTION` subcommand carries the Base64 nick, the numeric `decision` code (the Java `Player` action constant), the `bet` amount as a decimal string (`0` for non-bets), a `cinematic_or_*` slot (the all-in animation payload or `*`), and finally `record_or_*` / `sig_or_*` (each `*` when absent).

The nick, decision, bet and cinematic fields are operational (logs, animations, parser compatibility). **Only `<record_b64>` and `<sig_b64>` are cryptographically meaningful**. They are the canonical values fed to the chain and verifier.

---

## 5. Hash chain `H_t`

Source: [`HandStateChain.java`](../src/main/java/com/tonikelope/coronapoker/HandStateChain.java).

### 5.1 Initial state `H_0`

Each peer computes locally after `MEGAPACKET` processing (no broadcast):

```
H_0 = SHA-256(
        "HAND\0"                             ||  //  5B domain separator
        HAND_ID                              ||  // 16B random from host
        num_players (uint8)                  ||  //  1B
        per peer, sorted by id:                  // 96B × N
          PLAYER_ID(32) || K_pocket(32) || K_community(32)
        || SHA-256(cascaded_deck_bytes)          // 32B deck commitment
      )
```

Each peer's block, its `PLAYER_ID` (`SHA-256(nick_canonical_utf8)`) followed by its per-hand commitments `K_pocket = k_pocket·B` and `K_community = k_community·B` (Ristretto255 encodings), is sorted by `PLAYER_ID` as a 32-byte unsigned integer, so `H_0` is identical across peers regardless of join order. Binding the `K` commitments here is what the verifiable dealing checks its DLEQ de-lock proofs against (see [`SECURITY.md`](SECURITY.md) §2.5). The deck commitment binds the chain to the exact cascade permutation: peers that walked a different cascade diverge on the very first absorb.

### 5.2 Per-action ratchet

```
H_{t+1} = SHA-256(record_t || sig_t)
```

The signature is included so that verifying `H_final` implicitly verifies that each constituent action was correctly signed.

Intermediate `H_t` values are **not** broadcast in production. The chain is verified only at hand close, via receipts (§6). A development-only assertion mode can broadcast and compare intermediate `H_t` across peers, but it is never enabled in release builds (broadcasting intermediate values would correlate chain state with public actions).

### 5.3 Terminal settlement record

After the last action and community-card reveal, the chain absorbs one terminal record committing the hand's **settlement** (the money movement, not just the actions and board). Every peer computes it locally from inputs it has already verified (the showdown cards resolved to genesis, the bets committed in `H_t`), so honest peers produce byte-identical tables and a peer reporting a different payout diverges on `H_final`.

Layout ([`SettlementRecord.java`](../src/main/java/com/tonikelope/coronapoker/SettlementRecord.java), big-endian, no padding):

```
HAND_ID(16) || VERSION(0x02) || N(uint8)
  || per participant, sorted ascending by PLAYER_ID:
       PLAYER_ID(32) || bote_cents(int64) || pagar_cents(int64)
  || opening_remainder_cents(int64)
  || closing_remainder_cents(int64)
```

`bote_cents` is the player's total contribution and `pagar_cents` the amount paid. The two remainder fields distinguish carry entering and leaving the hand. `Crupier.settlementAmountToCents` rejects non-finite, negative or out-of-range floating-point inputs. A recovered negative carry separately latches `settlement_accounting_invalid`. Once values are integer cents, `SettlementRecord.amountsBalance` enforces with overflow-safe arithmetic: `Σ pagar + closing_remainder = Σ bote + opening_remainder`. Participants are sorted by `PLAYER_ID`; duplicate ids are rejected. The legacy three-argument encoder with only a closing remainder remains solely for historical fixtures/records; production consensus must use v2.

Absorb (a distinct domain separator keeps a settlement table from ever being parsed as an action record):

```
H_final = SHA-256("SETTLE\0" || H_t || settlement_table)
```

The record carries no per-actor signature: it rides the closing receipt, whose `"RECEIPT"` signature already covers `H_final` (§6), and it adds no wire message. The absorb is local, attested by the existing receipt exchange. Cross-platform reproducibility relies on the same rules as §4.2 (cents at chip precision, big-endian, canonical `PLAYER_ID`).

---

## 6. Receipt and consensus

At hand close every peer publishes a signed receipt over the `HANDVERIFY` command (dual-form payload: a trigger from the host, then one signed receipt per peer). Source: [`Crupier.java`](../src/main/java/com/tonikelope/coronapoker/Crupier.java), [`IdentityManager.java`](../src/main/java/com/tonikelope/coronapoker/IdentityManager.java).

### 6.1 Wire form

```
Host → all:      GAME # <command_id> # HANDVERIFY                       (no payload, trigger)
Each peer → all: GAME # <command_id> # HANDVERIFY # <nick_b64> # <receipt_b64>
```

Both forms ride the standard `GAME # <command_id> #` envelope. The parser distinguishes the trigger from a receipt by field count (3 fields vs 5).

### 6.2 Receipt format

```
receipt = HAND_ID(16) || H_final(32) || flags(1) || sig(64)        = 113 bytes

sig = Ed25519.sign(my_privkey, "RECEIPT\0" || HAND_ID || H_final || flags)
```

- `H_final` is the chain value after the terminal settlement absorb (§5.3), so a matching receipt attests agreement on the pot payout as well as the action history and board.
- `flags.bit0` (`RECEIPT_FLAG_BIT_INVALID_SIG_SEEN`) is set when the issuer observed at least one invalid Ed25519 signature on an `ACTION` or `COMM_REVEAL` during the hand.
- `flags.bit1` (`RECEIPT_FLAG_BIT_DECK_UNVERIFIED`) is set when the issuer could not confirm the honest-shuffle proof (`DUALLOCK_BUNDLE`) for this hand's deck.
- `flags.bit2` (`RECEIPT_FLAG_BIT_NO_SHUFFLE_PROOF`) qualifies bit1: set only when a proof was expected (fresh deal) and none arrived (host withholding, as opposed to a slow local verifier). See [`SECURITY.md`](SECURITY.md) §6 for the full bit1/bit2 policy.

### 6.3 Consensus check

After collecting receipts (bounded by a timeout), each peer's `runConsensusCheck` classifies every expected peer and reports OK only when **all three** hold:

1. Every receipt signature verifies against the claimed signer's pinned pubkey (and the receipt's `HAND_ID` matches the local hand).
2. Every `H_final` is byte-identical to the local `H_final`.
3. No receipt has any `flags` bit set anywhere on the table.

The outcomes, in descending priority (only the strongest is surfaced):

| Outcome | Meaning | Severity | `disputed_hands` |
|---|---|---|---|
| `DIVERGENT` | a receipt's sig fails or its `H_final` differs | SEVERE (interpreted as host manipulation) | `reason='DIVERGENT'` |
| `MISSING` | a peer's receipt is absent / wrong length / stale `HAND_ID` / pubkey unavailable | WARNING (ambiguous: network or crash) | `reason='MISSING'` |
| `INVALID_SIG_SEEN` | all sigs valid and `H_final`s match, but some peer flagged an invalid action sig (bit0) | WARNING (popup to the table) | `reason='INVALID_SIG_SEEN'` |
| `DECK_NO_PROOF` | otherwise clean, but some peer never received the shuffle proof (bit1+bit2), host may be withholding it | WARNING (popup to the table) | `reason='DECK_NO_PROOF'` |
| `DECK_UNVERIFIED` | otherwise clean, but some peer's proof is still verifying (bit1 alone, slow peer) | WARNING (silent: JUL + row, no popup) | `reason='DECK_UNVERIFIED'` |
| `OK` | all present, unanimous, no flag bit set | INFO | No |

Consensus gates durable close for `DIVERGENT`, `MISSING` and `INVALID_SIG_SEEN`. The engine ends transmission and skips SQL close, reset and advancement. It does not reverse the already-computed payout; the open SQL hand and in-memory accounting remain available for recovery. `DECK_NO_PROOF` and `DECK_UNVERIFIED` remain forensic receipt outcomes because the separate shuffle-proof gate already blocked any unverified community unlock before reveal.

#### Why divergence is essentially proof of a malicious host

Every action is individually Ed25519-signed by its emitter and the `H_t` chain is computed locally by each peer from the actions received over TCP. For two peers to reach byte-different `H_final`, the host must have sent different/omitted/reordered actions to different peers (or a serious relay bug did). TCP guarantees in-order delivery or breaks the connection, so packet loss manifests as a **missing** receipt, never a silently **divergent** one. Hence `DIVERGENT` is smoking-gun evidence of host manipulation. `MISSING` is ambiguous and treated more mildly.

#### Why preserve rather than refund

The payout calculation may already exist in memory when receipt consensus fails. Refunding or settling again would risk double movement. The safe response is to stop, leave the durable hand open, retain its accounting and recover or investigate from that single state.

### 6.4 User-facing messaging

The Crupier log shows one of the verification outcomes at hand close. Divergence, missing receipt and invalid-signature outcomes show a modal, terminate transmission and block durable close; deck-proof receipt flags remain forensic as described above. `DECK_UNVERIFIED` stays deliberately silent. JUL records INFO / WARNING / SEVERE accordingly.

---

## 7. SQLite schema additions

Identity and dispute tables are created with `CREATE TABLE IF NOT EXISTS` on startup ([`Helpers.java`](../src/main/java/com/tonikelope/coronapoker/Helpers.java)). The existing `balance` table is also migrated transactionally: null identities are rejected, legacy duplicates retain the greatest row id, and a unique `(id_hand, player)` index is installed. Hand creation and close then require an exact roster and strict row counts.

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
    reason    TEXT,                             -- 'DIVERGENT' | 'MISSING' | 'INVALID_SIG_SEEN' | 'DECK_NO_PROOF' | 'DECK_UNVERIFIED'
    FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE
);
```

The `receipts` blob is stored **as collected** (the receipts are already signed, they are not separately encrypted at rest).

---

## 8. Wire protocol summary

| Command | Direction | Notes |
|---|---|---|
| Join payload with `JOIN` marker field | New peer → host (relayed) | Identity announcement folded into the existing join, self-sig binds `session_id` |
| `GAME # ... # <record_b64> # <sig_b64>` | Player → host → all | Signed canonical action |
| `<PIECE> # <nick_b64> # <payload_b64>` | Host → each recipient | Per-recipient encrypted community piece (`FLOP/TURN/RIVER_PIECE`, `RABBIT_*_PIECE`) |
| `COMM_REVEAL # <record_b64> # <sig_b64>` | Host → all | Signed community-card announcement, absorbed into `H_t` |
| `HANDVERIFY` (no payload) | Host → all | Consensus trigger |
| `HANDVERIFY # <nick_b64> # <receipt_b64>` | Each peer → all | Signed end-of-hand receipt |

Wire-incompatible changes are gated by the strict `AboutDialog.VERSION` equality check at join (§3).

---

## 9. UI summary

| Where | Element | Behavior |
|---|---|---|
| Table, `LocalPlayer` (human) | Clickable avatar | Opens the identity identicon of own pubkey + full fingerprint + a copy-to-share hint. No "Verify identity" button (self-verification is meaningless) and no passive verification indicator on the table. |
| Table, `RemotePlayer` (human) | Clickable avatar | Opens the identity identicon of the peer's pubkey + fingerprint + "Verify identity" (sets `verified_oob=1` for that `(nick, pubkey)`). No passive verification indicator on the table. |
| Table, `RemotePlayer` (bot) | Avatar click is a no-op | Bots have no identity. The avatar click does nothing for them. |
| Waiting room, participant list (right-click) | Session-key identicon | Client opens the AES identicon of its channel with the host. Host opens the per-client session-identicon mosaic (`SessionIdenticonMosaicDialog`). |
| `NewGameDialog` (host) | Non-blocking warning | If estimated password entropy `< 60 bits`, warn. The host may proceed. |
| Crupier log / modal at hand close | Verification outcome | `✓ verified` / `⚠ divergent` / `⚠ missing`. Modal on any non-OK outcome except the deliberately silent `DECK_UNVERIFIED`. |

---

## 10. Bot identity

**Bots have no cryptographic identity of their own.** The host operates each bot and **signs bot actions with the host's own Ed25519 private key**. Receivers therefore verify a bot's actions against the **host's** pinned pubkey (the signer resolution maps any `Participant.isCpu()` actor to the host's identity). Bots are not inserted into `known_identities` and expose no identity affordance in the UI (their avatar click is a no-op). The `$` character is reserved for bot nicknames: the human nick-entry path ([`NewGameDialog.java`](../src/main/java/com/tonikelope/coronapoker/NewGameDialog.java)) strips it before identity initialization, and the host rejects any remote `JOIN` containing it ([`WaitingRoomFrame.java`](../src/main/java/com/tonikelope/coronapoker/WaitingRoomFrame.java)). Host-generated bot names carry it. `CoronaBot$<number>` is the current bot naming/classification convention; the reservation is the `$` character itself.

A malicious host can of course abuse the bots it operates, but every bot action still carries the host's signature and lands in `H_t` like any other action, so it is independently verifiable and attributable to the host in the chain.

### Host identity is a regular peer

The host has the same kind of per-nick Ed25519 identity as any other peer, is stored in `known_identities` like everyone else, and signs its own actions like everyone else. Its only cryptographic specialisations are:

- Signing community-card reveals (`ACTION_COMMUNITY`, §4.7) so every peer absorbs identical reveal records into `H_t`.
- Signing the actions of the bots it operates (above).

No special "host pubkey" exists in the protocol: host == player + extra responsibilities.

---

## 11. Glossary

- **TOFU**: Trust On First Use. Accept a key the first time, pin it. SSH-style.
- **PAKE**: Password-Authenticated Key Exchange. Authenticate with a shared password without revealing it.
- **Domain separator**: Unique NUL-terminated string prefix in every signature (and in the chain absorb) so a value for one purpose cannot be replayed in another: `ACTION\0`, `RECEIPT\0`, `SHOWDOWN\0`, `JOIN\0` for the signed contexts, plus `HAND\0` for the `H_0` seed and `SETTLE\0` for the terminal settlement absorb (§5.1, §5.3).
- **Ratchet**: One-way state update where each step depends on the previous. Reordering is impossible without breaking the chain.
- **Receipt**: Signed commitment by a peer to a final chain state, archivable as evidence.
- **OOB (Out-of-Band)**: A channel separate from the system being secured (e.g. a phone call to compare a fingerprint shown in the UI).
- **NFC / NFD**: Unicode Normalization Forms (Composed / Decomposed). The same logical character can be byte-different across systems unless normalized.
