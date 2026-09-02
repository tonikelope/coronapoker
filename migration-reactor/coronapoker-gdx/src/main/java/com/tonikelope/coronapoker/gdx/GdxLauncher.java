package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.CoronaPokerApplication;
import com.tonikelope.coronapoker.core.CoronaPokerBootstrap;
import com.tonikelope.coronapoker.gdxdemo.CoronaPokerGdxLauncher;

/**
 * Process entry point for the GDX frontend.
 *
 * It owns only the shared process lifecycle. Rendering is delegated unchanged
 * to the approved demo launcher and renderer.
 */
public final class GdxLauncher {

    private GdxLauncher() {
    }

    public static void main(String[] args) {
        CoronaPokerApplication application = CoronaPokerBootstrap.createApplication();
        try {
            application.start();
            application.menuReady();
            CoronaPokerGdxLauncher.main(args);
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
}
