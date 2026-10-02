/*
 * Copyright (C) 2020-2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.ApplicationMetadata;

/**
 * Canonical bottom-left product mark shared by every GDX surface.
 *
 * <p>The frontend and table have independent renderers, so keeping the
 * geometry here prevents the product/mod version from silently changing size
 * between the menu, lobby, live table and final balance screen.</p>
 */
final class GdxProductVersionBrand {

    static final int FONT_SIZE = 15;
    static final float X = 16f;
    static final float BASELINE_Y = 20f;
    static final float MAX_WIDTH = 620f;
    static final float ALPHA = 0.75f;
    static final int RGB = 0xd5dfeb;
    static final float QUICK_ACCESS_GAP = 10f;

    private GdxProductVersionBrand() {
    }

    static String label(GdxGamePresentationSettings presentationSettings) {
        return presentationSettings == null
                ? "CoronaPoker " + ApplicationMetadata.VERSION
                : presentationSettings.productVersionLabel();
    }

    static float top() {
        return BASELINE_Y + 2f;
    }

    static float quickAccessY() {
        return top() + QUICK_ACCESS_GAP;
    }

    static boolean overlapsQuickAccess(float quickAccessY) {
        return quickAccessY < top() + QUICK_ACCESS_GAP;
    }
}
