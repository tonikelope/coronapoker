<div align="justify">

<i>Once upon a time, before ChatGPT, some humans coded for pleasure...</i>

<h1 align="center">CoronaPoker</h1>

<p align="center">
  <a href="https://GitHub.com/Naereen/StrapDown.js/graphs/commit-activity"><img src="https://img.shields.io/badge/Maintained%3F-yes-green.svg" alt="Maintenance"></a>
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPL v3"></a>
</p>

This is the project of a perfectionist, who one day during the confinement of COVID19, came up with the idea of developing the most complete and fun open source game of Texas hold'em for his friends. I hope you enjoy playing it as much as I enjoy programming it. Carpe diem.

<p align="center"><a href="https://github.com/tonikelope/coronapoker/releases/latest" target="_blank"><img src="https://raw.githubusercontent.com/tonikelope/megabasterd/master/src/main/resources/images/linux-mac-windows.png"></a></p>

<p align="center"><b>(Proudly) developed with:</b><br><img src="java_swing_mini.png" height="100"></p>

<h1 align="center"><a href="https://github.com/tonikelope/coronapoker/releases/latest"><b>DOWNLOAD CORONAPOKER</b></a></h1>

https://github.com/tonikelope/coronapoker/assets/1344008/88ee3491-459f-43e7-8f62-3567c593482d


## Some features:
- Cross platform.
- Secure by design: Zero-Trust Architecture + a modest (but pain-in-the-ass) Ring-3 anticheat.
- No central servers nor third parties logging things (just you and your friends).
- Point-to-point encryption (DH + AES 128).
- Password protected games.
- Up to 10 simultaneous human/bot players.
- Intuitive interface (with comfortable key shortcuts).
- Global in-game zoom (UHD resolution supported).
- TRUE RANDOM shuffle (<a href="https://github.com/tonikelope/coronapoker/raw/master/shuffle.pdf">MORE INFO</a>).
- Cool sounds, decks, mats, cinematics and 3D card effects.
- ALL-IN side pots fully supported.
- "Dead button" rule for BB/SB/DE positions.
- IWTSTH rule available (can be enabled/disabled by the host during the game).
- RABBIT HUNTING (can be enabled/disabled by the host during the game).
- All blinds stuff adjustable by the host during the game.
- REBUY available during games.
- Waiting room chat with emojis, custom gifs and urls support.
- Text to speech fast chat and sending of custom gifs during the game.
- 3 view modes for different screen sizes (normal, compact, and super compact) and low brightness mode.
- Very high tolerance to network/power failures (games can be resumed from exact stop point).
- It is possible to pause the game at any time and add new players.
- Game log and statistics.
- i18n support (english and spanish).
- Customizable: create and share your MODs with custom font, decks, sounds and cinematics.

# 👁️ PANOPTES ZERO-TRUST POKER ENGINE
**Cryptographic Consensus, Stateless Auditing, and Anti-Cheat Mechanisms for Hostile P2P Environments**
<p align="center"><img src="https://raw.githubusercontent.com/tonikelope/coronapoker/master/src/main/resources/images/panoptes_logo.jpg" height="400" alt="Panoptes Zero-Trust Engine Logo"></p>

## 📌 Executive Summary
Panoptes is a cryptographic engine written in C designed to enforce absolute mathematical fairness in decentralized Peer-to-Peer (P2P) card games.

In traditional client-server topologies, players implicitly trust a central authoritative server. In a purely decentralized P2P model like CoronaPoker, one of the players must act as the host. This introduces the *malicious host vulnerability*: the host has physical access to the RAM where the deck is shuffled and the game state is maintained, allowing them to theoretically peek at hidden cards, alter the deck, or manipulate betting outcomes.

