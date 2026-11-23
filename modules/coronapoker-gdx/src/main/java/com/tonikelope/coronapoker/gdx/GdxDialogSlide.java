package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Moves the foreground while its backdrop and input ownership stay in place. */
final class GdxDialogSlide {

    private static final float ENTER_SECONDS = 0.28f;
    private static final float EXIT_SECONDS = 0.20f;

    private Object dialog;
    private float openness;
    private Runnable onClosed;
    private float drawingOffset;
    private final Matrix4 projection = new Matrix4();

    void update(Object visibleDialog, float delta, boolean animationsEnabled) {
        Object next = animationsEnabled ? visibleDialog : null;
        if (next != dialog) {
            Runnable finish = visibleDialog == dialog ? onClosed : null;
            onClosed = null;
            dialog = next;
            openness = 0f;
            if (finish != null) finish.run();
        }
        advance(delta);
    }

    void advance(float delta) {
        if (dialog == null) return;
        if (onClosed == null) {
            openness = Math.min(1f, openness + delta / ENTER_SECONDS);
        } else {
            openness = Math.max(0f, openness - delta / EXIT_SECONDS);
            if (openness == 0f) {
                Runnable finish = onClosed;
                onClosed = null;
                dialog = null;
                finish.run();
            }
        }
    }

    boolean deferClose(Object visibleDialog, Runnable finish) {
        if (visibleDialog == null || dialog != visibleDialog || openness == 0f) {
            return false;
        }
        if (onClosed == null) onClosed = finish;
        return true;
    }

    boolean closing() {
        return onClosed != null;
    }

    boolean moving() {
        return dialog != null && (openness < 1f || closing());
    }

    float offset(float worldHeight) {
        return moving() ? worldHeight * (1f - Interpolation.pow3Out.apply(openness)) : 0f;
    }

    float drawingOffset() {
        return drawingOffset;
    }

    boolean begin(Viewport viewport, ShapeRenderer shapes, SpriteBatch batch) {
        drawingOffset = offset(viewport.getWorldHeight());
        if (drawingOffset == 0f) return false;
        projection.set(viewport.getCamera().combined).translate(0f, drawingOffset, 0f);
        shapes.setProjectionMatrix(projection);
        batch.setProjectionMatrix(projection);
        return true;
    }

    void end(Viewport viewport, ShapeRenderer shapes, SpriteBatch batch) {
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        drawingOffset = 0f;
    }
}
