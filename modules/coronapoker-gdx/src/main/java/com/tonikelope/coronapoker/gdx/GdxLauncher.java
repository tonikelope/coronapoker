package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Graphics.Monitor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.tonikelope.coronapoker.core.CoronaPokerApplication;
import com.tonikelope.coronapoker.core.CoronaPokerBootstrap;
import com.tonikelope.coronapoker.core.DatabaseService;
import com.tonikelope.coronapoker.core.NewGameSessionGateway;
import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.RecoverableGameRepository;
import com.tonikelope.coronapoker.core.network.NetworkLobbyGateway;
import com.tonikelope.coronapoker.CoreGameTableFactory;
import com.tonikelope.coronapoker.Crupier;
import com.tonikelope.coronapoker.DebugLog;
import com.tonikelope.coronapoker.core.game.ClasspathGameCinematicAssets;
import com.tonikelope.coronapoker.core.game.GameCinematicAssets;
import com.tonikelope.coronapoker.core.game.ModAwareGameCinematicAssets;
import com.tonikelope.coronapoker.core.media.ModMediaCatalog;
import java.util.Arrays;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Process entry point for the GDX frontend.
 *
 * It owns the shared process lifecycle and the single native GDX window. A
 * real table scene is mounted only after the shared core supplies its initial
 * authoritative snapshot.
 */
public final class GdxLauncher {

    private GdxLauncher() {
    }

    public static void main(String[] args) {
        Path debugFile = GdxDebugFile.install();
        DebugLog.install();
        Logger.getLogger(GdxLauncher.class.getName()).log(Level.INFO,
                "GDX debug log: {0}", debugFile);
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
        boolean silent = java.util.Arrays.stream(args)
                .anyMatch("--silent"::equalsIgnoreCase);
        if (silent) {
            System.setProperty("coronapoker.gdx.silent", "true");
        }
        PreferencesService preferences = application.service(PreferencesService.class);
        GdxWindowMode windowMode = GdxWindowMode.requested(args,
                preferences.properties());
        GdxDisplayModeController.rememberFullscreenMode(
                windowMode == GdxWindowMode.WINDOWED
                        ? GdxWindowMode.configured(preferences.properties())
                        : windowMode);
        ModMediaCatalog modMedia = GdxGamePresentationSettings.discoverInstalledMod();
        GdxGamePresentationSettings presentationSettings =
                new GdxGamePresentationSettings(preferences, modMedia);
        configureModSounds(modMedia, presentationSettings.language());
        Monitor requestedMonitor = requestedMonitor(args);
        DisplayMode display = requestedMonitor == null
                ? fastestDisplayMode()
                : Lwjgl3ApplicationConfiguration.getDisplayMode(requestedMonitor);
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("CoronaPoker // GDX");
        // --silent starts the shared master control muted, but keeps the audio
        // backend available so the in-game volume button can re-enable it.
        config.useVsync(true);
        // Exclusive fullscreen uses pure VSync, preserving the approved visual
        // reference's frame-pacing target without importing any table logic.
        // On mixed-refresh Windows desktops, windowed OpenGL may synchronize to
        // the primary output even when the window starts on another monitor;
        // use that selected monitor's own refresh as the non-fixed fallback.
        config.setForegroundFPS(windowMode == GdxWindowMode.EXCLUSIVE
                ? 0 : display.refreshRate);
        config.setIdleFPS(30);
        config.setBackBufferConfig(8, 8, 8, 8, 24, 8,
                presentationSettings.requestedMsaaSamples());
        config.setWindowIcon("images/corona_poker_16.png");
        if (windowMode == GdxWindowMode.WINDOWED) {
            config.setDecorated(true);
            config.setResizable(true);
            config.setWindowedMode(1600, 900);
            if (requestedMonitor != null) {
                int x = requestedMonitor.virtualX
                        + Math.max(0, (display.width - 1600) / 2);
                int y = requestedMonitor.virtualY
                        + Math.max(0, (display.height - 900) / 2);
                config.setWindowPosition(x, y);
            }
        } else if (windowMode == GdxWindowMode.BORDERLESS) {
            config.setDecorated(false);
            config.setResizable(false);
            config.setWindowedMode(display.width, display.height);
            Monitor target = requestedMonitor == null
                    ? Lwjgl3ApplicationConfiguration.getPrimaryMonitor()
                    : requestedMonitor;
            config.setWindowPosition(target.virtualX, target.virtualY);
        } else {
            config.setFullscreenMode(display);
        }
        System.setProperty("coronapoker.gdx.activeWindowMode",
                windowMode.persistedValue());

        GdxGameText gameText = new GdxGameText(preferences.properties()
                .getProperty("lenguaje", "es"));
        GdxGameLogSink gameLog = new GdxGameLogSink();
        GameCinematicAssets bundledCinematics =
                new ClasspathGameCinematicAssets("cinematics/allin");
        GameCinematicAssets cinematics = modMedia.installed()
                ? new ModAwareGameCinematicAssets(modMedia, bundledCinematics,
                        Arrays.stream(Crupier.ALLIN_CINEMATICS.getValue())
                                .map(entry -> (String) entry[0]).toList())
                : bundledCinematics;
        CoreGameTableFactory gameTables = new CoreGameTableFactory(
                application.service(DatabaseService.class), gameText, gameLog,
                new GdxGameDialogSink(), new GdxGameDecisionSink(gameText),
                presentationSettings, cinematics, modMedia.installed(),
                preferences.properties());
        RecoverableGameRepository recoverableGames
                = new RecoverableGameRepository(
                        application.service(DatabaseService.class));
        try (NetworkLobbyGateway lobbyGateway
                = NetworkLobbyGateway.forCurrentUser(gameTables,
                        recoverableGames)) {
            NewGameSessionGateway sessions = lobbyGateway;
            GdxApplicationShell shell = new GdxApplicationShell(
                    display.refreshRate, application, sessions, gameLog,
                    presentationSettings, gameText, language -> {
                        gameText.setLanguage(language);
                        configureModSounds(modMedia, language);
                    });
            config.setWindowListener(new Lwjgl3WindowAdapter() {
                @Override
                public boolean closeRequested() {
                    return shell.requestWindowClose();
                }
            });
            new Lwjgl3Application(shell, config);
        }
    }

    private static void configureModSounds(ModMediaCatalog mod, String language) {
        if (!mod.installed()) return;
        String selected = Crupier.ALLIN_SOUNDS.containsKey(language)
                ? language : "es";
        Crupier.ALLIN_SOUNDS_MOD = mod.soundCategory(selected, "allin",
                Crupier.ALLIN_SOUNDS.get(selected));
        Crupier.FOLD_SOUNDS_MOD = mod.soundCategory(selected, "fold",
                Crupier.FOLD_SOUNDS.get(selected));
        Crupier.SHOWDOWN_SOUNDS_MOD = mod.soundCategory(selected, "showdown",
                Crupier.SHOWDOWN_SOUNDS.get(selected));
        Crupier.LOSER_SOUNDS_MOD = mod.soundCategory(selected, "loser",
                Crupier.LOSER_SOUNDS.get(selected));
        Crupier.WINNER_SOUNDS_MOD = mod.soundCategory(selected, "winner",
                Crupier.WINNER_SOUNDS.get(selected));
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
