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
}
