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

# Features

## 🔐 Security & cryptography

CoronaPoker was built with one non-negotiable principle: **nobody should ever be able to cheat, spy or tamper — including the host.**

### Zero-trust deck protocol
Every card is shuffled and locked collectively by **every player at the table** through a Mental Poker EC-SRA protocol implemented from scratch over **Curve25519** (with X25519 clamping and a radix-16 scalar-mult core). No single participant — not even the host — can see another player's pocket cards or peek at community cards before they are legitimately revealed. Each hand is re-shuffled collectively, so card distribution is verifiable and unbiasable.

### End-to-end encrypted channels
All traffic between players is encrypted with **AES-256-CBC + HMAC-SHA256** over keys negotiated via **ECDH key exchange**. Anyone on the network path sees only opaque blocks — game state, chat messages and player actions are unreadable.

### Password-bound keys
In password-protected games the symmetric channel keys are derived from the password with **HMAC-SHA512** over the raw ECDH shared secret. A passive MITM cannot complete the handshake without the password.

### Fully decentralised
Pure P2P, no central servers, no accounts, no telemetry and no third parties logging anything. Your game exists only between the machines at the table.

---

## 🌐 Networking & resilience

- **Automatic UPnP port mapping** — when you host a game, CoronaPoker tries to open the listening port on your router and cleans it up when the table closes. Manual port forwarding works as a fallback.
- **Adjustable listening port** — defaults to 7234, configurable per game.
- **Optional password protection** for the table itself, on top of channel encryption.
- **Automatic reconnection** — if a player drops, the client tries to rejoin and pick up exactly where it left off; the table waits during a configurable grace window before asking the host whether to remove them.
- **Crash recovery** — every hand is checkpointed to a local **SQLite** database, so a game can resume from the exact stop point after a crash, power loss or reboot.
- **Recent-server list** — quick rejoin to tables you have already connected to.

---

## 🃏 Texas Hold'em — rules & table

A rules-correct No-Limit Hold'em implementation, focused on private home games rather than tournament structures.

| Feature | Details |
|---|---|
| **Players** | 2 to 10 — any mix of humans and bots |
| **ALL-IN & side pots** | Fully supported with correct pot splits |
| **Dead-button rule** | BB / SB / Button handled correctly when players leave |
| **Action timer** | Per-turn thinking time with on-screen countdown bar |
| **Rebuy** | Mid-game rebuy, host-toggleable, scheduled for the next hand |
| **Blinds** | Adjustable by the host live — manual change or scheduled automatic doubling |
| **Pause & join** | Pause anytime; new players can be added to a running session |
| **IWTSTH** | "I Want To See That Hand" — force showdown after an uncalled all-in, toggleable |
| **Rabbit Hunting** | Reveal what would have come on the remaining streets, toggleable |
| **Spectator mode** | Busted-out players can stay at the table and watch the rest of the session |
| **Hand generator** | Build specific board / hole-card scenarios for study or practice |

---

## 🤖 Bots

Not the fold-everything kind. CoronaPoker bots play like real opponents.

- **4 difficulty levels** — Easy, Medium, Hard, Expert
- **3 skill tiers** assigned per bot, weighted by difficulty:
  - 🍿 **Recreational** — loose, emotional, prone to tilt after losses
  - 🛠️ **Regular** — solid fundamentals, occasional mistakes
  - 🦈 **Shark** — disciplined, exploitative, calculates fold equity
- **4 adaptive personalities** that shift dynamically with stack pressure (M-ratio), tilt and table dynamics:
  - 🗿 **NIT** — tight, conservative, rarely bluffs
  - 📡 **STATION** — calls everything, impossible to read
  - 🎯 **TAG** — tight-aggressive, the textbook grinder
  - 💥 **LAG** — loose-aggressive, unpredictable and relentless
- **Real poker engine** built on the Alberta hand evaluator + hand-potential — true equity, not lookup tables
- Multi-street planning: **c-bets, semi-bluffs, slow-played traps, float plays, scare-card reads, MDF bluff-catching** and range-aware aggression
- **Per-bot opponent tracking** (VPIP / PFR / AF / fold-to-cbet) — they remember your tendencies and exploit them
- **Heads-up vs multi-way awareness** — ranges and bluff frequencies are gated to the table size
- **Calibrated mistake injection** scaled by difficulty — Expert is razor-sharp, Easy makes human-shaped errors in the right spots
- **Validated through AAA QA** — every release is benchmarked against fixed-strategy opponents over tens of thousands of hands before shipping

---

## 💬 Chat, voice & social

### Waiting room chat
Full chat while the game is being set up, with a built-in **emoji picker (~1.800 emojis with recent-use history)**, inline **GIF support** with automatic format conversion (bundled `gifsicle` binary for Windows / Linux / macOS) and **automatic image previews** for URLs pasted into the chat.

### In-game fast chat
A side panel for sending quick messages mid-hand without leaving the action, with **text-to-speech readout** of incoming messages and inline GIFs.

### Player identity
Each player gets a deterministic **identicon avatar** derived from their cryptographic key, so the same player is visually recognisable from session to session without any central account system.

### Action sounds & character voices
Distinct sounds for every action (deal, check, call, raise, fold, all-in, showdown, winner) plus comedy voice clips that can be triggered on common actions. Everything is toggleable and replaceable.

---

## 📊 Statistics & history

- **Local SQLite database** stores every hand played: timestamps, actions per street, board, stacks, winner and pot.
- **Per-session and per-hand stats viewer** — browse past games, replay any single hand and inspect aggregated metrics per player.
- **Live in-game log** — real-time scrolling action log for the current hand.
- **All data stays on your machine** — no cloud sync, no upload, ever.

---

## 🎨 Customization & MOD packs

Every visual and audio asset is replaceable through redistributable MOD packs:

- **Card decks** — 4 themes bundled (`coronapoker`, `interstate60`, `goliat`, `goliat4`), each in both animated GIF and static HQ variants.
- **All-in cinematics** — 9 bundled movie clips that play on every all-in; MOD packs can replace the whole set.
- **Sound packs** — full per-language sound trees (English / Spanish) for actions, showdowns, voices and ambient music.
- **Fonts** — custom display fonts (e.g. McLaren bundled by default).
- **Background music** — context-aware tracks for waiting room, gameplay and stats screen.
- **One-file distribution** — drop a single MOD pack in the right folder and it is picked up on next launch.

---

## 🖥️ UX & display

- **Three table layouts** — Normal, Compact and Super-Compact — to fit anything from a 13" laptop to a 4K monitor.
- **Global zoom** with keyboard shortcuts and an optional auto-zoom that fits the table to the window.
- **Low-brightness overlay** for late-night sessions.
- **Animated 3D card deal** with toggleable animation.
- **Action confirmation** — optional safety prompt before fold / all-in / raise.
- **Auto-action buttons** — pre-select your action for the next turn (check, call any, fold).
- **In-game screenshots** of the table state.
- **Keyboard shortcuts** for every common action with a built-in reference dialog.

---

## 🌍 Internationalization

- Bundled languages: **English** and **Spanish**.
- All UI strings, dialogs, action labels and contextual sounds are localised — including the comedy voice packs.

---

## 🧱 Stack

- **Java** (compiles with JDK 11+, current development on JDK 25)
- **Swing** UI with NetBeans Matisse forms
- **Maven** build, single self-contained shaded jar
- **Alberta** poker hand evaluator for true equity computation
- **SQLite** (via `sqlite-jdbc`) for local hand history
- Pure-Java **EC-SRA / Curve25519** implementation — no native crypto dependencies

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
