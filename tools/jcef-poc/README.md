<div align="justify">

# CoronaPoker JCEF / libGDX OSR PoC

This standalone proof of concept tests whether an official YouTube player can
run alongside CoronaPoker's renderer. It runs independently of the game
modules, screens, resources and preferences.

The [current player guide](../../docs/JCEF_VIDEO_EXPERIMENT.md) describes the
integration shipped with the game.

## What it demonstrates

- A 1280 × 720 libGDX/LWJGL3 window with a floating Scene2D panel.
- Windowless Chromium/JCEF rendering through Off-Screen Rendering (OSR).
- BGRA frame data from CEF copied to a persistent direct buffer, with dirty
  regions uploaded to an OpenGL texture on the libGDX render thread only.
- Playback through the official YouTube IFrame API with Chromium controls
  and audio.
- Mouse, wheel, keyboard, focus, 16:9 resizing, minimizing, closing and
  reopening, and pausing when hidden.
- FPS, frame time, Java heap, resolution, texture updates per second, upload
  cost, player state and JCEF, CEF and Chromium versions.
- F8 or the `CEF: ON/OFF` button stops or restarts the browser. The background
  keeps rendering so you can compare the cost.

## Pinned versions

- Java target: 17, the same as CoronaPoker.
- libGDX: 1.14.2.
- LWJGL: 3.4.1.
- jcefmaven: 152.0.6.
- JCEF: 17e805a.
- CEF: 152.0.6+g708dc14.
- Chromium: 152.0.7977.83.
- Gradle Wrapper: 9.8.2.

Maven Central hosts `me.friwi:jcefmaven:152.0.6` and its native packages. The
default Windows JAR bundles the Windows x64 native package. On first launch,
it extracts the bundled files to `%LOCALAPPDATA%\CoronaPoker\jcef-poc`.

## Build and run on Windows x64

```powershell
cd tools/jcef-poc
.\gradlew.bat clean check jar
java -jar .\build\libs\coronapoker-jcef-poc-0.1.0-windows-x86_64.jar
```

You can also use `scripts\run-windows.cmd`. The example video is `M7lc1UVf-VE`.

```powershell
java -jar .\build\libs\coronapoker-jcef-poc-0.1.0-windows-x86_64.jar `
  --video "https://www.youtube.com/watch?v=M7lc1UVf-VE"
```

The player accepts a video ID or a YouTube URL, including `youtube.com`,
`youtu.be`, `embed`, `shorts` and `live` URLs. It also reads
`jcef-poc.properties` from the working directory. See
`jcef-poc.properties.example`.

## Origin, Referer and error 153

In normal mode, a loopback HTTP server bound exclusively to `127.0.0.1` on a
random port serves `player.html`. This gives the main page a real HTTP origin.
The page uses `strict-origin-when-cross-origin` and passes `origin` and
`widget_referrer` to the IFrame API. JCEF adds the same `Referer` only when
a request to YouTube lacks it. The header identifies the local page.

To try hosting the page at a stable address, use `--page-url https://...`.
The hosted page must accept `?video=ID` and implement the IFrame API.
In this mode, the supplied HTTPS origin identifies the client.

The page displays and reports errors 2, 5, 100, 101, 150 and 153, as well
as autoplay blocks. The initial autoplay request is muted. Use the official
player control to turn on audio. YouTube serves the video to its own iframe.

## OSR bridge design

JCEF includes an OSR browser designed to display in a JOGL `GLCanvas`,
which would add a graphics component and an OpenGL context. This proof of
concept uses a minimal browser in the `org.cef.browser` package to reuse
JCEF's protected native calls while keeping rendering in the libGDX window.

1. CEF provides a temporary BGRA `ByteBuffer` with its origin at the top left.
2. The callback copies only changed rows to one persistent direct buffer. It
   reallocates the buffer only when the resolution changes.
3. The libGDX OpenGL thread uploads the accumulated dirty regions using
   `glTexSubImage2D` and `GL_BGRA`.
4. A short lock prevents CEF from overwriting the buffer during the upload.
5. Scene2D draws the texture and converts input coordinates to Chromium's
   top-left coordinate space.

The proof of concept depends on package-level JCEF members. A production
integration would need to pin and verify each JCEF update or maintain a small
patched adapter upstream.

Chromium keeps its default GPU configuration. JCEF 152's Java API exposes
the BGRA memory callback, so presenting Chromium output in libGDX requires
a CPU copy and a texture upload. The metrics quantify this cost.

## Other platforms

Each JAR contains the native binaries for one platform. Supported build
targets include:

```text
-PtargetPlatform=windows-x86_64
-PtargetPlatform=linux-x86_64
-PtargetPlatform=macos-x86_64
-PtargetPlatform=macos-aarch64
```

```bash
./gradlew clean check jar -PtargetPlatform=linux-x86_64
```

On macOS, use `scripts/run-macos.sh`. It adds `-XstartOnFirstThread` for GLFW
and opens the AWT modules JCEF requires. On Linux, use `scripts/run-linux.sh`.
Testing each variant requires running its graphical interface on that system.

## Interpreting the metrics

The overlay measures the Java/libGDX loop and the cost of OpenGL upload calls.
The heap figure covers the JVM. Chromium runs in separate native processes
that can use memory, CPU, video decoding and GPU resources beyond what the
overlay measures. Use Task Manager, Process Explorer, GPUView or equivalent
tools to measure those processes when evaluating a production integration.

## Primary sources

- [jcefmaven](https://github.com/jcefmaven/jcefmaven)
- [CEF Off-Screen Rendering](https://github.com/chromiumembedded/cef/blob/master/docs/general_usage.md#off-screen-rendering)
- [JCEF](https://github.com/chromiumembedded/java-cef)
- [YouTube IFrame Player API](https://developers.google.com/youtube/iframe_api_reference)
- [YouTube client identity and Referer](https://developers.google.com/youtube/terms/required-minimum-functionality#api-client-identity-and-credentials)

</div>
