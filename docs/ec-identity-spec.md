# EC-Identity v1 — Specification

Cryptographic identity, action signing and hand integrity layer for CoronaPoker.

**Branch**: `ec-identity-v1`
**Status**: Design — not yet implemented
**Author**: tonikelope
**Last updated**: 2026-05-18

---

## 1. Threat model

This layer addresses vectors that the current master cannot detect.

### What this spec covers

| Vector | Defense |
|---|---|
| Hostile host substituting another player's identity | Ed25519 long-term keys + silent TOFU pinning + optional OOB fingerprint comparison via identicons |
| Hostile host rewriting hand history (action injection, reordering) | Per-action Ed25519 signatures + `H_t` hash chain with `PREV_H` binding |
| Network MITM on the ECDH handshake (ISP, hostile router, public Wi-Fi) | Existing `Helpers.deriveChannelSecret` (HMAC binding with shared password) and existing session-key identicon — kept as-is |
| Post-game disputes about what actions occurred | Final receipts signed with Ed25519 identity keys, archivable as evidence |

### What this spec explicitly does NOT cover

- **Local trojan / Ring-0 attacker on a victim's machine.** Private keys live in `<user.home>/.coronapoker/identity.ed25519` and in JVM heap. A sufficiently privileged local attacker reads them. Mitigating this requires Panoptes-class C-enclave hardening, out of scope.
- **MITM during the first encounter between peers that never met before.** TOFU has no anchor on first contact. Mitigation is **optional, opt-in** OOB fingerprint comparison via the identicon dialog. Never forced, never blocking.
- **Collusion of N-1 peers against 1 victim.** Inherent to any consensus without a trusted third party. Victim sees the consensus check log when it diverges, can refuse to settle informally.
- **Cross-device identity portability.** Each install generates its own keypair. Pubkey = machine, nick = player. Multi-device users will see their pubkey change between sessions — that is the natural and correct behavior.
- **Identification by pubkey for stats/recover.** Nick remains the identifier for SQLite stats and recover. No SQL schema changes to existing tables.
- **Chain recovery on mid-hand reconnection.** A peer that drops mid-hand and reconnects cannot rejoin the in-progress hand (matches master's existing recovery behavior). Their absent receipt makes that hand close as MISSING for them. See §12 for deferred v2 plans.

### Discarded design alternatives (and why)

- **Constant-time `modInverse` in SRA**: only matters with local trojan, where nothing Java-pure saves you.
- **Subgroup low-order point check in SRA**: redundant — `CryptoSRA.resolveCardIndex = -1` already invalidates tampered hands.
- **MAC swarm (Panoptes-style)**: same MITM exposure during pubkey exchange, but loses non-repudiation.
- **Direct P2P topology for game commands**: too invasive a refactor. Star topology stays. The signing layer makes host's broker role harmless.
- **Mutual nonce challenge at join**: replay protection is delegated to `session_id` baked into `JOIN_IDENTITY` self_sig — same effect, zero extra round-trips.
- **Blocking modal on TOFU pubkey mismatch**: removed for UX reasons. TOFU updates silently (last-write-wins), the user inspects identicons if they care.
- **`⟳` badge for "previously verified pubkey changed"**: simplicity wins. Only two shield states: verified (green) or not (grey).
- **Anti-spoofing nick canonicalization (zero-width chars, bidi, lookalikes)**: irrelevant if nobody modifies the client source. Only real cross-platform variations (Unicode NFC vs NFD) are canonicalized.
- **PAKE formal (SPAKE2/OPAQUE)**: existing `HMAC(password, ECDH)` is sufficient with a non-trivial password. Commit 0 adds entropy warning at game creation.

---

## 2. Identity layer

### 2.1 Keypair

Each installation generates one Ed25519 keypair on first launch.

- **Algorithm**: `Ed25519` (RFC 8032), `KeyPairGenerator.getInstance("Ed25519")` (native in JDK 15+).
- **Storage**:
  - Private key: `<user.home>/.coronapoker/identity.ed25519`, PKCS#8 encoded, FS permissions `0600` on POSIX.
  - Public key: sidecar `identity.ed25519.pub`, 32 raw bytes.
- **At-rest encryption**: deliberately **not** in v1. FS permissions are the user's responsibility. A leaked privkey == regenerate (delete file) and re-pair via TOFU.
- **No rotation in v1**: deleting the file generates a fresh keypair next launch. Other peers will see a pubkey change on next connect (silent TOFU update).
- **First-time failure handling**: if `<user.home>/.coronapoker/` cannot be created (permission denied, full disk, etc.) or the keypair cannot be written, the application **fails loudly** with a clear error dialog and refuses to start any networked game. Identity persistence is a hard requirement — the app does not fall back to ephemeral in-memory identity.

### 2.2 Identity vs nick semantics

The nick identifies a **player** (the person). The pubkey identifies a **machine** (the installation).

Implications, all expected and correct:

- Two players sharing one machine: same pubkey, different nicks. `known_identities` has one row per nick.
- One player using two machines: same nick, two pubkeys. Peers will see the pubkey change when they switch devices. The shield reverts to grey (unverified for the new pubkey) silently.
- An impostor trying to claim antonio's nick: gets accepted silently into TOFU. Other peers must inspect the identicon manually to detect — but per-action signatures still expose the impostor at first action because the impostor cannot produce a signature matching antonio's known pubkey.

### 2.3 Fingerprint format

Two derived representations, both from `SHA-256(pubkey)`:

- **Short** (8 hex chars, 32 bits): `a3f9-1c4b`. Compact use.
- **Full** (128 bits in 8 groups of 4): `a3f9 1c4b 7e2d 9faa 8c12 4456 ef78 1234`. For OOB comparison.

### 2.4 Identicon

The existing `IdenticonDialog` is reused. Commit 3 modifies:

- Hash: `MD5` → `SHA-256`.
- Grid: `5×5` → `7×7` with horizontal symmetry (~100 bits visible entropy).
- Foreground: 1 color → 2 colors from disjoint hash bytes.
- Title: full fingerprint hex in 8 groups of 4.

The existing identicon (derived from the session AES key) is **kept untouched** for network-MITM detection. The new identity identicon is **added alongside**.

---

## 3. Handshake protocol

A single new command is added to the existing waiting-room join handshake.

### `JOIN_IDENTITY`

After the existing ECDH session-key handshake, the new peer broadcasts:

```
JOIN_IDENTITY # <nick> # <pubkey_b64> # <self_sig_b64>

self_sig = Ed25519.sign(privkey,
                        "JOIN_V1\0" || session_id || nick_canonical_utf8 || pubkey)
```

Where:

- `session_id` is 16 random bytes generated by the host at game creation and broadcast in the existing initial-join init payload. All peers know this value.
- `nick_canonical_utf8` is `Normalizer.normalize(nick, NFC).getBytes(UTF_8)`.

### Replay protection by `session_id`

Each game has a fresh `session_id`. A `self_sig` is valid only for the specific session whose `session_id` it encodes. Replaying a `JOIN_IDENTITY` from a previous session fails verification because the embedded `session_id` won't match the current one.

### TOFU resolution (silent, no user interaction)

After verifying `self_sig`, each peer updates its local `known_identities`:

| Case | Behavior | Shield |
|---|---|---|
| Unknown nick | INSERT row, `sessions_count=1`, `verified_oob=0` | Grey |
| Known nick + pubkey matches | UPDATE `last_seen`, `sessions_count++` | Grey or green (depends on `verified_oob`) |
| Known nick + pubkey differs | UPDATE row (last-write-wins), `verified_oob=0` | Grey (re-verification needed) |

**No blocking modal.** The user finds out about pubkey changes only by inspecting the shield (green vs grey) or opening the identicon dialog.

### Manual verification ("Verificar identidad" button)

Inside the identity identicon dialog, a button labeled **"Verificar identidad"** is offered (Spanish: "Verificar", not "Validar" — verification connotes authenticity confirmation, validation connotes rule-checking). Clicking it:

```sql
UPDATE known_identities SET verified_oob = 1 WHERE nick = ? AND pubkey = ?
```

Next render, the shield turns green for that nick. If the pubkey later changes silently, `verified_oob` resets to 0 → shield reverts to grey.

#### Hint text at the bottom of an unverified identicon dialog

When the dialog opens on an `(nick, pubkey)` pair with `verified_oob = 0`, a small explanatory line is rendered below the identicon, above the "Verificar identidad" button. Proposed i18n key and copy:

```properties
# messages_es.properties
ui.identicon.no_verificada=Esta clave no ha sido verificada por un canal seguro externo (ej. WhatsApp, Telegram). Cuando hayas confirmado que coincide con la del otro jugador, pulsa "Verificar identidad".

# messages_en.properties
ui.identicon.no_verificada=This key has not been verified through a secure external channel (e.g. WhatsApp, Telegram). Once you have confirmed it matches the other player's key, click "Verify identity".
```

When `verified_oob = 1`, the hint line is hidden and a small green checkmark indicator can be shown instead.

---

## 4. Canonical action record

Every hand-action mutation is serialized to a flat 92-byte record before being signed and absorbed into the hash chain.

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
 90      2   FLAGS           uint16 BE   bit0 = is_allin, bit1 = is_voluntary, rest = 0
                                          Total: 92 bytes
```

### 4.2 Canonicalization rules — protection against JVM/OS variations

Four rules. All defensive against honest cross-platform bugs, none against malicious source-modified clients.

1. **NFC nick normalization**:
   ```java
   nick_utf8 = Normalizer.normalize(nick, Normalizer.Form.NFC)
                          .getBytes(StandardCharsets.UTF_8);
   ```
   Defends against precomposed vs decomposed Unicode (macOS HFS vs Windows). Identity for ASCII nicks.

2. **Float-to-cents conversion**:
   ```java
   amount_cents = Math.round((double) amount_float * 100.0);
   ```
   Eliminates `0.1f + 0.2f` jitter by widening to double before multiplication, then banker's rounding. Single canonical implementation; **prohibited to re-implement elsewhere.**

3. **Locale-independent wire parsing**:
   - `Float.parseFloat` and `Long.parseLong` are already locale-independent — use them.
   - Any use of `NumberFormat` for amounts must explicitly use `Locale.ROOT`.

4. **Encoder as single source of truth**:
   The class `CanonicalActionRecord.encode(...)` is the only path that produces the 92-byte buffer. Strict argument validation, fail-loud on out-of-range inputs.

### 4.3 Wire enum (independent of Java internal constants)

```
STREET (uint8):       ACTION_TYPE (uint8):
  0 = preflop           0 = FOLD
  1 = flop              1 = CHECK
  2 = turn              2 = CALL
  3 = river             3 = BET
  4 = showdown          4 = RAISE
                        5 = ALLIN
```

If Java refactors its enums (rename, add `STRADDLE`, etc.), the wire byte stays stable. Translation Java↔wire is isolated in `HandStateChain`.

### 4.4 Per-action signature

```
sig_t = Ed25519.sign(player_privkey,
                     "ACTION_V1\0" || record_t)
```

### 4.5 Host-issued auto-fold (timeout)

When a player times out and the host auto-folds them:

- `ACTION_TYPE = FOLD`
- `FLAGS bit1 (is_voluntary) = 0`
- The signature is by the **host's privkey**, not the timed-out player's.

Receivers verify: if `is_voluntary=0`, signature must validate against the host's pubkey (known from `known_identities`). If `is_voluntary=1`, signature must validate against `PLAYER_ID`'s pubkey. Any other combination → reject.

### 4.6 Events NOT in the chain

Out of scope for `CanonicalActionRecord`. Each has its own flow:

| Event | Why excluded |
|---|---|
| SHOW_CARDS at showdown | Needs to transport `sra_unlock` (32B) or revealed cards — doesn't fit 92B. Separate flow, individually signed but outside chain. |
| Community card reveal by host | Host-driven event, not player action. Deck commitment in `H_0` already binds the deck. |
| EXIT | Session-level event. Signed with the player's Ed25519 identity key for non-repudiation (prevents host from forging an EXIT of another player). Outside the action chain `H_t`. |
| REBUY | Between hands, doesn't affect current `H_t`. Stays as today. |

### 4.7 Wire encoding (commit 5)

```
GAME # <hand_seq> # <ACTION_TYPE_STR> # <nick> # <amount_str> # <record_b64> # <sig_b64>
```

The legacy fields `<hand_seq>`, `<ACTION_TYPE_STR>`, `<nick>`, `<amount_str>` remain for human-readable logs and parser compatibility. **Only `<record_b64>` and `<sig_b64>` are cryptographically meaningful** — they are the canonical values fed to the chain and verifier.

---

## 5. Hash chain `H_t`

### 5.1 Initial state `H_0`

Each peer computes locally after `MEGAPACKET` processing:

```
H_0 = SHA-256(
        "HAND_V1\0"                          ||  //  8B domain separator
        HAND_ID                              ||  // 16B random from host
        num_players (uint8)                  ||  //  1B
        sorted_player_ids_concat             ||  // 32B × N (lexicographic)
        SHA-256(cascaded_deck_bytes)             // 32B deck commitment
      )
```

`sorted_player_ids_concat` is the concatenation of `PLAYER_ID` (`SHA-256(nick_canonical_utf8)`) values, sorted lexicographically as 32-byte unsigned integers. Sorting guarantees identical `H_0` across peers regardless of join order.

No broadcast: each peer computes from already-known local data.

### 5.2 Per-action ratchet

```
H_t = SHA-256(record_t || sig_t)
```

The signature is included so verifying `H_final` implicitly verifies each constituent action was correctly signed.

### 5.3 Debug-mode `H_CHECK` (commit 4)

Behind a static flag `HandStateChain.DEBUG_HANDCHAIN = false`, commit 4 introduces broadcast after each action:

```
H_CHECK # <nick> # <h_t_b64>
```

Receivers compare with their own `H_t`. Mismatch → severe log with last N actions dumped.

**Debug only — never enabled in release builds.** Intermediate `H_t` values reduce chain entropy if correlated with public actions. Production builds verify chain integrity only at hand-close.

---

## 6. Receipt and consensus

Replaces the no-op `HANDVERIFY` semantics. The command name `HANDVERIFY` is **kept** (dual-form payload).

### 6.1 Wire form

```
Host → All:      HANDVERIFY                              (no payload, trigger)
Each peer → All: HANDVERIFY # <nick> # <receipt_b64>     (with payload, signed receipt)
```

The parser distinguishes by field count.

### 6.2 Receipt format

```
receipt = (hand_id || H_final || sig)

sig = Ed25519.sign(my_privkey, "RECEIPT_V1\0" || HAND_ID || H_final)
```

Encoded as flat binary in `<receipt_b64>`.

### 6.3 Strict unanimity — divergence interpretation

After collecting receipts (timeout `CLIENT_RECEPTION_TIMEOUT`, reused from master), each peer verifies:

1. Each signature validates against the known pubkey of its claimed signer (`known_identities`).
2. Each `H_final` is byte-identical to the local `H_final`.

**The hand always settles normally** regardless of the result. Consensus failure is **signaletic, not gating**.

#### Why divergence is essentially proof of a malicious host

In this protocol, every action is individually Ed25519-signed by its emitter, and the `H_t` chain is computed locally by each peer from the actions they received over TCP. For two peers to arrive at byte-different `H_final` values, one of the following must have happened:

- The host sent different action contents to different peers, or
- The host omitted actions for some peers but not others, or
- The host reordered actions for some peers, or
- A critical bug in the host's relay loop produced one of the above by accident.

Network packet loss or out-of-order delivery cannot cause silent divergence because TCP guarantees in-order delivery or breaks the connection. Network failures manifest as **missing receipts** (peer dropped off), not as divergent ones.

Therefore the divergent case is **smoking-gun evidence of host manipulation** (deliberate or via a serious bug). The missing case is ambiguous (network outage, client crash, deliberate non-acknowledgment) and is treated more mildly.

#### Three outcomes

| Outcome | Severity | Crupier log (i18n) | Popup (modal OK) | JUL debug | `disputed_hands` |
|---|---|---|---|---|---|
| All received + unanimous | OK | `game.mano_verificada_consenso` | None | INFO | No |
| Some receipt missing | Warning (ambiguous: network or client crash) | `game.mano_verificacion_jugador_ausente` (lists missing nicks) | Yes, informative tone | WARNING | Yes, `reason='MISSING'` |
| All received but divergent | **Strong alert** (interpreted as host manipulation) | `game.mano_verificacion_divergente` (alarmist tone, recommends action) | Yes, prominent warning tone | SEVERE | Yes, `reason='DIVERGENT'` |

The hand is paid out in all three cases — the existing master flow for `cancelarManoYDevolverApuestas` / `MISDEAL` is **untouched** and remains reserved for actual SRA-level catastrophes (cable pull, mid-deal rage quit, cryptographic protocol breakdown).

### 6.4 Why not abort on divergence

Aborting would be a trivial griefing vector: any peer could rage-quit mid-river to force their unwanted hand to cancel. Settling and warning is the pragmatic choice for the "amistoso" model. Humans at the table discuss offline if reciprocity is warranted, and the strong wording of the divergent message makes the abuse pattern impossible to ignore.

### 6.5 i18n keys (added in commit 6)

```properties
# messages_es.properties
game.mano_verificada_consenso=✓ Mano verificada criptográficamente · {0} firmas unánimes
game.mano_verificacion_divergente=⚠ HOST HOSTIL DETECTADO · el servidor envió información distinta a distintos jugadores · evidencia guardada
game.mano_verificacion_jugador_ausente=⚠ Aviso: {0} no envió firma de cierre · mano liquidada

game.popup_verificacion_titulo_alerta=ALERTA DE SEGURIDAD
game.popup_verificacion_titulo_aviso=Aviso de verificación criptográfica
game.popup_verificacion_divergente=El servidor de esta partida ha enviado información distinta a distintos jugadores en esta mano. Esto solo puede ocurrir si el host está manipulando el juego deliberadamente o tiene un bug grave en su versión.\n\nLa mano se ha liquidado igualmente, pero los datos se han guardado como evidencia.\n\nRECOMENDACIÓN: considere abandonar esta partida.
game.popup_verificacion_ausente=No se pudo verificar el consenso de la última mano: el jugador {0} no envió su firma de cierre. Esto puede deberse a un corte de red o un crash del cliente. La mano se ha liquidado igualmente.

# messages_en.properties
game.mano_verificada_consenso=✓ Hand cryptographically verified · {0} unanimous signatures
game.mano_verificacion_divergente=⚠ HOSTILE HOST DETECTED · server sent different data to different players · evidence saved
game.mano_verificacion_jugador_ausente=⚠ Notice: {0} did not send closing signature · hand settled

game.popup_verificacion_titulo_alerta=SECURITY ALERT
game.popup_verificacion_titulo_aviso=Cryptographic verification notice
game.popup_verificacion_divergente=The server of this game has sent different data to different players in this hand. This can only happen if the host is deliberately manipulating the game or has a serious bug in their version.\n\nThe hand has been settled anyway, but the data has been saved as evidence.\n\nRECOMMENDATION: consider leaving this game.
game.popup_verificacion_ausente=Could not verify consensus of the last hand: player {0} did not send a closing signature. This may be due to a network outage or a client crash. The hand has been settled anyway.
```

### 6.6 JUL log (English, `Crupier`'s `LOGGER`)

```java
LOGGER.log(Level.INFO,    "Hand {0} verified: {1} receipts unanimous, H={2}", ...);
LOGGER.log(Level.WARNING, "Hand {0} verification incomplete: missing=[{1}]", ...);
LOGGER.log(Level.SEVERE,  "Hand {0} signature divergence: divergent=[{1}], local_H={2}", ...);
```

---

## 7. SQLite schema additions

All additive. No existing tables touched.

```sql
-- commit 2
CREATE TABLE IF NOT EXISTS known_identities (
    nick           TEXT    PRIMARY KEY,
    pubkey         BLOB    NOT NULL,            -- 32 raw bytes Ed25519
    first_seen     INTEGER NOT NULL,            -- epoch seconds
    last_seen      INTEGER NOT NULL,
    sessions_count INTEGER NOT NULL DEFAULT 0,
    verified_oob   INTEGER NOT NULL DEFAULT 0   -- 1 if user clicked "Verify identity"
);

-- commit 6
CREATE TABLE IF NOT EXISTS disputed_hands (
    id        INTEGER PRIMARY KEY,
    id_hand   INTEGER NOT NULL,
    timestamp INTEGER NOT NULL,                 -- epoch seconds
    receipts  BLOB    NOT NULL,                 -- encrypted with GAME_MASTER_KEY
    local_h   BLOB    NOT NULL,                 -- our local H_final at dispute time
    reason    TEXT,                             -- 'MISSING' or 'DIVERGENT'
    FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE
);
```

Migrations are `CREATE TABLE IF NOT EXISTS` on startup. No data migration needed.

---

## 8. Wire protocol summary

| Command | Direction | Added in | Compat-break | Notes |
|---|---|---|---|---|
| `JOIN_IDENTITY` | New peer → all (via host relay) | commit 2 | No | Identity announcement, `session_id` in self_sig |
| `GAME # ... # <record_b64> # <sig_b64>` | Player → host → all | commit 5 | **Yes** | Augmented action format |
| `H_CHECK # <nick> # <h_t_b64>` | Peer → all | commit 4 (debug) | No | Only with `DEBUG_HANDCHAIN=true` |
| `HANDVERIFY` (no payload) | Host → all | unchanged | No | Same as today |
| `HANDVERIFY # <nick> # <receipt_b64>` | Each peer → all | commit 6 | **Yes** | Augmented form — host's old plain `HANDVERIFY` becomes the trigger |

### `MIN_COMPATIBLE_VERSION` bump

In **commit 5** (first incompatible wire change). Earlier commits are additive only.

---

## 9. UI summary

| Where | Element | Click action | Commit |
|---|---|---|---|
| Waiting room, per participant row | Single shield, same position for all roles | • Client view: opens `IdenticonDialog` with session-AES identicon (existing, unchanged). <br/> • Host view: opens new `SessionIdenticonMosaicDialog` showing per-client session identicons in a grid. | 3 |
| Mesa, `LocalPlayer` (human) | Shield (grey/green per `verified_oob`) | Identity identicon of own pubkey + full fingerprint hex + "Verify identity" button. | 3 |
| Mesa, `RemotePlayer` (human) | Shield (grey/green per `verified_oob`) | Identity identicon of remote peer's pubkey (as locally received) + fingerprint + "Verify identity" button (UPDATEs `verified_oob=1` for that `(nick, pubkey)`). | 3 |
| Mesa, `RemotePlayer` (bot) | **No shield** | Bot has no shield. Presence/absence visually distinguishes human from bot. | 3 |
| `NewGameDialog` (host) | Non-blocking toast on submit | If estimated password entropy < 60 bits, warn. User can proceed. | 0 |
| Crupier log in-game | i18n message at hand close | `✓ verified` / `⚠ divergent` / `⚠ missing` | 6 |
| Popup modal at hand close | Informative `Helpers.mostrarMensajeInformativo` | Only on divergent or missing — not on successful verification | 6 |
| Debug JUL log | INFO / WARNING / SEVERE per outcome | Always, regardless of release mode | 6 |

### Identicon parameters (commit 3)

```
Hash:    SHA-256 (replaces existing MD5)
Inputs:  Ed25519 pubkey for identity; AES session key for session
Grid:    7×7 with horizontal symmetry (replaces existing 5×5)
Colors:  2 foreground from disjoint hash bytes + transparent background
Scale:   nearest-neighbor to dialog size, no smoothing
Title:   full fingerprint hex in 8 groups of 4
Verify:  "Verify identity" button on identity dialogs only
```

---

## 10. Bot identity

Each bot has its own ephemeral Ed25519 keypair generated by the host at game creation. Implications:

- Host broadcasts each bot's pubkey in the existing JOIN flow, as if the bot were a regular peer.
- Host signs all bot actions with the bot's privkey (not the host's own).
- Other peers verify bot actions against the bot's announced pubkey.
- Bot keypairs are not persisted — fresh per game session.
- Bot pubkeys are **NOT** inserted into `known_identities` (which is persistent SQLite). They live in-memory in `Crupier` for the game session only. Each new game generates fresh bot pubkeys with no historical baggage.
- Bots cannot be OOB-verified (no `verified_oob` ever set to 1 for bot pubkeys). Tooltip on the (absent) shield clarifies this if hovered over a bot's avatar slot.

