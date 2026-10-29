package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GdxAvatarCropModelTest {

    @Test
    void initialCropUsesTheLargestCenteredSquare() {
        GdxAvatarCropModel.Crop crop = GdxAvatarCropModel.centered(
                1600, 900);

        assertEquals(350f, crop.x(), 0.001f);
        assertEquals(0f, crop.y(), 0.001f);
        assertEquals(900f, crop.size(), 0.001f);
    }

    @Test
    void movingAndCenteredResizingRemainInsideTheSourceImage() {
        GdxAvatarCropModel.Crop initial = GdxAvatarCropModel.crop(
                1200, 800, 600f, 400f, 400f);
        GdxAvatarCropModel.Crop moved = GdxAvatarCropModel.move(initial,
                1200, 800, -10_000f, 10_000f);
        GdxAvatarCropModel.Crop resized = GdxAvatarCropModel.resizeCentered(
                moved, 1200, 800, 100f);

        assertTrue(moved.x() >= 0f && moved.y() >= 0f);
        assertTrue(moved.x() + moved.size() <= 1200.001f);
        assertTrue(moved.y() + moved.size() <= 800.001f);
        assertEquals(800f, resized.size(), 0.001f);
        assertTrue(resized.x() >= 0f && resized.y() >= 0f);
    }

    @Test
    void cornerResizeKeepsASquareLockedInsideTheImage() {
        GdxAvatarCropModel.Crop initial = GdxAvatarCropModel.crop(
                1200, 800, 600f, 400f, 400f);
        GdxAvatarCropModel.Crop resized = GdxAvatarCropModel.resizeFromCorner(
                initial, 1200, 800,
                GdxAvatarCropModel.Corner.TOP_LEFT, 50f, 25f);

        assertEquals(800f, resized.x() + resized.size(), 0.001f);
        assertEquals(600f, resized.y() + resized.size(), 0.001f);
        assertTrue(resized.x() >= 0f && resized.y() >= 0f);
        assertTrue(resized.x() + resized.size() <= 1200.001f);
        assertTrue(resized.y() + resized.size() <= 800.001f);
    }

    @Test
    void partialCenteringOnlyChangesTheRequestedAxis() {
        GdxAvatarCropModel.Crop initial = GdxAvatarCropModel.crop(
                1200, 800, 250f, 600f, 200f);
        GdxAvatarCropModel.Crop horizontal
                = GdxAvatarCropModel.centerHorizontally(initial, 1200, 800);
        GdxAvatarCropModel.Crop vertical
                = GdxAvatarCropModel.centerVertically(initial, 1200, 800);

        assertEquals(600f, horizontal.centerX(), 0.001f);
        assertEquals(initial.centerY(), horizontal.centerY(), 0.001f);
        assertEquals(initial.centerX(), vertical.centerX(), 0.001f);
        assertEquals(400f, vertical.centerY(), 0.001f);
        assertEquals(initial.size(), horizontal.size(), 0.001f);
        assertEquals(initial.size(), vertical.size(), 0.001f);
    }

    @Test
    void cornerHitZonesIncludeTheirVisibleExteriorArea() {
        float left = 100f;
        float bottom = 200f;
        float size = 400f;
        float radius = 30f;

        assertEquals(GdxAvatarCropModel.DragTarget.TOP_LEFT,
                GdxAvatarCropModel.dragTargetAt(left, bottom, size,
                        82f, 618f, radius));
        assertEquals(GdxAvatarCropModel.DragTarget.TOP_RIGHT,
                GdxAvatarCropModel.dragTargetAt(left, bottom, size,
                        518f, 618f, radius));
        assertEquals(GdxAvatarCropModel.DragTarget.BOTTOM_LEFT,
                GdxAvatarCropModel.dragTargetAt(left, bottom, size,
                        82f, 182f, radius));
        assertEquals(GdxAvatarCropModel.DragTarget.BOTTOM_RIGHT,
                GdxAvatarCropModel.dragTargetAt(left, bottom, size,
                        518f, 182f, radius));
        assertEquals(GdxAvatarCropModel.DragTarget.MOVE,
                GdxAvatarCropModel.dragTargetAt(left, bottom, size,
                        300f, 400f, radius));
        assertEquals(GdxAvatarCropModel.DragTarget.NONE,
                GdxAvatarCropModel.dragTargetAt(left, bottom, size,
                        50f, 650f, radius));
    }

    @Test
    void unavailableFirstFrameGeometryIsNotInteractiveAndNeverThrows() {
        assertEquals(GdxAvatarCropModel.DragTarget.NONE,
                GdxAvatarCropModel.dragTargetAt(0f, 0f, 0f,
                        0f, 0f, 18f));
        assertEquals(GdxAvatarCropModel.DragTarget.NONE,
                GdxAvatarCropModel.dragTargetAt(0f, 0f, Float.NaN,
                        0f, 0f, 18f));
        assertEquals(GdxAvatarCropModel.DragTarget.NONE,
                GdxAvatarCropModel.dragTargetAt(0f, 0f, 100f,
                        0f, 0f, -1f));
    }

    @Test
    void cornerResizeUsesDragDeltaWithoutJumpingFromHandleOffset() {
        GdxAvatarCropModel.Crop initial = GdxAvatarCropModel.crop(
                1200, 800, 600f, 400f, 400f);

        GdxAvatarCropModel.Crop unchanged
                = GdxAvatarCropModel.resizeFromCornerDelta(initial,
                        1200, 800,
                        GdxAvatarCropModel.Corner.BOTTOM_RIGHT, 0f, 0f);
        GdxAvatarCropModel.Crop horizontalOnly
                = GdxAvatarCropModel.resizeFromCornerDelta(initial,
                        1200, 800,
                        GdxAvatarCropModel.Corner.BOTTOM_RIGHT, -100f, 0f);

        assertEquals(initial, unchanged);
        assertEquals(300f, horizontalOnly.size(), 0.001f);
        assertEquals(initial.x(), horizontalOnly.x(), 0.001f);
        assertEquals(initial.y(), horizontalOnly.y(), 0.001f);
    }
}
