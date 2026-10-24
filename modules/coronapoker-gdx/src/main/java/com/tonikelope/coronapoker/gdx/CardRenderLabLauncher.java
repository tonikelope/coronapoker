/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.glutils.HdpiMode;

/** Process entry point for the standalone CoronaPoker card-rendering lab. */
public final class CardRenderLabLauncher {

    private CardRenderLabLauncher() { }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config
                = new Lwjgl3ApplicationConfiguration();
        config.setTitle("CoronaPoker // Card Render Lab");
        config.setWindowedMode(1400, 900);
        config.setWindowSizeLimits(1100, 700, -1, -1);
        config.setResizable(true);
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setIdleFPS(20);
        config.setHdpiMode(HdpiMode.Pixels);
        config.setBackBufferConfig(8, 8, 8, 8, 0, 0, 0);
        config.disableAudio(true);
        config.setWindowIcon("images/corona_poker_16.png");
        new Lwjgl3Application(new CardRenderLabApp(), config);
    }
}
