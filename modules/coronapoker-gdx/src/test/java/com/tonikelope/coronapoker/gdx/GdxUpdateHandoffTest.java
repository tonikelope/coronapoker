package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.UpdaterService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GdxUpdateHandoffTest {

    @Test
    void buildsTheWindowsUpdaterContractForTheReleaseJar() {
        Path current = Path.of("C:/CoronaPoker/CoronaPoker_24.10.jar");
        Path javaHome = Path.of("C:/Java/jdk-25");

        UpdaterService.Request request = GdxUpdateHandoff.request(
                "24.11", "es", current, javaHome, "Windows 11");

        assertEquals(current.toAbsolutePath().normalize(),
                request.currentJar());
        assertEquals(Path.of("C:/CoronaPoker/CoronaPoker-24.11.jar")
                .toAbsolutePath().normalize(), request.newJar());
        assertEquals(javaHome.resolve("bin/java.exe").toAbsolutePath()
                .normalize(), request.javaExecutable());
        assertTrue(request.spanish());
    }

    @Test
    void usesThePlatformJavaExecutableAndEnglishUpdaterOtherwise() {
        UpdaterService.Request request = GdxUpdateHandoff.request(
                "24.12", "en", Path.of("/opt/coronapoker/game.jar"),
                Path.of("/opt/jdk"), "Linux");

        assertEquals(Path.of("/opt/coronapoker/CoronaPoker-24.12.jar")
                .toAbsolutePath().normalize(), request.newJar());
        assertEquals(Path.of("/opt/jdk/bin/java").toAbsolutePath()
                .normalize(), request.javaExecutable());
        assertFalse(request.spanish());
    }
}
