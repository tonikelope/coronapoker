/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Rectangle;

/** Geometry shared by table-dialog painting and pointer hit testing. */
final class GdxTableDialogLayout {

    static final float AUTO_ACTION_HEIGHT = 250f;

    private GdxTableDialogLayout() {
    }

    static AutoAction autoAction(float panelX, float panelY, float panelWidth) {
        return new AutoAction(
                new Rectangle(panelX + 42f, panelY + 178f,
                        panelWidth - 84f, 46f),
                new Rectangle(panelX + 28f, panelY + 104f,
                        panelWidth - 56f, 60f),
                new Rectangle(panelX + 42f, panelY + 82f,
                        panelWidth - 84f, 14f),
                new Rectangle(panelX + 30f, panelY + 20f,
                        panelWidth - 60f, 52f));
    }

    static AutoCall autoCall(float panelX, float panelY, float panelWidth) {
        float rowX = panelX + 56f;
        float rowWidth = panelWidth - 112f;
        return new AutoCall(
                new Rectangle(rowX, panelY + 318f, rowWidth, 64f),
                new Rectangle(rowX, panelY + 244f, rowWidth, 64f),
                GdxSettingsLayout.stepperRow(rowX, panelY + 155f,
                        rowWidth, GdxSettingsLayout.ROW_HEIGHT));
    }

    record AutoAction(Rectangle title, Rectangle action,
            Rectangle progress, Rectangle cancel) {
    }

    record AutoCall(Rectangle enabled, Rectangle noLimit,
            GdxSettingsLayout.StepperRow amount) {
    }
}
