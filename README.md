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
Before a hand begins, Panoptes establishes a secure, forward-secret communication channel and locks its own memory to prevent host interference.

* **Encrypted Memory Vault (PanoptesVault):** All sensitive game state is continuously encrypted in RAM using ChaCha20. The vault's decryption keys are dynamically managed and intrinsically tied to the engine's execution integrity, meaning any attempt to dump or scrape memory yields unusable noise.
* **Execution Flow Protection:** The engine utilizes polymorphic execution techniques and active environmental checks to detect and neutralize runtime analysis, debuggers, or memory hooking attempts.
* **Hardware Anchors (X25519):** Each client generates an asymmetric identity keypair via X25519 Elliptic Curve Diffie-Hellman to anchor their session.

### Phase 1: Distributed Entropy & The Megapacket
The game begins by ensuring no single entity—not even the host—can dictate or predict the deck's order.

* **Multi-Party Entropy:** Every active player client generates a local cryptographic seed and submits it alongside their Public Key to the host.
* **The Master Shuffle:** The host's native engine aggregates all player seeds using deterministic XOR operations, combining them with a server-side seed and a hidden Shuffle Key. This resulting Master Seed drives a ChaCha20 keystream applied to a strict Fisher-Yates shuffle.
* **Key Encapsulation Mechanism (KEM):** Pocket cards are never transmitted in plaintext. Panoptes uses a hybrid KEM (X25519 + ChaCha20 + Poly1305) to encrypt each player's cards against their specific Public Key.
* **The Megapacket & Sponge MAC:** The server constructs a single, immutable data payload containing the public keys, KEM envelopes, and an encrypted capsule of the remaining deck. This packet is sealed using a cryptographic Sponge construction (`STATE_CHAIN_MAC`) and broadcasted to all peers as the irrefutable genesis state of the hand.

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

### Phase 4: The Action Chain (Betting Integrity)
Betting sequences are protected against reordering, injection, or modification.

* **Sponge Construction:** Every action (Bet, Fold, Call) is signed with a Poly1305 MAC and absorbed into the running cryptographic Sponge hash (`STATE_CHAIN_MAC`).
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