This model preserves the separation of identities — the host plays as itself and operates each bot as a distinct actor. A malicious host could of course still abuse bots, but at least each bot's actions are independently verifiable in the chain.

### Host identity is a regular peer

The host of a game has the same Ed25519 identity as any other peer (one keypair per installation). They are stored in `known_identities` like everyone else, sign their own actions like everyone else, and other peers verify their actions against the host's known pubkey. The only special role of the host in cryptographic terms is:

- Issuing auto-folds on behalf of timed-out players (with `FLAGS.is_voluntary=0`, signed by host's own privkey, verified against host's known pubkey).
- Generating ephemeral bot keypairs for the game session.

No special "host pubkey" exists in the protocol. Host == player + extra responsibilities.

---

## 11. Commit plan with done criteria

Each commit compiles standalone, passes existing tests, and leaves the working tree consistent.

### Commit 0 — Password strength warning

- **Scope**: UI-only, no wire, no crypto.
- **Touch**: `NewGameDialog.java`, new `Helpers.estimatePasswordEntropyBits(String)`.
- **Done**:
  - Password `"1234"` triggers non-blocking warning toast.
  - Password `"k0r0n4p0k3r-2026!"` does not warn.
  - No false positives on reasonable passwords ≥ 12 mixed chars.

### Commit 1 — Ed25519 identity layer

