/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.UpdaterService;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/** Resolves the installed GDX JAR and the external updater handoff paths. */
final class GdxUpdateHandoff {

    private GdxUpdateHandoff() {
    }

    static Optional<UpdaterService.Request> runtimeRequest(String version,
            String language) {
        try {
            URI location = GdxLauncher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI();
            if (!"file".equalsIgnoreCase(location.getScheme())) {
                return Optional.empty();
            }
            Path currentJar = Path.of(location).toAbsolutePath().normalize();
            if (!Files.isRegularFile(currentJar)
                    || !currentJar.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return Optional.empty();
            }
            return Optional.of(request(version, language, currentJar,
                    Path.of(System.getProperty("java.home")),
                    System.getProperty("os.name", "")));
        } catch (Exception failure) {
            return Optional.empty();
        }
    }

    static UpdaterService.Request request(String version, String language,
            Path currentJar, Path javaHome, String operatingSystem) {
        Path installedJar = currentJar.toAbsolutePath().normalize();
        Path destination = installedJar.resolveSibling(
                "CoronaPoker-" + version + ".jar");
        boolean windows = operatingSystem.toLowerCase(Locale.ROOT)
                .contains("win");
        Path javaExecutable = javaHome.toAbsolutePath().normalize()
                .resolve("bin").resolve(windows ? "java.exe" : "java");
        return new UpdaterService.Request(version, installedJar, destination,
                javaExecutable, "es".equalsIgnoreCase(language));
    }
}
