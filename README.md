<div align="justify">

<i>Once upon a time, before ChatGPT, some humans coded for pleasure...</i>

<h1 align="center">CoronaPoker</h1>

<p align="center">
  <a href="https://GitHub.com/Naereen/StrapDown.js/graphs/commit-activity"><img src="https://img.shields.io/badge/Maintained%3F-yes-green.svg" alt="Maintenance"></a>
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPL v3"></a>
</p>

This is the project of a perfectionist, who one day during the confinement of COVID19, came up with the idea of developing the most complete and fun open source game of Texas hold'em for his friends. I hope you enjoy playing it as much as I enjoy programming it. Carpe diem.

<p align="center"><a href="https://github.com/tonikelope/coronapoker/releases/latest" target="_blank"><img src="https://raw.githubusercontent.com/tonikelope/megabasterd/master/src/main/resources/images/linux-mac-windows.png"></a></p>

<h1 align="center"><a href="https://github.com/tonikelope/coronapoker/releases/latest"><b>DOWNLOAD CORONAPOKER</b></a></h1>

https://github.com/tonikelope/coronapoker/assets/1344008/88ee3491-459f-43e7-8f62-3567c593482d


## Features

### Cryptographic security
- Zero-Trust deck protocol: every card is shuffled and locked by every player through Mental Poker EC-SRA over Curve25519. Nobody — not even the host — can see another player's pocket cards or peek at the community cards before they are revealed. Each hand is shuffled fresh by all participants, so card distribution is unbiasable.
- End-to-end encryption between players (ECDH key exchange → AES-256-CBC + HMAC-SHA256).
- Password-bound channel keys: when the game is password-protected, the symmetric channel keys are cryptographically tied to the password (HMAC-SHA512), so an attacker on the network path cannot mount a MITM even with full visibility of the wire.
- Pure P2P: no central servers, no telemetry, no third parties logging anything. Just you and your friends.

### Gameplay
- Cross-platform: Windows, macOS, Linux.
- 2 to 10 players: any mix of humans and bots.
- Bots with 3 difficulty levels (Easy / Medium / Hard) and adaptive personalities (NIT, STATION, TAG, LAG) that c-bet, semi-bluff, slow-play traps, tilt, and adjust to each opponent's stats.
- ALL-IN side pots, fully supported.
- Dead-button rule (BB / SB / Button) for fair short-handed play.
- IWTSTH ("I Want To See That Hand") and Rabbit Hunting, toggleable mid-game.
- REBUY mid-game.
- Blinds fully adjustable by the host during play (manual or scheduled doubling).
- Pause anytime; add new players to a running session.

### Reliability
- Crash-tolerant: a game can be resumed from the exact stop point after network or power failure.
- Hand history: game log and per-player statistics persisted on disk.

### UX
- Intuitive interface with comfortable keyboard shortcuts.
- Global in-game zoom (UHD supported).
- 3 view modes for any screen size (normal, compact, super-compact) + low-brightness mode.
- Cool sounds, decks, mats, cinematics and 3D card effects.
- Full i18n support (English / Spanish out of the box).

### Communication
- Waiting-room chat with emojis, custom GIFs and URL preview.
- In-game fast chat with TTS (text-to-speech) and inline GIF sending.

### Customization
- Shareable MODs: custom fonts, decks, mats, sounds, cinematics.

## Build from source

Requirements: JDK 11 or newer, Apache Maven 3.x.

```bash
git clone https://github.com/tonikelope/coronapoker.git
cd coronapoker
mvn clean package
```

The runnable jar is generated at `target/CoronaPoker-<version>-jar-with-dependencies.jar`. Launch it with:

```bash
java -jar target/CoronaPoker-<version>-jar-with-dependencies.jar
```