- **Scope**: keypair gen/load/expose. Zero communication.
- **Touch**: new `IdentityManager` (singleton, init in `Init.java`). File I/O in `<user.home>/.coronapoker/identity.ed25519` + `.pub`.
- **API**: `getPublicKey()`, `getShortFingerprint()`, `getFullFingerprint()`, `sign(byte[] domain, byte[] data)`, `verify(byte[] pubkey, byte[] domain, byte[] data, byte[] sig)`.
- **Done**:
  - Fresh `<user.home>` → file created silently on first launch.
  - Fingerprint stable across restarts.
  - Unit test: sign/verify roundtrip + cross-platform vector match (Windows/Linux/macOS).

### Commit 2 — Identity handshake + TOFU

- **Scope**: `JOIN_IDENTITY` command. `known_identities` table. Silent TOFU resolution.
- **Touch**: `Helpers.java` schema, `WaitingRoomFrame.java` handshake, `NetServer/NetClient.java` routing, new `TOFUResolver` class.
- **Done**:
  - Two clean clients connect → both insert each other into `known_identities`.
  - Same clients reconnect → `sessions_count++`, no warnings.
  - Client with tampered pubkey reconnects → `known_identities` updates silently (last-write-wins), `verified_oob` resets to 0.
  - Wrong `session_id` in self_sig → JOIN rejected with clear error.

