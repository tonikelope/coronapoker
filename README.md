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
**Cryptographic Consensus, Stateless Auditing & Anti-Tamper Protocol for Decentralized P2P Environments**
<p align="center"><img src="https://raw.githubusercontent.com/tonikelope/coronapoker/master/src/main/resources/images/panoptes_logo.jpg" height="400" alt="Panoptes Zero-Trust Engine Logo"></p>

## 📌 Executive Summary
Panoptes is a cryptographic engine written in C designed to enforce absolute mathematical fairness in decentralized Peer-to-Peer (P2P) card games.

In traditional client-server topologies, players implicitly trust a central authoritative server. In a purely decentralized P2P model like CoronaPoker, one of the players must act as the host. This introduces the *malicious host vulnerability*: the host has physical access to the RAM where the deck is shuffled and the game state is maintained, allowing them to theoretically peek at hidden cards, alter the deck, or manipulate betting outcomes.

Panoptes was engineered to eradicate this vulnerability entirely. It achieves this by enveloping a rigidly transparent **Zero-Trust Cryptographic Protocol** higly defended with a custom **anti-cheat**. **The default policy under which Panoptes operates is that all players, including the host, are treated as compromised nodes at the Java and JVM level**, either because they are acting maliciously or because they are controlled by an external attacker. Therefore, all players are mathematically blind to the game state until the network achieves mutual consensus. Every action is sealed, every state transition requires cryptographic consent, and every finished hand is statelessly audited by all peers.

---

## 🛡️ PART I: THE ZERO-TRUST CRYPTOGRAPHIC PROTOCOL

The core achievement of Panoptes is its state machine. A standard game progresses through distinct cryptographic phases, protecting the integrity of the deck, the community cards, and the sequence of bets without relying on a trusted third party.

### Phase 0: Environment Integrity & Secure Enclaves
<img width="1404" height="870" alt="imagen" src="https://github.com/user-attachments/assets/2a1f20b0-2678-4e6b-9858-45b0138b5321" />

Before a hand begins, Panoptes establishes a secure, forward-secret communication channel and locks its own memory to prevent host interference.

* **Build-Time Cryptographic DNA:** A unique 32-byte deterministic polymorphic seed is injected by the Python Builder into the C engine at compile-time, ensuring no two versions share the same internal logic.
* **OS-Native Environment Anchoring anti-TOCTOU measures:** The kernel enforces physical file locks and continuous Inode/Hash validation on all critical assets (.jar and .dll).
* **Hybrid JIT Key Forging Build-time:** Logic is fused with live boot hardware entropy to forge a unique ChaCha20 JIT Vault Key. 
* **Polymorphic Memory Shield:** The engine spawns several isolated and encrypted memory structs (all decoys, 1 true state), constantly mutating and shifting to mitigate memory-dumping attacks.
* **Ephemeral Session Isolation X25519:** Session Keys are generated and sealed strictly within the encrypted enclave, establishing Perfect Forward Secrecy (PFS) without the private key ever touching plain-text RAM.

### Phase 1: Distributed Entropy & The Hand Commitment
<img width="5640" height="4108" alt="imagen" src="https://github.com/user-attachments/assets/9d5c810e-01b8-4696-9270-21a73da914d7" />

The game begins by ensuring no single entity—not even the host—can dictate or predict the deck's order.

* **Collaborative Multi-Party Entropy:** Entropy contributions from all players and the Host are fused into a global pool. This ensures that no single entity can control or predict the final deck order.
* **Internal Entropy Blinding:** The engine performs a final "blinding" operation by mixing external seeds with OS hardware noise and the Polymorphic Root Seed. This prevents the Host from "mining" favorable decks even if they control the OS.
* **Immutable Hand Commitment:** The resulting deck state is digested via Mix Sponge (ChaCha20-based) and signed with Poly1305. Once the "Hand Commitment MAC" is generated, the future of the hand is mathematically set in stone.
* **Hand State Blockchain Genesis:** The commitment is ingested as the Hand Genesis Block. This anchors the initial deal as the immutable root of a private, ephemeral blockchain that tracks every subsequent action in the hand.
* **Zero-Knowledge Distribution:** Sensitive data is sealed in X25519-encrypted envelopes. Each player can only decrypt their own pocket cards and their unique shards (Splits) of the street keys (Flop, Turn, River).
* **Decentralized Key Sharding:** Street keys are broken into fragments using XOR-based secret sharing. Revelation of board cards requires a decentralized consensus, as no single player or Host holds a complete street key.

