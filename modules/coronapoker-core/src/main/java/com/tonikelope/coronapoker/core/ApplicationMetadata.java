package com.tonikelope.coronapoker.core;

import java.net.URI;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Renderer-neutral application identity for the CoronaPoker product. */
public final class ApplicationMetadata {

    private static final String FALLBACK_VERSION = "25.41";
    private static final String VERSION_RESOURCE
            = "/META-INF/coronapoker-version.properties";

    /** Product builds supply their runtime identity as a classpath resource. */
    public static final String VERSION = loadVersion();
    public static final URI LATEST_RELEASE_URI
            = URI.create("https://github.com/tonikelope/coronapoker/releases/latest");
    public static final URI UPDATER_URI
            = URI.create("https://github.com/tonikelope/coronapoker/raw/master/coronaupdater.jar");

    private ApplicationMetadata() {
    }

    private static String loadVersion() {
        try (InputStream input = ApplicationMetadata.class.getResourceAsStream(
                VERSION_RESOURCE)) {
            if (input == null) return FALLBACK_VERSION;
            Properties values = new Properties();
            values.load(input);
            String version = values.getProperty("version", "").trim();
            if (!version.matches("[0-9]+\\.[0-9]+")) {
                throw new IllegalStateException(
                        "Invalid CoronaPoker product version: " + version);
            }
            return version;
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
