package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Graphics.Monitor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.tonikelope.coronapoker.core.CoronaPokerApplication;
import com.tonikelope.coronapoker.core.CoronaPokerBootstrap;
import com.tonikelope.coronapoker.core.DatabaseService;
import com.tonikelope.coronapoker.core.NewGameSessionGateway;
import com.tonikelope.coronapoker.core.network.NetworkLobbyGateway;
import com.tonikelope.coronapoker.CoreGameTableFactory;

/**
 * Process entry point for the GDX frontend.
 *
 * It owns the shared process lifecycle and the single native GDX window. The
 * approved demo renderer remains unmodified and is mounted by that shell when
 * a real table session opens.
 */
public final class GdxLauncher {

    private GdxLauncher() {
    }

    public static void main(String[] args) {
        CoronaPokerApplication application = CoronaPokerBootstrap.createApplication();
        try {
            application.start();
            application.menuReady();
            launchWindow(args, application);
        } catch (Throwable failure) {
            application.fail(failure);
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("GDX frontend failed", failure);
        } finally {
            application.close();
        }
    }

    private static void launchWindow(String[] args,
            CoronaPokerApplication application) {
        boolean windowed = java.util.Arrays.stream(args)
                .anyMatch("--windowed"::equalsIgnoreCase);
        boolean silent = java.util.Arrays.stream(args)
                .anyMatch("--silent"::equalsIgnoreCase);
        if (silent) {
            System.setProperty("coronapoker.gdx.silent", "true");
        }
        Monitor requestedMonitor = requestedMonitor(args);
        DisplayMode display = requestedMonitor == null
                ? fastestDisplayMode()
                : Lwjgl3ApplicationConfiguration.getDisplayMode(requestedMonitor);
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("CoronaPoker // GDX");
        config.disableAudio(silent);
        config.useVsync(true);
        // Exclusive fullscreen uses pure VSync exactly like the canonical demo.
        // On mixed-refresh Windows desktops, windowed OpenGL may synchronize to
        // the primary output even when the window starts on another monitor;
        // use that selected monitor's own refresh as the non-fixed fallback.
        config.setForegroundFPS(windowed ? display.refreshRate : 0);
        config.setIdleFPS(30);
        config.setBackBufferConfig(8, 8, 8, 8, 24, 8, 4);
        config.setWindowIcon("images/corona_poker_16.png");
        if (windowed) {
            config.setWindowedMode(1600, 900);
            if (requestedMonitor != null) {
                int x = requestedMonitor.virtualX
                        + Math.max(0, (display.width - 1600) / 2);
                int y = requestedMonitor.virtualY
                        + Math.max(0, (display.height - 900) / 2);
                config.setWindowPosition(x, y);
            }
        } else {
            config.setFullscreenMode(display);
        }

        CoreGameTableFactory gameTables = new CoreGameTableFactory(
                application.service(DatabaseService.class));
        try (NetworkLobbyGateway lobbyGateway
                = NetworkLobbyGateway.forCurrentUser(gameTables)) {
            NewGameSessionGateway sessions = lobbyGateway;
            new Lwjgl3Application(new GdxApplicationShell(
                    display.refreshRate, application, sessions), config);
        }
    }

    private static DisplayMode fastestDisplayMode() {
        DisplayMode fastest = Lwjgl3ApplicationConfiguration.getDisplayMode();
        for (Monitor monitor : Lwjgl3ApplicationConfiguration.getMonitors()) {
            DisplayMode candidate = Lwjgl3ApplicationConfiguration.getDisplayMode(monitor);
            if (candidate.refreshRate > fastest.refreshRate
                    || (candidate.refreshRate == fastest.refreshRate
                    && candidate.width * candidate.height
                    > fastest.width * fastest.height)) {
                fastest = candidate;
            }
        }
        return fastest;
    }

    private static Monitor requestedMonitor(String[] args) {
        for (String argument : args) {
            if (!argument.regionMatches(true, 0, "--monitor=", 0, 10)) {
                continue;
            }
            int requested;
            try {
                requested = Integer.parseInt(argument.substring(10));
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException(
                        "El monitor debe indicarse como --monitor=1, --monitor=2, ...",
                        failure);
            }
            Monitor[] monitors = Lwjgl3ApplicationConfiguration.getMonitors();
            if (requested < 1 || requested > monitors.length) {
                throw new IllegalArgumentException("Monitor " + requested
                        + " no disponible; detectados: " + monitors.length);
            }
            return monitors[requested - 1];
        }
        return null;
    }
}
