# Windows installer

CoronaPoker publishes a portable JAR per supported platform and additionally a
native Windows installer. The installer is built with the JDK's native
`jpackage` launcher and bundles a private Java 25 runtime with all the
modules of the source JDK. Inno Setup supplies the branded, bilingual
installation wizard.
Users do not need to install or update Java globally.

The package deliberately installs per user. Its stable application JAR lives
at `app/CoronaPoker.jar`, beside the optional `app/mod` directory, and the
private runtime retains `runtime/bin/java.exe`. This is the layout expected by
`coronaupdater.jar`: self-update replaces the stable JAR and relaunches it with
the bundled runtime, without administrator elevation. Installing the game does
not redirect `user.home`. Both editions use
`%USERPROFILE%\.coronapoker` for preferences and user data.

Self-update replaces the application JAR. Run a current Setup to refresh
the bundled Java runtime or replace a reduced runtime from an earlier
installer. Since 27.18 the video loopback server uses `java.base` sockets
and works on pre-video installer runtimes that lack `jdk.httpserver`.

The task page offers **Allow CoronaPoker through Windows Firewall** (or its
Spanish equivalent) and selects it by default. With the task selected, the
Install button shows the system UAC shield when elevation is needed. Setup
asks for elevation once at the end and configures an inbound TCP rule for the
installed `CoronaPoker.exe` on private and public Windows network profiles.
Reinstalling reuses the rule for that executable. Uninstall removes matching
local rules and succeeds if they are already gone. Clearing the task keeps
the per-user installation unelevated. The task tooltip explains that the
rule allows hosting over the Internet and that Windows may ask for access
when you create a game if you skip it during Setup.

The **MODs** group is off by default. Setup can download Chilean MOD from
the matching GitHub release and verify its SHA-256, or install a personal ZIP.
Setup shows download and extraction progress, then includes the expanded MOD
size in the estimate on the confirmation and installation pages. If a MOD is
already installed, Setup saves it in a sibling `mod-backup-*` folder before
activating the new one. Uninstall leaves the active MOD and those backups
in place.

### Package a personal MOD

Use one of the [supported ZIP layouts](../../docs/MODS.md#package-a-personal-mod).
Select the ZIP from disk. Setup installs its contents in `app/mod` and asks
for a password if the ZIP has encrypted entries.

The native `.exe` intentionally starts without the legacy JVM splash screen.
`jpackage` invokes the main class directly instead of reading the JAR's
`SplashScreen-Image` manifest entry, which avoids adding a separate splash
resource and keeps the native startup path as direct as possible. The portable
JAR continues to show its existing splash when launched with `java -jar`.

Install Inno Setup once on the release machine:

```powershell
winget install --id JRSoftware.InnoSetup -e --scope user
```

Build the product JAR first, then use the release wrapper:

```powershell
.\qa.cmd build
.\tools\windows\build-release-installer.ps1 -ChileanModPath 'path\to\chilean_mod.zip'
```

The result is `target/CoronaPoker_<version>_Windows_<arch>_Setup.exe`. The build
host needs Inno Setup 6.7 or newer. The wrapper downloads the current x64
Microsoft Build of OpenJDK 25 from Microsoft's stable URL and verifies its
official SHA-256. It reuses the verified JDK in the Git-ignored `.toolchains`
cache and removes temporary installer staging even if packaging fails.

`build-installer.ps1` remains the lower-level entry point for development. It
requires a JDK 25 distribution with `jpackage`. The build supplies the JDK
as the runtime image and checks that the packaged module inventory matches
the source JDK. Use `-PackageType app-image` there to inspect or
test the native `CoronaPoker.exe` layout without creating an installer. Inno
Setup and the MOD archive are only required when building an installer.
By default the build reads `chilean_mod.zip` from the destination directory.
Use the exact file that will be published with the release, since the
installer checks its SHA-256 when a user chooses to download it.

The build regenerates the DPI-safe wizard artwork from the checked-in game
logo and table texture. The wizard follows the current Windows UI language:
Spanish systems get the Spanish installer and all other systems fall back to
English. The same stable application ID must remain unchanged so later Setup
versions upgrade the existing per-user installation instead of creating a
second copy.

The installer is a single executable that includes both the game and its
private Java runtime. On the first launch,
Windows or endpoint protection may scan the complete unsigned executable
before Inno Setup shows the license page. Later launches can reuse the
cached scan. The package uses solid compression and keeps the payload in
one executable.

For a signed public build, install the code-signing certificate in the Windows
certificate store and pass its thumbprint:

```powershell
.\tools\windows\build-installer.ps1 `
  -SigningCertificateThumbprint '<SHA1 thumbprint>'
```

The native launcher and final installer are both signed and timestamped.
Without a certificate the same package is produced unsigned and Windows may
show a SmartScreen warning. Keep the `AppId` in `CoronaPoker.iss` stable so
future releases upgrade the existing installation cleanly.

## Release integration

`polishfinish` treats the Windows installer as a required release artifact,
not as a replacement for the portable package. For every release:

1. Run `tools/build/build-platform-jars.ps1` to build and verify the five
   platform JARs in `target/release-<version>/`. The Windows x64 build also
   remains at `target/CoronaPoker_<version>.jar`. Prepare `chilean_mod.zip`
   (carry forward the verified official asset when the MOD is unchanged).
2. Run `tools/windows/build-release-installer.ps1` to download and verify the
   current JDK 25 when the verified local `.toolchains` cache is absent or
   stale, and build
   `CoronaPoker_<version>_Windows_x64_Setup.exe` from that exact JAR.
3. Verify the embedded JAR hash, Java 25 runtime, native icon and metadata,
   updater layout, language resources, installer hash and signing status.
4. Publish and remotely verify all platform JARs, the installer and the MOD
   before deleting local build output.
5. Archive useful audit evidence, then remove the entire root `target`,
   module build outputs, downloaded JDK archives, extracted toolchains,
   staging images and temporary previews. Final publication cleanup also
   removes the local JARs and installers. For intermediate tests, keep
   the current Windows JAR in `target` until final publication.
6. Keep only the newest public GitHub release and the historical 24.10
   release, after verifying the new release. Preserve historical Git tags.
