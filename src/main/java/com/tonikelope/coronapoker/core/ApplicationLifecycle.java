package com.tonikelope.coronapoker.core;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Thread-safe, renderer-neutral lifecycle for one CoronaPoker process.
 */
public final class ApplicationLifecycle {

    public enum State {
        NEW,
        STARTING,
        MENU,
        SESSION,
        TABLE,
        STOPPING,
        TERMINATED,
        FAILED
    }

    private State state = State.NEW;
    private Throwable failure;

    public synchronized State state() {
        return state;
    }

    public synchronized Throwable failure() {
        return failure;
    }

    synchronized void beginStartup() {
        moveTo(State.STARTING, EnumSet.of(State.NEW));
    }

    synchronized void menuReady() {
        moveTo(State.MENU, EnumSet.of(State.STARTING, State.MENU, State.SESSION, State.TABLE));
    }

    synchronized void sessionOpened() {
        moveTo(State.SESSION, EnumSet.of(State.MENU, State.SESSION));
    }

    synchronized void tableEntered() {
        moveTo(State.TABLE, EnumSet.of(State.SESSION, State.TABLE));
    }

    synchronized void beginShutdown() {
        moveTo(State.STOPPING, EnumSet.of(
                State.NEW, State.STARTING, State.MENU, State.SESSION, State.TABLE, State.STOPPING));
    }

    synchronized void terminated() {
        moveTo(State.TERMINATED, EnumSet.of(State.STOPPING, State.TERMINATED));
    }

    synchronized void failed(Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        if (state == State.TERMINATED) {
            throw new IllegalStateException("Cannot fail a terminated application");
        }
        if (state != State.FAILED) {
            state = State.FAILED;
            failure = cause;
        }
    }

    private void moveTo(State target, EnumSet<State> allowed) {
        if (!allowed.contains(state)) {
            throw new IllegalStateException("Cannot move application lifecycle from " + state + " to " + target);
        }
        state = target;
    }
}
