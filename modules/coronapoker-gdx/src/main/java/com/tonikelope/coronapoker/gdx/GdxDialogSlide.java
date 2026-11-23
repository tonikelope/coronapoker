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

    enum Direction {
        UP(0, 1), RIGHT(1, 0), LEFT(-1, 0), DOWN(0, -1), OFF(0, 0);

        final int x;
        final int y;

        Direction(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private Object dialog;
    private Direction direction = Direction.OFF;
    private float openness;
    private Runnable onClosed;
    private float drawingOffsetX;
    private float drawingOffsetY;
    private final Matrix4 projection = new Matrix4();

    void update(Object visibleDialog, float delta, Direction selectedDirection) {
        Object next = selectedDirection == Direction.OFF ? null : visibleDialog;
        if (next != dialog) {
            Runnable finish = visibleDialog == dialog ? onClosed : null;
            onClosed = null;
            dialog = next;
            direction = selectedDirection;
            openness = 0f;
            if (finish != null) finish.run();
        } else if (openness == 1f) {
            direction = selectedDirection;
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

    private float remainingDistance() {
        return moving() ? 1f - Interpolation.pow3Out.apply(openness) : 0f;
    }

    float offsetX(float worldWidth) {
        return worldWidth * remainingDistance() * direction.x;
    }

    float offsetY(float worldHeight) {
        return worldHeight * remainingDistance() * direction.y;
    }

    float drawingOffsetX() {
        return drawingOffsetX;
    }

    float drawingOffsetY() {
        return drawingOffsetY;
    }

    boolean begin(Viewport viewport, ShapeRenderer shapes, SpriteBatch batch) {
        drawingOffsetX = offsetX(viewport.getWorldWidth());
        drawingOffsetY = offsetY(viewport.getWorldHeight());
        if (drawingOffsetX == 0f && drawingOffsetY == 0f) return false;
        projection.set(viewport.getCamera().combined)
                .translate(drawingOffsetX, drawingOffsetY, 0f);
        shapes.setProjectionMatrix(projection);
        batch.setProjectionMatrix(projection);
        return true;
    }

    void end(Viewport viewport, ShapeRenderer shapes, SpriteBatch batch) {
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        drawingOffsetX = 0f;
        drawingOffsetY = 0f;
    }
}
