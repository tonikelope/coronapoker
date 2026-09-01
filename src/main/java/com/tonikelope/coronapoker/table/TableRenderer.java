/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.table;

import java.util.concurrent.CompletionStage;

/**
 * Presentation boundary shared by Swing and GDX.
 *
 * Completion of {@link #render(TableVisualEvent)} is the explicit animation
 * barrier. The dealer may wait for it only where the classic flow already has
 * a visual barrier; renderers never decide game progression.
 */
public interface TableRenderer extends AutoCloseable {

    CompletionStage<Void> open(TableSnapshot initialState);

    CompletionStage<Void> render(TableVisualEvent event);

    @Override
    void close();
}
