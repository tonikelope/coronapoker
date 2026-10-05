/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GdxEditMenuStyleTest {

    @Test
    void everySharedContextMenuSurfaceIsFullyOpaque() {
        assertEquals(1f, GdxEditMenuStyle.BORDER.a);
        assertEquals(1f, GdxEditMenuStyle.BACKGROUND.a);
        assertEquals(1f, GdxEditMenuStyle.ROW.a);
        assertEquals(1f, GdxEditMenuStyle.ROW_HOVER.a);
    }
}
