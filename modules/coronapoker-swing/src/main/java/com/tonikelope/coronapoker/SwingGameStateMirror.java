/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.GameStateMirror;

/** Keeps classic static fields coherent while Swing is the active frontend. */
final class SwingGameStateMirror implements GameStateMirror {
    @Override public void recoveredBuyin(int buyin, boolean rebuy) {
        GameFrame.BUYIN = buyin;
        GameFrame.REBUY = rebuy;
    }
    @Override public void blindSchedule(int value, int type) {
        GameFrame.CIEGAS_DOUBLE = value;
        GameFrame.CIEGAS_DOUBLE_TYPE = type;
    }
    @Override public void recovering(boolean value) { GameFrame.setRECOVER(value); }
}
