/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GdxMacFirstThreadTest {

    @Test
    void onlyMacWithoutTheFirstThreadFlagRequiresRelaunch() {
        assertTrue(GdxMacFirstThread.requiresRelaunch("Mac OS X",
                List.of("-Xmx1g"), false));
        assertTrue(GdxMacFirstThread.requiresRelaunch("Darwin",
                List.of(), false));
        assertFalse(GdxMacFirstThread.requiresRelaunch("Mac OS X",
                List.of("-XstartOnFirstThread"), false));
        assertFalse(GdxMacFirstThread.requiresRelaunch("Mac OS X",
                List.of(), true));
        assertFalse(GdxMacFirstThread.requiresRelaunch("Linux",
                List.of(), false));
        assertFalse(GdxMacFirstThread.requiresRelaunch("Windows 11",
                List.of(), false));
    }

    @Test
    void jarRelaunchPreservesSafeVmAndApplicationArguments(
            @TempDir Path temp) throws Exception {
        Path javaHome = Path.of("runtime");
        Path artifact = Files.createFile(temp.resolve("CoronaPoker.jar"));

        List<String> command = GdxMacFirstThread.command(javaHome, artifact,
                "ignored", List.of("-Xmx2g", "-agentlib:jdwp=port"),
                List.of("--silent"));

        assertEquals(List.of(javaHome.resolve("bin").resolve("java").toString(),
                "-XstartOnFirstThread", "-Xmx2g",
                "-Dcoronapoker.macos.firstThread=true", "-jar",
                artifact.toString(), "--silent"), command);
    }
}
