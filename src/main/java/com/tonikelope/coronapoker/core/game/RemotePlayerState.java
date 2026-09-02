/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Remote-only state kept outside its Swing/GDX presentation. */
public final class RemotePlayerState extends PlayerState {
    private volatile boolean bot;
    private volatile boolean turn;
    private volatile int responseTime;

    public RemotePlayerState() { super(); }
    public RemotePlayerState(String nickname) { super(nickname); }
    public boolean bot() { return bot; }
    public boolean turn() { return turn; }
    public int responseTime() { return responseTime; }
    public void setBot(boolean value) { bot = value; }
    public void setTurn(boolean value) { turn = value; }
    public void setResponseTime(int value) { responseTime = value; }
}
