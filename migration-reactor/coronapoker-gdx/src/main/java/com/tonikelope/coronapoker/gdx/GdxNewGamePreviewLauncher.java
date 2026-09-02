package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** Isolated visual preview; it is deliberately not the production launcher. */
public final class GdxNewGamePreviewLauncher {

    private GdxNewGamePreviewLauncher() {
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("CoronaPoker GDX // Preview Nueva timba");
        config.setWindowedMode(1600, 900);
        config.setResizable(true);
        config.useVsync(true);
        config.setForegroundFPS(0);
        config.setIdleFPS(30);
        config.setBackBufferConfig(8, 8, 8, 8, 24, 8, 4);
        config.setWindowIcon("images/corona_poker_16.png");
        new Lwjgl3Application(new NewGameScreenPreview(), config);
    }
}
