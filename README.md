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


--

## 🔐 Security

PokerNight was built with one non-negotiable principle: **nobody should ever be able to cheat, spy, or tamper — including the host.**

### Zero-Trust deck protocol
Every card is shuffled and locked by **every player** through the Mental Poker EC-SRA protocol over **Curve25519**. No single participant — not even the host — can see another player's pocket cards or peek at community cards before they are revealed. Each hand is re-shuffled collectively, making card distribution provably unbiasable.

### End-to-end encryption
All traffic between players is encrypted via **ECDH key exchange → AES-256-CBC + HMAC-SHA256**. Nobody on the network path can read game state, chat messages, or player actions.

### Password-bound channel keys
In password-protected games, symmetric channel keys are cryptographically derived from the password using **HMAC-SHA512**. An attacker with full visibility of the wire cannot mount a MITM attack — the keys simply won't match without the password.

### Fully decentralised
Pure P2P. No central servers. No telemetry. No third parties logging anything. Your game exists only between the machines at the table.

---

## 🃏 Gameplay

A complete, rules-correct Texas Hold'em implementation with everything you'd expect at a real table — and a few things you wouldn't.

| Feature | Details |
|---|---|
| **Players** | 2 to 10 — any mix of humans and bots |
| **ALL-IN & side pots** | Fully supported with correct pot splits |
| **Dead-button rule** | BB / SB / Button handled correctly for short-handed play |
| **IWTSTH** | "I Want To See That Hand" — toggleable mid-game |
| **Rabbit Hunting** | See what would have come — toggleable mid-game |
| **Rebuy** | Mid-game rebuy at any point |
| **Blinds** | Fully adjustable by the host during play — manual or scheduled doubling |
| **Pause & join** | Pause anytime; add new players to a running session |

---

## 🤖 Bots

Not the fold-everything kind. PokerNight bots play like real opponents.

- **3 difficulty levels** — Easy, Medium, Hard
- **4 adaptive personalities:**
  - 🗿 **NIT** — tight, conservative, rarely bluffs
  - 📡 **STATION** — calls everything, impossible to read
  - 🎯 **TAG** — tight-aggressive, the textbook grinder
  - 💥 **LAG** — loose-aggressive, unpredictable and relentless
- Bots **c-bet, semi-bluff, slow-play traps, and tilt** under pressure
- They **track and adjust** to each opponent's tendencies over time

---

## 💬 Communication

### Waiting room
Full chat with emoji support, custom GIFs, and automatic URL previews while the game is being set up.

### In-game fast chat
Send quick messages mid-hand with **text-to-speech (TTS)** readout and inline GIF support — without ever leaving the action.

---

## 🎨 Customization & Modding

Every visual and audio element is replaceable. Create and share **MOD packs** containing:

- Custom card decks & table mats
- Fonts, sounds, and cinematics
- Shareable as a single distributable file — drop it in and play

---

## ⚙️ Reliability

- **Crash-tolerant** — a game resumes from the exact stop point after any network or power failure
- **Hand history** — full game log and per-player statistics persisted to disk after every hand

---

## 🖥️ UX

- Intuitive interface with **keyboard shortcuts** for every common action
- **Global in-game zoom** — UHD / 4K display support
- **3 view modes** — Normal, Compact, Super-Compact — for any screen size
- **Low-brightness mode** for late-night sessions
- High-quality sounds, cinematic animations, and **3D card effects**
- **Full i18n** — English and Spanish out of the box

---

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