Panoptes was engineered to eradicate this vulnerability entirely. It achieves this by enveloping a rigidly transparent **Zero-Trust Cryptographic Protocol** higly defended with a custom **anti-cheat**. **The default policy under which Panoptes operates is that all players, including the host, are treated as compromised nodes at Java level**, either because they are acting maliciously or because they are controlled by an external attacker. Therefore, all players are mathematically blind to the game state until the network achieves mutual consensus. Every action is sealed, every state transition requires cryptographic consent, and every finished hand is statelessly audited by all peers.

---

## 🛡️ PART I: THE ZERO-TRUST CRYPTOGRAPHIC PROTOCOL

The core achievement of Panoptes is its state machine. A standard game progresses through distinct cryptographic phases, protecting the integrity of the deck, the community cards, and the sequence of bets without relying on a trusted third party.

### Phase 0: Environment Integrity & Secure Enclaves
<img width="1404" height="870" alt="imagen" src="https://github.com/user-attachments/assets/2a1f20b0-2678-4e6b-9858-45b0138b5321" />

Before any gameplay occurs, Panoptes establishes a secure execution context, locking its own memory to prevent host interference, memory-dumping, or runtime instrumentation.

* **Build-Time Cryptographic DNA:** A unique 32-byte deterministic polymorphic seed (`CHAOS_SEED`) is injected by the compilation pipeline into the native C engine. This ensures no two compiled binaries share the same internal memory layout or key derivation logic.
* **Hybrid JIT Key Forging:** Upon initialization, compile-time logic is fused with live, hardware-level OS entropy to forge a unique ChaCha20 key. This key encrypts the `PanoptesVault`—the engine’s secure memory enclave.
* **Polymorphic Memory Shield & Decoys:** The engine spawns multiple isolated memory structs (several decoys and one true state), constantly applying ChaCha20 keystreams to mitigate RAM-scraping attacks.
* **Continuous Telemetry & The Deadman Switch:** Background Guardian threads continuously monitor CPU cycle drift (`sabueso_rdtsc`), inline hooks, and attached debuggers. Any anomaly instantly flips a mathematical `v.poison` bit, irrevocably corrupting the Vault's cryptographic outputs.

### Phase 1: Distributed Entropy & The Hand Commitment
<img width="5640" height="4108" alt="imagen" src="https://github.com/user-attachments/assets/491afd04-ae89-42d3-af4d-fc782fa04c6d" />

The hand begins by ensuring no single entity—not even the server—can dictate, predict, or manipulate the deck's order.

* **Collaborative Multi-Party Entropy:** Entropy seeds from all active peers and the host are fused via XOR into a global pool. This mathematically guarantees that no single node can bias the final deck configuration.
* **In-Enclave Fisher-Yates Shuffle:** The combined entropy seeds a ChaCha20 keystream, which drives a deterministic Fisher-Yates shuffle strictly inside the encrypted Vault.
* **Zero-Knowledge KEM Envelopes:** Sensitive data is sealed using an X25519 Key Encapsulation Mechanism (KEM). Each player receives an envelope containing their pocket cards and their unique cryptographic shards of the community street keys.
* **The Escrow Ciphertext:** The community cards (Flop, Turn, and River) are encrypted with ChaCha20 and placed in a public "Escrow." No individual holds the complete key to unlock them.
* **Genesis of the Action Blockchain:** The entire initial state (public keys, escrow, encrypted envelopes) is digested via a Sponge Construction to create the `HAND_STATE_BLOCKCHAIN`. This hash anchors the initial deal as the immutable root of a private, ephemeral blockchain.
  
### Phase 2: The Cryptographic Betting Loop (Action Blockchain)

During the active betting rounds, gameplay actions are continuously verified and sealed into the local blockchain, preventing reordering, injection, or dropping of bets.

* **Sponge-Based State Absorption:** Every gameplay action (Bet, Fold, Call) includes the exact amount, the current street, and a microsecond-precision hardware timestamp. This payload is absorbed into the running `HAND_STATE_BLOCKCHAIN` via a cryptographic Sponge function.
* **Context-Aware Signatures:** Actions are broadcasted with a Poly1305 Message Authentication Code (MAC). The key for this MAC is dynamically derived from the *current* state of the blockchain. If the host attempts to drop a previous bet, the state hashes will desynchronize, and all subsequent signatures will mathematically fail.
* **Zero-Trust Bot Delegation:** Server-side bots operate under the exact same zero-trust strictures as human clients. Bot actions are signed via their delegated X25519 private keys, explicitly preventing the host from forging or silently overriding bot behavior.