### Commit 3 — Identicons and shields UI

- **Scope**: UI only. Reuses `IdenticonDialog`, adds `SessionIdenticonMosaicDialog`.
- **Touch**: `IdenticonDialog.java` (MD5→SHA-256, 5×5→7×7, 2 colors, fingerprint hex label, overloaded constructor for raw byte arrays), shield components in `LocalPlayer/RemotePlayer/WaitingRoomFrame`, "Verify identity" button.
- **Done**:
  - Click on each shield opens the correct identicon (identity in mesa, session in waiting room).
  - "Verify identity" button persists `verified_oob=1`; shield turns green after refresh.
  - Bot avatars have no shield.
  - Host's mosaic dialog shows N session identicons in a grid.

### Commit 4 — Hand state chain (debug-only assertion)

- **Scope**: `H_0` computation, `H_t` ratchet on every action, debug-mode `H_CHECK`.
- **Touch**: new `HandStateChain` with `CanonicalActionRecord.encode(...)`. Field `hand_state_hash` in `Crupier`. Integration in local action emission and remote action processing.
- **Done**:
  - Unit tests: NFC/NFD nick equivalence, float→cents vectors, fixed 92-byte record from known inputs.
  - Cross-platform vector test: same inputs produce byte-identical record on Windows/Linux/macOS.
  - Multi-peer test with `DEBUG_HANDCHAIN=true`: 3 peers, full hand, `H_final` byte-identical across all.
  - Negative test: deliberate endianness bug in encoder → divergence log identifies the exact action.

