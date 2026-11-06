package com.tonikelope.coronaupdater;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import org.junit.Test;

public class ReleaseArtifactTest {
    @Test public void oldReleasesKeepTheirPortableJarOnEveryPlatform() {
        assertEquals("CoronaPoker_27.16.jar", ReleaseArtifact.jarName("27.16", "Linux", "aarch64"));
        assertEquals("CoronaPoker_26.99.jar", ReleaseArtifact.jarName("26.99", "Mac OS X", "aarch64"));
    }

    @Test public void windowsX64KeepsTheCanonicalJarForTheInstaller() {
        assertEquals("CoronaPoker_27.17.jar", ReleaseArtifact.jarName("27.17", "Windows 11", "amd64"));
    }

    @Test public void linuxSelectsItsOwnArchitecture() {
        assertEquals("CoronaPoker_27.17_Linux_x64.jar", ReleaseArtifact.jarName("27.17", "Linux", "x86_64"));
        assertEquals("CoronaPoker_27.17_Linux_arm64.jar", ReleaseArtifact.jarName("27.17", "Linux", "aarch64"));
    }

    @Test public void macAndDarwinAreNeverMistakenForWindows() {
        assertEquals("CoronaPoker_27.17_macOS_x64.jar", ReleaseArtifact.jarName("27.17", "Mac OS X", "amd64"));
        assertEquals("CoronaPoker_27.17_macOS_arm64.jar", ReleaseArtifact.jarName("27.17", "Darwin", "arm64"));
    }

    @Test public void missingOrUnsupportedPayloadFailsInsteadOfInstallingWindowsOnAnotherOs() {
        assertThrows(IllegalArgumentException.class, () -> ReleaseArtifact.jarName("27.17", "Linux", "riscv64"));
        assertThrows(IllegalArgumentException.class, () -> ReleaseArtifact.jarName("27.17", "Windows 11", "aarch64"));
        assertThrows(IllegalArgumentException.class, () -> ReleaseArtifact.jarName("27.17", "FreeBSD", "amd64"));
    }

    @Test public void versionsAreValidatedAndFutureReleasesStayPlatformSpecific() {
        assertThrows(IllegalArgumentException.class, () -> ReleaseArtifact.jarName("../27.17", "Linux", "amd64"));
        assertEquals("CoronaPoker_28.0_Linux_x64.jar", ReleaseArtifact.jarName("28.0", "Linux", "amd64"));
    }
}
