package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.UpdaterService;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GdxUpdateHandoffTest {

    @Test
    void keepsTheStableWindowsInstallationNameAcrossUpdates() {
        Path current = Path.of("C:/CoronaPoker/CoronaPoker.jar");
        Path javaHome = Path.of("C:/Java/jdk-25");

        UpdaterService.Request request = GdxUpdateHandoff.request(
                "25.1", "es", current, javaHome, "Windows 11");

        assertEquals(current.toAbsolutePath().normalize(),
                request.currentJar());
        assertEquals(current.toAbsolutePath().normalize(), request.newJar());
        assertEquals(javaHome.resolve("bin/java.exe").toAbsolutePath()
                .normalize(), request.javaExecutable());
        assertTrue(request.spanish());
    }

    @Test
    void migratesVersionedWindowsInstallationToTheStableName() {
        Path current = Path.of("C:/CoronaPoker/CoronaPoker_25.2.jar");
        UpdaterService.Request request = GdxUpdateHandoff.request(
                "25.3", "es", current,
                Path.of("C:/Java/jdk-25"), "Windows 11");

        assertEquals(current.toAbsolutePath().normalize(), request.currentJar());
        assertEquals(current.resolveSibling("CoronaPoker.jar").toAbsolutePath()
                .normalize(), request.newJar());
    }

    @Test
    void usesTheStableInstallationNameOnOtherPlatformsToo() {
        Path current = Path.of("/opt/coronapoker/game.jar");
        UpdaterService.Request request = GdxUpdateHandoff.request(
                "24.12", "en", current,
                Path.of("/opt/jdk"), "Linux");

        assertEquals(current.resolveSibling("CoronaPoker.jar").toAbsolutePath()
                .normalize(), request.newJar());
        assertEquals(Path.of("/opt/jdk/bin/java").toAbsolutePath()
                .normalize(), request.javaExecutable());
        assertFalse(request.spanish());
    }

    @Test
    void versionedInstallationHandsTheUpdaterTheStableDestination() throws Exception {
        Path updaterJar = Path.of("C:/Temp/coronaupdater.jar");
        Path current = Path.of("C:/CoronaPoker/CoronaPoker_25.2.jar");
        Path javaHome = Path.of("C:/Java/jdk-25");
        List<List<String>> commands = new ArrayList<>();
        UpdaterService updater = new UpdaterService(() -> updaterJar,
                commands::add);
        updater.start();

        updater.handoff(GdxUpdateHandoff.request("25.3", "es", current,
                javaHome, "Windows 11"));

        assertEquals(List.of(List.of(
                javaHome.resolve("bin/java.exe").toAbsolutePath().normalize().toString(),
                "-jar", updaterJar.toString(), "25.3",
                current.toAbsolutePath().normalize().toString(),
                current.resolveSibling("CoronaPoker.jar").toAbsolutePath()
                        .normalize().toString(),
                "¡Santiago y cierra, España!")), commands);
    }

    @Test
    void modUpdateUsesTheExternalUpdatersHistoricalArgumentContract()
            throws Exception {
        Path updaterJar = Path.of("C:/Temp/coronaupdater.jar");
        Path current = Path.of("C:/CoronaPoker/CoronaPoker.jar");
        Path javaHome = Path.of("C:/Java/jdk-25");
        URI download = URI.create("https://example.test/chilean-mod.zip");
        List<List<String>> commands = new ArrayList<>();
        UpdaterService updater = new UpdaterService(() -> updaterJar,
                commands::add);
        updater.start();

        UpdaterService.ModRequest request = GdxUpdateHandoff.modRequest(
                "0.58", download, "secret", "es", current, javaHome,
                "Windows 11");
        updater.handoff(request);

        assertEquals(List.of(List.of(
                javaHome.resolve("bin/java.exe").toAbsolutePath().normalize()
                        .toString(),
                "-jar", updaterJar.toString(),
                current.resolveSibling("mod").toAbsolutePath().normalize()
                        .toString().replace('\\', '/'),
                "0.58", current.toAbsolutePath().normalize().toString(),
                download.toString(), "secret",
                "¡Santiago y cierra, España!")), commands);
    }
}
