/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class GdxAvatarCropInteractionContractTest {

    @Test
    void exteriorCornerHitUsesTheSameTargetForCursorAndPointerCapture()
            throws Exception {
        String source = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "GdxFrontendScreen.java"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");

        assertTrue(source.contains(
                "GdxAvatarCropModel.DragTarget target = avatarCropDragAt(\n"
                + "                    pointer.x, pointer.y);\n"
                + "            boolean insideImage"
                + " = avatarCropImageBounds.contains(pointer);\n"
                + "            if (target"
                + " != GdxAvatarCropModel.DragTarget.NONE || insideImage)"));
        assertTrue(source.contains(
                "Vector2 source = avatarCropUnclampedSourceAt(session,"));
        assertTrue(source.contains(
                "GdxAvatarCropModel.resizeFromCornerDelta("));
    }
}
