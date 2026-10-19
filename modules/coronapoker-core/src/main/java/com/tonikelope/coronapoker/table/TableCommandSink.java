/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.table;

/** Receives renderer input; the existing game remains the authority. */
@FunctionalInterface
public interface TableCommandSink {

    void submit(TableCommand command);

    /**
     * Submits a command and reports whether the authoritative game accepted
     * it synchronously. Renderers use this acknowledgement to avoid latching
     * their controls after a stale or otherwise rejected local action.
     *
     * <p>Simple sinks remain source-compatible and treat a completed submit
     * call as accepted. The product game sink overrides this method for poker
     * decisions, whose controller already exposes an exact acceptance result.
     */
    default boolean trySubmit(TableCommand command) {
        submit(command);
        return true;
    }
}
