/*
 * Copyright (C) 2020 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

/**
 * Single-owner generation fence for a reconnectable heartbeat worker.
 *
 * <p>A socket replacement invalidates the worker that supervised the old
 * channel. Its delayed timeout/finally must not close, retire or otherwise
 * mutate the replacement worker.</p>
 */
final class HeartbeatGeneration {

    private long generation;
    private boolean alive;

    synchronized long start() {
        alive = true;
        return ++generation;
    }

    synchronized void invalidate() {
        generation++;
        alive = false;
    }

    synchronized boolean isCurrent(long candidate) {
        return alive && generation == candidate;
    }

    synchronized boolean retire(long candidate) {
        if (!isCurrent(candidate)) {
            return false;
        }
        alive = false;
        return true;
    }

    synchronized boolean isAlive() {
        return alive;
    }
}
