package com.tonikelope.coronaupdater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Selects the normal CoronaPoker launch path after an update. */
final class CoronaPokerRelauncher {

    private static final String WINDOWS_LAUNCHER = "CoronaPoker.exe";

    private CoronaPokerRelauncher() {
    }

    static Process relaunch(String jarPath) throws IOException {
        LaunchPlan plan = launchPlan(
                System.getProperty("os.name", ""),
                Path.of(jarPath),
                Path.of(System.getProperty("java.home")));
        ProcessBuilder builder = new ProcessBuilder(plan.command());
        if (plan.workingDirectory() != null) {
            builder.directory(plan.workingDirectory().toFile());
        }
        return builder.start();
    }

    static LaunchPlan launchPlan(String operatingSystem, Path jar,
            Path javaHome) {
        Path normalizedJar = jar.toAbsolutePath().normalize();
        boolean windows = operatingSystem.toLowerCase(Locale.ROOT)
                .contains("win");

        if (windows) {
            Path appDirectory = normalizedJar.getParent();
            if (appDirectory != null && appDirectory.getFileName() != null
                    && "app".equalsIgnoreCase(
                            appDirectory.getFileName().toString())) {
                Path installationDirectory = appDirectory.getParent();
                if (installationDirectory != null) {
                    Path nativeLauncher = installationDirectory
                            .resolve(WINDOWS_LAUNCHER)
                            .toAbsolutePath().normalize();
                    if (Files.isRegularFile(nativeLauncher)) {
                        return new LaunchPlan(List.of(
                                nativeLauncher.toString()),
                                installationDirectory);
                    }
                }
            }
        }

        Path javaExecutable = javaHome.toAbsolutePath().normalize()
                .resolve("bin").resolve(windows ? "java.exe" : "java");
        return new LaunchPlan(List.of(javaExecutable.toString(), "-jar",
                normalizedJar.toString()), null);
    }

    record LaunchPlan(List<String> command, Path workingDirectory) {

        LaunchPlan {
            command = List.copyOf(command);
        }
    }
}
