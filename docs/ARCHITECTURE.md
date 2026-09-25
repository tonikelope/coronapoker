# CoronaPoker architecture

CoronaPoker 24.11 is a libGDX desktop application. Poker rules, network
protocols, persistence and security live in a renderer-independent core. The
GDX module owns presentation and user input. Maven enforces this dependency
direction and packages the modules as one runnable application.

![CoronaPoker class architecture](diagrams/coronapoker-frontend-uml.png)

The editable source is
[`coronapoker-frontend-uml.drawio`](diagrams/coronapoker-frontend-uml.drawio).

## Process entry point

The executable entry class is
`com.tonikelope.coronapoker.gdx.GdxLauncher`.

The application startup path is:

```text
GdxLauncher.main
  -> CoronaPokerBootstrap.createApplication
  -> CoronaPokerApplication
  -> GdxApplicationShell
  -> GdxFrontendScreen
```

`CoronaPokerBootstrap` creates the process services and returns a
`CoronaPokerApplication`. `GdxApplicationShell` owns the libGDX window and
switches between the frontend screens and the poker table. `GdxFrontendScreen`
renders the menu, setup, waiting room, statistics and end-of-game views.

## Lobby and table creation

`NetworkLobbyGateway` owns the host or client lobby. It performs the network
handshake, maintains the participant roster and publishes a `TableSession` when
the peers agree that the game can start.

```text
GdxFrontendScreen
  -> NetworkLobbyGateway
  -> CoreGameTableFactory.create
  -> TableSession
  -> GdxApplicationShell.attachTable
  -> GdxTableRenderer
  -> CoronaPokerGdxTable
```

`CoreGameTableFactory` constructs the authoritative table graph. This includes
the dealer, player controllers, network peers, transport, database adapters and
the renderer-neutral table bridge. The GDX module contains no second poker
engine.

## Table boundary

The table boundary lives under
`modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/table`.
These types contain no libGDX classes.

### TableSession

`TableSession` transfers ownership of a prepared table from the lobby to the
application shell. It contains:

- the initial `TableSnapshot`;
- the `TableCommandSink` used by the frontend;
- the `TableEventBridge` used by the engine;
- the starter that begins the dealer after the table scene is ready;
- the resources closed when the table ends.

`TableSession.attach(renderer)` performs the following sequence:

1. Attach one renderer.
2. Open it with the initial snapshot.
3. Wait for the frontend to report that the scene is ready.
4. Start the engine.

The engine cannot publish the first hand event before the GDX table exists.

### Commands from GDX to the core

The table controls submit typed `TableCommand` values through
`TableCommandSink`.

```text
CoronaPokerGdxTable
  -> TableCommandSink.submit
  -> CoreGameTableFactory command adapter
  -> Crupier and player controllers
```

A command is a request. The core validates it and remains authoritative for
the game result.

### Events from the core to GDX

The engine publishes immutable `TableVisualEvent` values through
`TableEventBridge`.

```text
Crupier
  -> TableEventBridge.publish
  -> GdxTableRenderer.render
  -> CoronaPokerGdxTable.acceptEvent
```

`GdxTableRenderer` transfers each event to the libGDX render thread. It does
not decide poker rules. Events that return a `CompletionStage<Void>` define an
animation barrier at a specific point in hand progression, such as dealing or
payout. They do not transfer game authority to the renderer.

### Snapshot and event model

`TableSnapshot` contains the complete state required to open a table. Ordered
events carry later state changes. GDX renders and animates its local scene every
frame; it does not request or copy a full snapshot every frame.

The boundary therefore has a small runtime cost:

- one snapshot when the table opens;
- one typed command for each user action;
- one typed event for each relevant game state change;
- an animation completion only where the engine must wait for presentation.

## Maven modules

![CoronaPoker module map](diagrams/coronapoker-module-map.png)

The editable source is
[`coronapoker-module-map.drawio`](diagrams/coronapoker-module-map.drawio).

| Module | Responsibility |
| --- | --- |
| `modules/coronapoker-core` | Rules, dealer, bots, network, persistence, security and table contracts |
| `modules/coronapoker-assets` | Images, cards, sounds, translations and bundled media |
| `modules/coronapoker-gdx` | Launcher, screens, table renderer, input, audio and desktop integration |
| `modules/coronapoker-qa` | Dependency, source ownership and distribution checks |
| `tools/qa` | Extended protocol, recovery, security and application certification |

Each code module has its own source tree. The modules are internal build units,
not separate products. The distribution contains one executable:

```text
target/CoronaPoker-24.11.jar
```

## Dependency rules

The allowed product dependencies are:

```text
coronapoker-gdx -> coronapoker-core
coronapoker-gdx -> coronapoker-assets
coronapoker-core -> Java and third-party engine libraries
```

The core must not import libGDX. GDX may use core contracts and services but
must not duplicate game rules. Resources contain no Java source. Architecture
tests fail the build if these rules are broken.

The repository-root `src/main/resources` directory stores shared product data.
Product Java source belongs only to the module source trees. Generated files in
`target` directories are build output and never source ownership.

## Thread ownership

The core owns its dealer, network and persistence executors. libGDX owns the
render thread. `GdxTableRenderer` and the application shell transfer visual
work to the render thread. Render code must not block on network or database
operations. Core code must not read or mutate GDX actors.

## Historical implementation

Earlier implementations remain available in Git history for behavioral
comparison. They are not part of the active source tree, Maven reactor,
distribution or runtime. Behavior still being matched must be expressed as a
core or GDX scenario so it remains executable in the current product.
