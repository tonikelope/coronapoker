# Windows installer

CoronaPoker keeps the portable cross-platform JAR and additionally publishes a
native Windows installer. The installer is built with the JDK's native
`jpackage` launcher and contains a reduced private Java runtime made by
`jlink`; Inno Setup supplies the branded, bilingual installation wizard.
Users do not need to install or update Java globally.

The package deliberately installs per user. Its stable application JAR lives
at `app/CoronaPoker.jar`, beside the optional `app/mod` directory, and the
private runtime retains `runtime/bin/java.exe`. This is the layout expected by
`coronaupdater.jar`: self-update replaces the stable JAR and relaunches it with
the bundled runtime, without administrator elevation. Installing the game does
not redirect `user.home`; both the portable and installed editions continue to
use `%USERPROFILE%\.coronapoker` for preferences and user data.

Install Inno Setup once on the release machine:

```powershell
winget install --id JRSoftware.InnoSetup -e --scope user
```

Build the product JAR first, then use the release wrapper:

```powershell
.\qa.cmd build
.\tools\windows\build-release-installer.ps1
```

The result is `target/CoronaPoker_<version>_Windows_<arch>_Setup.exe`. The build
host needs Inno Setup 6.7 or newer. The wrapper downloads the current x64
Microsoft Build of OpenJDK 25 from Microsoft's stable URL, downloads and checks
its official SHA-256, builds the installer, and removes both the ZIP and
extracted JDK even if packaging fails. Java is therefore never a permanent
release-machine prerequisite or repository artifact.

`build-installer.ps1` remains the lower-level entry point for development. It
requires a JDK 25 distribution with `jdeps`, `jlink` and `jpackage`; the JDK
must either include `jmods` or preserve the packaged modules needed by the JDK
25 linkable-runtime feature. Use `-PackageType app-image` there to inspect or
test the native `CoronaPoker.exe` layout without creating an installer; Inno
Setup is not required for that mode.

The build regenerates the DPI-safe wizard artwork from the checked-in game
logo and table texture. The wizard follows the current Windows UI language:
Spanish systems get the Spanish installer and all other systems fall back to
English. The same stable application ID must remain unchanged so later Setup
versions upgrade the existing per-user installation instead of creating a
second copy.

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

1. Build the canonical `CoronaPoker_<version>.jar` and `chilean_mod.zip`.
2. Run `tools/windows/build-release-installer.ps1` to download and verify the
   current JDK 25 temporarily and build
   `CoronaPoker_<version>_Windows_x64_Setup.exe` from that exact JAR.
3. Verify the embedded JAR hash, Java 25 runtime, native icon and metadata,
   updater layout, language resources, installer hash and signing status.
4. Publish and remotely verify all three assets before deleting local build
   output.
5. Remove downloaded JDK archives, extracted toolchains, staging images,
   previews, logs and obsolete experimental installers during final cleanup.
