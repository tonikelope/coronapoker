package com.tonikelope.coronaupdater;

import java.util.Locale;

/** Selects the platform payload while preserving the pre-27.17 update contract. */
final class ReleaseArtifact {

    private ReleaseArtifact() {
    }

    static String jarName(String version, String osName, String osArch) {
        if (version == null || !version.matches("[0-9]+\\.[0-9]+")) {
            throw new IllegalArgumentException("Invalid release version");
        }
        String[] parts = version.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        String base = "CoronaPoker_" + version;
        if (major < 27 || major == 27 && minor < 17) return base + ".jar";

        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String arch = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT);
        String architecture = switch (arch) {
            case "amd64", "x86_64" -> "x64";
            case "aarch64", "arm64" -> "arm64";
            default -> throw new IllegalArgumentException("Unsupported architecture: " + arch);
        };
        if (os.contains("mac") || os.contains("darwin")) {
            return base + "_macOS_" + architecture + ".jar";
        }
        if (os.contains("win") && "x64".equals(architecture)) {
            return base + ".jar";
        }
        if (os.contains("linux")) {
            return base + "_Linux_" + architecture + ".jar";
        }
        throw new IllegalArgumentException("No release payload for " + os + " / " + arch);
    }
}