### Commit 5 — Per-action Ed25519 signature + version bump

- **Scope**: GAME wire format augmented. Verification on receive. `MIN_COMPATIBLE_VERSION` bumped.
- **Touch**: `Crupier.java` broadcast/receive paths. `Init.java` version constant.
- **Done**:
  - 3 peers play a full hand, every action signed and verified.
  - Tampered peer broadcasts an action with invalid signature → other peers reject + log warning.
  - Old client (previous version) cannot join (version gate works).

### Commit 6 — Receipts + unanimous consensus check

- **Scope**: replace `HANDVERIFY` semantics with receipt exchange. `disputed_hands` table. i18n keys. JUL logs. Popup at hand close on divergence.
- **Touch**: `Crupier.recibirConsensoFinal` rewrite, `Helpers.java` schema, `messages_es/en.properties`.
- **Done**:
  - Normal hand: silent consensus, balance commits as today.
  - Synthetic divergence: hand still settles, popup shown, log says divergent, `disputed_hands` row inserted.
  - Peer disconnected mid-hand without sending receipt: hand still settles, popup shown listing missing nicks, log says incomplete, `disputed_hands` row inserted with `reason='MISSING'`.
  - No misdeal flow triggered by any consensus failure (existing misdeal flow untouched).

---

## 12. Open questions deferred to v2

