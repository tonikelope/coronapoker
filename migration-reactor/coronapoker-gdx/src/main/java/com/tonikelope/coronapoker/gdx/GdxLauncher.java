package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Graphics.Monitor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.tonikelope.coronapoker.core.CoronaPokerApplication;
import com.tonikelope.coronapoker.core.CoronaPokerBootstrap;
import com.tonikelope.coronapoker.core.NewGameSessionGateway;
import com.tonikelope.coronapoker.core.network.NetworkLobbyGateway;

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
        DisplayMode display = fastestDisplayMode();
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("CoronaPoker // GDX");
        config.useVsync(true);
        config.setForegroundFPS(0);
        config.setIdleFPS(30);
        config.setBackBufferConfig(8, 8, 8, 8, 24, 8, 4);
        config.setWindowIcon("images/corona_poker_16.png");
        if (windowed) {
            config.setWindowedMode(1600, 900);
        } else {
            config.setFullscreenMode(display);
        }

        try (NetworkLobbyGateway lobbyGateway = NetworkLobbyGateway.forCurrentUser()) {
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
}
