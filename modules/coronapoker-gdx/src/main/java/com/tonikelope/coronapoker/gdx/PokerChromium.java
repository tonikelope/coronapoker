/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import org.cef.CefApp;
import org.cef.CefApp.CefAppState;
import org.cef.CefClient;
import org.cef.CefSettings;

/** Process-owned Chromium runtime. Each surface owns its own client and browser. */
final class PokerChromium {

    private static final Logger LOG = Logger.getLogger(PokerChromium.class.getName());
    private static final String VERSION = "152.0.6";
    private static final CopyOnWriteArrayList<Consumer<CefAppState>> LISTENERS =
            new CopyOnWriteArrayList<>();
    private static CefApp app;
    private static PokerVideoChromiumLog chromiumLog;
    private static boolean shuttingDown;

    private PokerChromium() { }

    static boolean available() {
        return PokerVideoPlatformSupport.isAvailable()
                && PokerVideoPlatformSupport.hasBrowserDisplay(
                        System.getProperty("os.name", ""), System.getenv("DISPLAY"));
    }

    static synchronized CefClient createClient(Consumer<CefAppState> listener)
            throws Exception {
        if (shuttingDown || !available()) {
            throw new IllegalStateException("Chromium is unavailable");
        }
        if (listener != null) LISTENERS.add(listener);
        try {
            if (app == null) initialize();
            return app.createClient();
        } catch (Exception | LinkageError failure) {
            if (listener != null) LISTENERS.remove(listener);
            throw failure;
        }
    }

    static void removeListener(Consumer<CefAppState> listener) {
        LISTENERS.remove(listener);
    }

    private static void initialize() throws Exception {
        String os = System.getProperty("os.name", "");
        Path directory = dataDirectory(os);
        Path cache = Files.createDirectories(directory.resolve("cache"));
        Path logs = Files.createDirectories(directory.resolve("logs"));
        Path nativeLog = logs.resolve("poker-video-cef.log").toAbsolutePath();

        CefAppBuilder builder = new CefAppBuilder();
        builder.setInstallDir(directory.resolve("jcef-" + VERSION).toFile());
        builder.addJcefArgs("--use-alloy-style", "--autoplay-policy=no-user-gesture-required");
        builder.getJcefArgs().addAll(PokerVideoPlatformSupport.browserArguments(os));
        boolean softwareDecode = Boolean.getBoolean("coronapoker.video.softwareDecode");
        if (softwareDecode) builder.addJcefArgs("--disable-accelerated-video-decode");
        if (Boolean.getBoolean("coronapoker.video.silentOutput")) builder.addJcefArgs("--mute-audio");
        CefSettings settings = builder.getCefSettings();
        settings.windowless_rendering_enabled = true;
        settings.root_cache_path = cache.toAbsolutePath().toString();
        settings.cache_path = "";
        settings.log_file = nativeLog.toString();
        settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING;
        settings.background_color = settings.new ColorType(255, 5, 8, 12);
        builder.setAppHandler(new MavenCefAppHandlerAdapter() {
            @Override public void stateHasChanged(CefAppState state) {
                for (Consumer<CefAppState> listener : LISTENERS) listener.accept(state);
            }
        });

        LOG.log(Level.INFO, "CoronaPoker Chromium start | {0} | OS {1} {2} | softwareDecode={3} | native log={4}",
                new Object[]{PokerVideoPlatformSupport.NATIVES_VERSION, os,
                    System.getProperty("os.version"), softwareDecode, nativeLog});
        PokerVideoChromiumLog log = new PokerVideoChromiumLog(nativeLog);
        try {
            app = builder.build();
            chromiumLog = log;
        } catch (Exception | LinkageError failure) {
            log.close();
            throw failure;
        }
    }

    static synchronized void shutdown() {
        shuttingDown = true;
        try {
            if (app != null) app.dispose();
        } finally {
            if (chromiumLog != null) chromiumLog.close();
            app = null;
            chromiumLog = null;
            LISTENERS.clear();
        }
    }

    private static Path dataDirectory(String osName) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "CoronaPoker", "jcef");
            }
        }
        if (os.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library",
                    "Caches", "CoronaPoker", "jcef");
        }
        String xdg = System.getenv("XDG_CACHE_HOME");
        return xdg == null || xdg.isBlank()
                ? Path.of(System.getProperty("user.home"), ".cache", "coronapoker", "jcef")
                : Path.of(xdg, "coronapoker", "jcef");
    }
}