1. **Cross-device identity import/export.** Allow a user to copy their `identity.ed25519` to another machine via an encrypted exportable bundle protected by a passphrase.
2. **Identity revocation broadcast.** Allow a user to publish a signed "revocation" that propagates and forces all peers to re-pin on next encounter.
3. **Hardware-backed identity storage.** Integrate with OS keychains (macOS Keychain, Windows DPAPI, Linux libsecret).
4. **At-rest encryption of `identity.ed25519`** with user passphrase.
5. **Public verifier tool.** Standalone JAR that takes a `disputed_hands.receipts` blob and a list of pubkeys, verifies signatures, prints the chain.
6. **Threshold receipts for very large meshes** (relaxation of strict unanimity if mesa size > 12).
7. **Chain recovery during mid-hand reconnection.** If a peer disconnects mid-hand and reconnects, they currently cannot rejoin the in-progress hand (master's existing recovery flow already gives up on mid-hand recovery). The new identity layer inherits this behavior: the reconnecting peer will not be able to sign a receipt for the in-progress hand, which closes as MISSING for them. The next hand starts cleanly. A proper chain-replay-on-reconnect protocol is deferred to v2.
8. **Signing SHOW_CARDS at showdown.** Today the player broadcasts `sra_unlock` in plain text; the per-hand SRA cascade is sufficient to bind the revealed cards to the committed deck. Adding Ed25519 signature on showdown reveal would give non-repudiation of card reveals but adds protocol complexity. Deferred.

---

## 13. Glossary

- **TOFU** — Trust On First Use. Accept a key the first time, pin it. SSH-style.
- **PAKE** — Password-Authenticated Key Exchange. Handshake using a shared password to authenticate without revealing it.
- **Domain separator** — Unique string prefix in every signature, distinguishing message types so a signature for one purpose cannot be replayed in another.
- **Ratchet** — One-way state update where each step depends on the previous. Reordering impossible without breaking the chain.
- **Receipt** — Signed commitment by a peer to a final state value, archivable as evidence.
- **OOB (Out-of-Band)** — Communication channel separate from the system being secured (e.g. WhatsApp call to verify a fingerprint shown in the CoronaPoker UI).
- **NFC / NFD** — Unicode Normalization Forms (Composed / Decomposed). Same logical character can be byte-different across systems unless normalized.
