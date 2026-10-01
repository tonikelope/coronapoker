/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Rectangle;

/** Geometry shared by table-dialog painting and pointer hit testing. */
final class GdxTableDialogLayout {

    private GdxTableDialogLayout() {
    }

    static AutoActionHud autoActionHud(float actionX, float actionY,
            float actionWidth, float actionHeight) {
        float progressHeight = 10f;
        float progressY = actionY + 5f;
        float contentY = actionY + 22f;
        float contentHeight = actionHeight - 22f;
        float cancelWidth = Math.min(190f, actionWidth * 0.25f);
        float gap = 10f;
        Rectangle bounds = new Rectangle(actionX, actionY,
                actionWidth, actionHeight);
        return new AutoActionHud(bounds,
                new Rectangle(actionX, contentY,
                        actionWidth - cancelWidth - gap, contentHeight),
                new Rectangle(actionX + actionWidth - cancelWidth,
                        contentY, cancelWidth, contentHeight),
                new Rectangle(actionX, progressY,
                        actionWidth, progressHeight));
    }

    static AutoCall autoCall(float panelX, float panelY, float panelWidth) {
        float rowX = panelX + 56f;
        float rowWidth = panelWidth - 112f;
        return new AutoCall(
                new Rectangle(rowX, panelY + 392f, rowWidth, 48f),
                new Rectangle(rowX, panelY + 318f, rowWidth, 64f),
                new Rectangle(rowX, panelY + 244f, rowWidth, 64f),
                GdxSettingsLayout.stepperRow(rowX, panelY + 155f,
                        rowWidth, GdxSettingsLayout.ROW_HEIGHT));
    }

    record AutoActionHud(Rectangle bounds, Rectangle message,
            Rectangle cancel, Rectangle progress) {
    }

    record AutoCall(Rectangle detail, Rectangle enabled, Rectangle noLimit,
            GdxSettingsLayout.StepperRow amount) {
    }
}
