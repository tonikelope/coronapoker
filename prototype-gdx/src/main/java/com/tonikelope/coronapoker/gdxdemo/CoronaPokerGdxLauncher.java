/*
 * Copyright (C) 2020-2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.gdxdemo;

import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Graphics.Monitor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** Desktop launcher for the isolated CoronaPoker GPU proof of concept. */
public final class CoronaPokerGdxLauncher {

    private CoronaPokerGdxLauncher() {
    }

    public static void main(String[] args) {
        boolean windowed = false;
        for (String arg : args) {
            if ("--windowed".equalsIgnoreCase(arg)) {
                windowed = true;
            }
        }

        DisplayMode display = fastestDisplayMode();
        System.out.printf("CoronaPoker GPU: %dx%d @ %d Hz%n",
                display.width, display.height, display.refreshRate);
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("CoronaPoker // libGDX GPU prototype");
        config.useVsync(true);
        // V-Sync follows the selected display mode. Avoid a second software
        // limiter so that 120/144/240 Hz monitors are not paced twice.
        config.setForegroundFPS(0);
        config.setIdleFPS(30);
        config.setBackBufferConfig(8, 8, 8, 8, 24, 8, 4);
        config.setWindowIcon("images/corona_poker_16.png");

        if (windowed) {
            config.setWindowedMode(1600, 900);
        } else {
            config.setFullscreenMode(display);
        }

        new Lwjgl3Application(new CoronaPokerGdxDemo(display.refreshRate), config);
    }

    private static DisplayMode fastestDisplayMode() {
        DisplayMode fastest = Lwjgl3ApplicationConfiguration.getDisplayMode();
        for (Monitor monitor : Lwjgl3ApplicationConfiguration.getMonitors()) {
            DisplayMode candidate = Lwjgl3ApplicationConfiguration.getDisplayMode(monitor);
            if (candidate.refreshRate > fastest.refreshRate
                    || (candidate.refreshRate == fastest.refreshRate
                    && candidate.width * candidate.height > fastest.width * fastest.height)) {
                fastest = candidate;
            }
        }
        return fastest;
    }
}
