package com.tonikelope.coronapoker.core;

import java.net.URI;

/** Renderer-neutral application identity used by both distributions. */
public final class ApplicationMetadata {

    public static final String VERSION = "24.10";
    public static final URI LATEST_RELEASE_URI
            = URI.create("https://github.com/tonikelope/coronapoker/releases/latest");
    public static final URI UPDATER_URI
            = URI.create("https://github.com/tonikelope/coronapoker/raw/master/coronaupdater.jar");

    private ApplicationMetadata() {
    }
}
