package com.tonikelope.coronapoker.swing;

import com.tonikelope.coronapoker.Init;
import com.tonikelope.coronapoker.core.CoronaPokerBootstrap;

/** Process entry point for the classic Swing frontend. */
public final class SwingLauncher {

    private SwingLauncher() {
    }

    public static void main(String[] args) {
        Init.launch(args, CoronaPokerBootstrap.createApplication());
    }
}