### Phase 2: Cryptographic Escrow & Compartmentalization
While players decrypt their pocket cards locally, the remaining community cards (the board) reside in the host's memory.

* **The Escrow:** The Flop, Turn, and River cards are locked in an encrypted Escrow payload using independent, isolated ChaCha20 keystreams.
* **The Blind Host:** At this stage, the host application possesses the encrypted Escrow data but mathematically lacks the keys required to decrypt it. The host is strictly isolated and just as blind as the connected clients.

### Phase 3: Token Consensus & Scorched Earth
When a betting round concludes and community cards must be revealed, the host cannot unilaterally query the engine for the cards.

* **Fractional Keys (Tokens):** Inside their original Megapacket envelope, every player received cryptographic "Street Tokens" (for the Flop, Turn, and River).
* **Consensus Aggregation:** The host requests the specific street token from all active clients. The native engine aggregates these tokens via XOR operations. Only when all required tokens are combined can the engine reconstruct the ephemeral key needed to unlock that specific street in the Escrow.
* **Scorched Earth Defense:** The moment a street is decrypted and broadcasted, the underlying ephemeral keys are permanently wiped from the Vault. If an attacker attempts to extract the master shuffle key prematurely, Panoptes proactively burns the street tokens, locking the game state forever.
* **The Exit Testament:** If a player legitimately disconnects mid-hand, the engine performs a permanent wipe of their session keys and generates a cryptographic "Testament". This signature allows the remaining peers to verify the exit and close the state audit without failing the final validation.

### Phase 4: The Action Blockchain (Betting Integrity)
Betting sequences are protected against reordering, injection, or modification.

* **Sponge Construction:** Every action (Bet, Fold, Call) is signed with a Poly1305 MAC and absorbed into the running cryptographic Sponge hash (`HAND_STATE_BLOCKCHAIN`).
* **Zero-Trust Bot Delegation:** Server-side bots operate under the exact same zero-trust rules as human players. Bot actions are deterministically signed via their delegated private keys, ensuring the host cannot forge or silently alter bot behavior.
* **Atomic Chain:** If the host attempts to drop a player's bet, inject a fake action, or manipulate the betting phase, the state hash will instantly desynchronize, invalidating the hand across the network.

### Phase 5: Showdown & The Stateless Audit
When the hand reaches the end, the system must definitively prove that the host did not manipulate the envelopes, the community cards, or the original shuffle.

* **Master Key Revelation:** At showdown, players reveal the `SHUFFLE_KEY_SHARE` that was hidden inside their original digital envelopes.
* **Stateless Client Verification:** Every client runs a localized, stateless audit. The client inputs the original Megapacket, the reconstructed Master Seed, and the final community cards into their own Panoptes engine.
* **Active Telemetry Assessment:** Concurrently, Panoptes evaluates the deep-system integrity checks performed during the hand (detecting virtualization, unauthorized memory access, or network spoofing), acting as a final gatekeeper for the host's legitimacy.
* **The Avalanche Effect:** The client engine re-simulates the entire hand. If the host manipulated a single bit during the game, the cryptographic hashes will avalanche, producing a massive mismatch. The client will instantly flag the host as compromised and mathematically prove the cheating attempt to the rest of the P2P swarm.

## 🛑 PART II: THE ANTI-TAMPER & ANTI-CHEAT ENGINE
While the Cryptographic Protocol ensures that a host cannot mathematically cheat within the rules of the protocol, the Panoptes Anti-Cheat Engine (PACE) is a robust Ring-3 defense system written in C designed to prevent the host from reverse-engineering the engine, dumping RAM, or hooking the process to steal keys.

*(Note: The exact operational vectors of PACE are deliberately abstracted to maintain operational security).*

<p align="center"><img src="panoptes.png" height="500"></p>

> ⚠️ **SECURITY NOTICE: CLOSED-SOURCE ENGINE** Although CoronaPoker is open-source (GPLv3), the source code for the `Panoptes` zero-trust and anti-cheat engine library remains closed-source. This is a deliberate, non-negotiable security measure. Pre-compiled binaries are provided for supported platforms.

</div>
