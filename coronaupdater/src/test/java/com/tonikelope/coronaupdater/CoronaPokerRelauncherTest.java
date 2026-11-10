package com.tonikelope.coronaupdater;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CoronaPokerRelauncherTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void installedWindowsBuildRestartsThroughNativeLauncher()
            throws Exception {
        Path installation = temporary.newFolder("CoronaPoker").toPath();
        Path app = Files.createDirectory(installation.resolve("app"));
        Path jar = Files.createFile(app.resolve("CoronaPoker.jar"));
        Path launcher = Files.createFile(
                installation.resolve("CoronaPoker.exe"));

        CoronaPokerRelauncher.LaunchPlan plan =
                CoronaPokerRelauncher.launchPlan("Windows 11", jar,
                        Path.of("C:/Java/jdk-25"));

        assertEquals(List.of(launcher.toAbsolutePath().normalize().toString()),
                plan.command());
        assertEquals(installation.toAbsolutePath().normalize(),
                plan.workingDirectory());
    }

    @Test
    public void missingNativeLauncherKeepsPortableJarFallback()
            throws Exception {
        Path installation = temporary.newFolder("Portable").toPath();
        Path app = Files.createDirectory(installation.resolve("app"));
        Path jar = Files.createFile(app.resolve("CoronaPoker.jar"));
        Path javaHome = Path.of("C:/Java/jdk-25");

        CoronaPokerRelauncher.LaunchPlan plan =
                CoronaPokerRelauncher.launchPlan("Windows 11", jar,
                        javaHome);

        assertEquals(List.of(javaHome.toAbsolutePath().normalize()
                        .resolve("bin/java.exe").toString(), "-jar",
                        jar.toAbsolutePath().normalize().toString()),
                plan.command());
        assertNull(plan.workingDirectory());
    }

    @Test
    public void nonWindowsBuildKeepsPortableJarFallback() throws Exception {
        File installation = temporary.newFolder("Linux-installation");
        Path app = Files.createDirectory(
                installation.toPath().resolve("app"));
        Path jar = Files.createFile(app.resolve("CoronaPoker.jar"));
        Files.createFile(installation.toPath().resolve("CoronaPoker.exe"));
        Path javaHome = Path.of("/opt/jdk");

        CoronaPokerRelauncher.LaunchPlan plan =
                CoronaPokerRelauncher.launchPlan("Linux", jar, javaHome);

        assertEquals(List.of(javaHome.toAbsolutePath().normalize()
                        .resolve("bin/java").toString(), "-jar",
                        jar.toAbsolutePath().normalize().toString()),
                plan.command());
        assertNull(plan.workingDirectory());
    }
}
