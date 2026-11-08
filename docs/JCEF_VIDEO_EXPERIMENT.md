# Poker videos with JCEF

The player is available on the main screen and in the waiting room. Open
**Appearance > Animations and effects > Poker videos** to configure it.
The player depends on the **Use animations and effects** switch.

## Window and controls

- The carousel starts with **Normal** videos by default. Choose **Shorts**
  in Settings or use the selector at the bottom of the player. Both controls
  save the same preference. Search prioritizes the game language, and the
  carousel shuffles the results at startup and after each full pass.
- The star adds or removes the current video from **Favorites**. The list
  keeps both formats in the order you saved them and persists after you quit.
  The arrows move through the list, and the counter shows the current
  position and total. The window adapts to each favorite's format. When you
  return to the carousel, the format selector reappears with the value saved
  in Settings.
- Drag the title bar to move the window, or drag an edge or corner to resize
  it. The game saves the position and size separately for each format. The
  window scales with the game area and leaves a margin at its edges.
- Closing the window minimizes the player to the icon next to the volume
  control. **Open video at startup** lets you start with the window open or
  with only the icon visible. **Animate player opening and closing**
  controls the transition between these states.
- Opening the window from the icon starts playback muted and resumes the
  video from its last known position in the session. YouTube's controls
  change the audio and captions. Modal dialogs pause playback. The game
  releases the browser when you enter a table and restores the video
  session when you leave if the player was open.

The window renders at the game's actual scale, including at 4K. YouTube
selects the video quality based on size, connection and device. The IFrame
API no longer supports setting the quality programmatically with
`setPlaybackQuality`. [Official IFrame API revision history](https://developers.google.com/youtube/iframe_api_revision_history)

## Platforms

Each JAR includes its own JCEF, libGDX and LWJGL binaries. Packages are
available for Windows x64, Linux x64 and ARM64, and macOS on Intel and Apple
Silicon. The game enables the player when it finds the native resource for
the operating system and architecture. On Linux, the bundled JCEF requires
access to X11 or XWayland through `DISPLAY`, even in a Wayland session.
If that access fails, the panel shows the reason.

Cross-platform packaging checks verify the contents of the JAR. Playback
and audio require testing on the target system. See
[Testing and certification](TESTING.md#poker-video-integration-checks) for
the automated and native checks.

## Loading and recovery

The game fetches a fresh catalog from YouTube at startup. It keeps the
results in memory when you minimize the player or enter a table. Search
uses the public results document and limits each download to 12 MiB and
15 seconds. The player skips removed, private or incompatible videos.
If the network or search format fails, the panel offers a retry while the
rest of the game remains available.

If the visible browser goes 30 seconds without a bridge response or its
first uploaded frame, the game recreates that browser once. If it still
fails, the window offers a manual retry. The YouTube API has a separate
30-second timeout. The readiness and recovery replies have 10 seconds,
playback has 20 seconds to start, and a subsequent buffering stall has
45 seconds. The player retries or skips videos within these limits and
offers a manual retry when it runs out of attempts. Pausing playback or
opening a modal does not count as a loading failure.

## Diagnosing a startup stall

The CoronaPoker debug console and the GDX debug file include native Chromium
warnings, browser console messages, browser and YouTube readiness, playback
progress, and counts of frames copied and uploaded. On Windows the debug
file is under `%USERPROFILE%\.coronapoker\Debug`. If the player stalls,
leave it visible until the recovery is logged so that the file records both
browser sessions.

To compare startup with hardware video decoding disabled, pass
`-Dcoronapoker.video.softwareDecode=true` before `-jar`. The setting
applies to Chromium video decoding.

## Integration

The local HTTP server listens only on loopback and uses sockets from
`java.base`. It handles two connections at a time with a bounded queue and
closes the connections when the session is released. The local page hosts
the YouTube iframe and blocks pop-ups and external top-level navigation.

JCEF renders to an off-screen BGRA buffer. The CEF callback copies pixels,
and the libGDX thread updates the texture. The native CEF instance stays
alive until CoronaPoker exits. The game releases each browser and its
texture when the player closes or you enter a table.

The document loads the iframe with autoplay disabled and mutes the player
before starting the video. The first `READY` in a fresh process warms up
the renderer and reloads the document once. When switching Shorts, the
bridge checks that the on-screen audio control matches the player's actual
mute state. The page preserves subsequent user choices.

For a silent native test, `-Dcoronapoker.video.silentOutput=true` suppresses
Chromium's audio output while preserving the audio control and API state.
