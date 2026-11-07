/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.sun.jna.platform.unix.X11;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Detects whether this build contains JCEF natives for the running platform. */
final class PokerVideoPlatformSupport {

    static final String NATIVES_VERSION =
            "jcef-17e805a+cef-152.0.6+g708dc14+chromium-152.0.7977.83";
    private static final boolean AVAILABLE = isAvailable(
            System.getProperty("os.name", ""),
            System.getProperty("os.arch", ""),
            PokerVideoPlatformSupport::hasResource);

    private PokerVideoPlatformSupport() {
    }

    static boolean isAvailable() {
        return AVAILABLE;
    }

    static boolean isAvailable(String osName, String osArch,
            Predicate<String> resourceExists) {
        String artifact = nativeArtifact(osName, osArch);
        return artifact != null && resourceExists.test(
                nativeResource(artifact));
    }

    static List<String> browserArguments(String osName) {
        // The embedded player renders its own controls. On Linux the native
        // GTK integration and Chromium's first-run UI can fail before OSR starts.
        return isLinux(osName)
                ? List.of("--no-first-run", "--ui-toolkit=fallback") : List.of();
    }

    static boolean hasBrowserDisplay(String osName, String displayName) {
        return hasBrowserDisplay(osName, displayName, name -> {
            var display = X11.INSTANCE.XOpenDisplay(name);
            if (display == null) return false;
            X11.INSTANCE.XCloseDisplay(display);
            return true;
        });
    }

    static boolean hasBrowserDisplay(String osName, String displayName,
            Predicate<String> canOpenDisplay) {
        // CEF 152 creates an X11 helper window even for windowless rendering
        // on the Wayland backend. A missing X display can abort the JVM.
        return !isLinux(osName) || displayName != null
                && !displayName.isBlank() && canOpenDisplay.test(displayName);
    }

    private static boolean isLinux(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("linux");
    }

    static String nativeArtifact(String osName, String osArch) {
        String os = osName == null ? ""
                : osName.toLowerCase(Locale.ROOT);
        String arch = osArch == null ? ""
                : osArch.toLowerCase(Locale.ROOT);
        String normalizedArch = switch (arch) {
            case "amd64", "x86_64" -> "amd64";
            case "aarch64", "arm64" -> "arm64";
            default -> null;
        };
        if (normalizedArch == null) return null;
        if (os.contains("mac") || os.contains("darwin")) {
            return "jcef-natives-macosx-" + normalizedArch;
        }
        if (os.contains("win")) {
            // JCEF Maven explicitly does not support OSR on Windows ARM64.
            return "amd64".equals(normalizedArch)
                    ? "jcef-natives-windows-amd64" : null;
        }
        if (os.contains("linux")) {
            return "jcef-natives-linux-" + normalizedArch;
        }
        return null;
    }

    static String nativeResource(String artifact) {
        return artifact + '-' + NATIVES_VERSION + ".tar.gz";
    }

    private static boolean hasResource(String name) {
        ClassLoader loader = PokerVideoPlatformSupport.class.getClassLoader();
        return loader == null
                ? ClassLoader.getSystemResource(name) != null
                : loader.getResource(name) != null;
    }
}