### Phase 3: Token Consensus & Escrow Revelation

When a betting round concludes and community cards must be revealed, the protocol enforces a distributed cryptographic consensus. The server is powerless to query the upcoming cards unilaterally.

* **Fractional Keys (XOR Shards):** During Phase 1, every player received fragmented "Street Tokens." To reveal the Flop, Turn, or River, the host must request the specific token from all active clients.
* **Consensus Aggregation:** The native engine aggregates these tokens via branchless XOR operations. Only when all active tokens are combined can the engine reconstruct the ephemeral ChaCha20 key required to decrypt that specific street from the Escrow.
* **Scorched Earth Defense:** The moment a street is decrypted, the underlying ephemeral tokens are permanently wiped from the Vault. If a malicious host attempts to extract tokens out-of-phase or brute-force the Escrow prematurely, Panoptes proactively burns the state, triggering the `v.poison` mechanism.
* **The Exit Testament & Vault Lobotomy:** If a player legitimately disconnects mid-hand, the engine performs a "Vault Lobotomy"—a permanent, physical zeroing of their session keys in RAM, combined with an intentional poisoning of the state. Before dying, it generates a cryptographic "Testament." This allows the remaining P2P swarm to verify the legitimate exit, ensuring the server cannot hijack the lobotomized session (Zombie Peer) while allowing the remaining players to bypass the missing token requirement.

### Phase 4: Showdown & The Stateless Forensic Audit

Upon hand completion, the system executes a deterministic, localized proof to guarantee no manipulation occurred during the hand's lifecycle, followed by a mandatory peer-to-peer cross-validation.

* **Master Key Revelation:** At showdown, players reveal the `SHUFFLE_KEY_SHARE` that was hidden inside their Phase 1 envelopes.
* **Stateless Client Verification:** Every client runs a localized forensic audit. The engine inputs the original Genesis Megapacket, the newly reconstructed Master Seed, and the final community cards.
* **Cryptographic Avalanche:** The client engine re-simulates the entire hand from genesis to showdown. It independently derives the deck, the escrow, and the blockchain hashes. If the host or a colluding peer manipulated a single bit during the game, the hashes will avalanche, producing a massive internal mismatch.
* **Cross-Verification Receipts:** Upon a successful local audit, each client generates a Poly1305-signed "Receipt" of their final state hash and broadcasts it to the table. Every player must verify the receipts of all other players. This mathematically proves that the host did not split the game state (e.g., sending divergent hand histories to different players).
* **Terminal Poisoning (Quarantine):** If the local audit fails, or if a peer's receipt does not match the consensus, the engine triggers a terminal poisoning of the state (`v.poison |= final_fail`). The client instantly generates invalid MACs for all future network traffic, mathematically isolating the compromised host and proving the cheating attempt to the rest of the P2P mesh.

## 🛑 PART II: ANTI-CHEAT ENGINE
While the Cryptographic Protocol ensures that a host cannot mathematically cheat within the rules of the protocol, the Panoptes Anti-Cheat Engine is a robust Ring-3 defense system written in C designed to prevent the host from reverse-engineering the engine, dumping RAM, or hooking the process to steal keys.

<p align="center"><img src="panoptes.png" height="500"></p>

> ⚠️ **SECURITY NOTICE: CLOSED-SOURCE ENGINE** Although CoronaPoker is open-source (GPLv3), the source code for the `Panoptes` zero-trust and anti-cheat engine library remains closed-source. This is a deliberate, non-negotiable security measure (by obscurity, yes). Pre-compiled binaries are provided for supported platforms.

</div>
