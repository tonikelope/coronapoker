<h1 align="center">CoronaPoker</h1>

<p align="center">
  <a href="https://github.com/tonikelope/coronapoker/graphs/commit-activity"><img src="https://img.shields.io/badge/Maintained%3F-yes-green.svg" alt="Maintenance"></a>
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPL v3"></a>
</p>

CoronaPoker is an open-source, peer-to-peer No-Limit Texas Hold'em game for
private tables. Play with friends and bots, host your own game and keep your
hand history on your machine.

I started CoronaPoker for friends during the COVID-19 lockdown. It grew into
the poker game I wanted to play and the software project I wanted to build.
**Carpe diem.**

<p align="center"><a href="https://github.com/tonikelope/coronapoker/releases/latest"><strong>Download CoronaPoker</strong></a></p>

## Contents

- [Getting started](#getting-started)
- [Features](#features)
  - [Poker rules and table](#poker-rules-and-table) · [Bots](#bots) · [Networking and recovery](#networking-and-recovery) · [Security](#security)
  - [Chat and audio](#chat-and-audio) · [Poker videos](#poker-videos) · [Appearance and controls](#appearance-and-controls)
  - [Statistics and history](#statistics-and-history) · [Customization and mods](#customization-and-mods)
- [Development](#development)
  - [Architecture](#architecture) · [Build and certify from source](#build-and-certify-from-source) · [Testing and certification](#testing-and-certification)
- [Contributors](#contributors)

<img width="2560" height="1440" alt="coronapoker_screenshot_1794135757102" src="https://github.com/user-attachments/assets/c5a2849c-fe70-4520-96be-17221cdc8a97" />

## Getting started

1. On Windows x64, use the installer with its included Java runtime. For a
   portable install, choose the JAR for your operating system and CPU and
   install Java 17 or newer. Portable builds are available for Windows x64,
   Linux x64 and ARM64, and macOS on Intel and Apple Silicon.
   [Latest downloads](https://github.com/tonikelope/coronapoker/releases/latest)
2. Open the Windows shortcut or run `java -jar "filename.jar"` with the
   downloaded JAR filename.
   You can cancel the startup update check and continue into the game.
3. Create a table or join using the host's address and port.
   Every participant in a game must run the exact same CoronaPoker version.

The interface, dialogs and contextual sounds are available in English and
Spanish.

## Features

### Poker rules and table

Play with 2 to 10 humans and bots at a private No-Limit Hold'em table.
The game supports heads-up play, side pots, all-ins and the dead-button rule.
Players who run out of chips can stay as spectators. The hand generator
shows example deals for every hand rank.

| Setting | What the host can configure |
| --- | --- |
| Stakes | Fixed or variable buy-in, rebuy limits and stack caps, and saved blind structures with timed or hand-based increases |
| Table rules | Ante, voluntary straddle, Rabbit Hunting and I Want To See That Hand |
| Run It Twice | Players in an all-in vote on dealing the remaining board twice. Everyone involved must agree. |
| Session | Turn timer, hand limit, saved presets and whether new players can join a running table |

The betting engine follows Robert's Rules of Poker for deal and showdown
order, minimum raises, reopening after short all-ins and pot eligibility.
When time runs out, the game checks if checking is free and folds if there
is a bet to call. An indivisible cent carries into the next hand, including
any remainder from a Run It Twice split.
[Robert's Rules of Poker (PDF)](robert_rules.pdf)

### Bots

Fill seats with bots at three difficulty levels. They estimate hand equity,
adapt to stack pressure and table size, and track opponents' tendencies.
Custom profiles let the host choose a nickname, avatar, difficulty,
personality and automatic-rebuy preference for each bot.
[Bot AI and decision model](docs/BOTS.md)

### Networking and recovery

- **Host your own table.** Use the local or public address shown in the lobby
  and game log. The default port is 7234. The host can set a table password
  and opt in to a temporary UPnP mapping.
- **Reconnect and recover.** A dropped connection can resume the session and
  replay pending commands in order. Local SQLite checkpoints support recovery
  of an interrupted game. If an active player is definitively lost, the host
  voids the affected hand and refunds the committed bets before recovery.
- **Join during play.** With hot join enabled, a new player enters as
  **Warming up** and becomes playable on the next hand. An authenticated
  returning player keeps their stack.
- **Manage the connection.** Recent addresses are saved. The host can inspect
  link telemetry and review or remove temporary network blocks.

### Security

- **Verifiable dealing.** Each human participant in the hand's cryptographic
  ring verifies the shuffle. Mental Poker keeps private cards sealed until
  their authorized reveal.
- **Identity and signed play.** Persistent Ed25519 identities sign game
  actions. Players can compare fingerprints out of band. A hash chain and
  signed closing receipts protect the hand history and settlement.
- **Encrypted channels.** Post-handshake traffic between each player and the
  host uses authenticated encryption. The table is peer-hosted, with no
  central game account.

The protocol guide covers the Ristretto255 implementation, the Bayer-Groth
verifiable shuffle, receipt checks and recovery.
[Security and cryptography](docs/SECURITY.md)

Click a human player's avatar at the table to compare their identity
fingerprint. The lobby also offers a session-key identicon for checking the
connection with the host. [Identity and identicons](docs/ec-identity-spec.md)

### Chat and audio

Chat in the lobby or at the table with emoji, GIFs, image previews and optional
text-to-speech. Hold **F9** to record a voice note and release to send it.
Recording sends automatically at 15 seconds. Notes can be replayed from chat
and are stored locally. The host controls whether the table allows them.

Right-click the speaker icon for volume, audio output and microphone controls.
Audio settings let you choose which music, effects and comedy voices play.

### Poker videos

Watch YouTube poker videos in the menu, waiting room or at the table.
Playback starts muted. The window closes when a game starts and can be
reopened from the icon next to the table's volume control.

- **Carousel and favorites.** Choose regular videos or Shorts in the player.
  Your format selection and favorites are saved across sessions.
- **Window and opacity.** Drag, resize or maximize the window. The slider in
  the center of the bottom bar lets you see the table through the video.
  The game saves the opacity and remembers the layout for each format.
- **Startup and animations.** Choose whether the player opens at startup
  and whether the window animates when opening or closing.

Configure the player under **Settings > Appearance > Animations and effects**.
[Video controls, platform support and troubleshooting](docs/JCEF_VIDEO_EXPERIMENT.md)

### Appearance and controls

- **Display.** Choose windowed, borderless or exclusive fullscreen. Set the
  frame-rate limit, VSync, scene MSAA and card smoothing. Settings tooltips
  show estimated performance impact for graphics options and effects with
  a meaningful cost.
- **Table.** Set the deck, card backs, felt and lighting. Deal community cards
  by street or all face down at the start. The choice applies from the next
  hand. Individual switches control animations and effects.
- **Input.** Rebind keyboard shortcuts, confirm important manual actions
  and queue a check, fold or call up to a configurable limit. An optional
  confirmation lets you cancel a queued action before it runs.

### Statistics and history

SQLite stores hands, board cards, actions and balances locally. Review games
and player statistics, mark games private or optionally synchronize finished
games directly with peers. You can exclude private games or games involving
specific players from sharing. Sharing and receiving are independent settings.

### Customization and mods

Install a MOD to replace supported card decks, sounds and cinematics.
Missing optional assets use the game's bundled versions.
[MOD structure and ZIP packaging](docs/MODS.md)

## Development

### Architecture

CoronaPoker uses Java 17, libGDX with LWJGL3, SQLite and Maven. The Maven
reactor separates the game core, GDX presentation, assets and architecture
tests. The architecture guide maps the modules, startup sequence and the
renderer-neutral command and event boundaries.
[Architecture and diagrams](docs/ARCHITECTURE.md) · [Module layout](modules/README.md)

### Build and certify from source

Use JDK 17 or newer for both building and running, and Apache Maven 3.x.
Run the product build from the repository root.

```bash
git clone https://github.com/tonikelope/coronapoker.git
cd coronapoker
mvn clean package
```

The build creates a runnable `target/CoronaPoker_<version>.jar` for the host
platform. `tools/build/build-platform-jars.ps1` generates the five release JARs in
`target/release-<version>/`. The Windows installer bundles the Windows x64 JAR
with a private Java 25 runtime.
[Windows installer build](packaging/windows/README.md)

### Testing and certification

Run these commands from the repository root on Windows.

| Goal | Command |
| --- | --- |
| Rebuild a test JAR | `.\qa.cmd build` |
| Run product and architecture tests | `.\qa.cmd test` |
| Add extended replayable QA | `.\qa.cmd extended` |
| Run the gameplay scenario catalogue once | `.\qa.cmd scenarios fast` |
| Run the standard release gate | `.\qa.cmd all balanced` |

The testing guide covers scenario depths, seed replay and how to read the
reports. New scenarios use the same executable catalogue.
[Testing guide](docs/TESTING.md) · [Adding scenarios](docs/ADDING_TEST_SCENARIOS.md)

## Contributors

CoronaPoker is developed by [@tonikelope](https://github.com/tonikelope).
Thanks to the testers and bug reporters who keep the project moving.
[Contributors](CONTRIBUTORS.md)
