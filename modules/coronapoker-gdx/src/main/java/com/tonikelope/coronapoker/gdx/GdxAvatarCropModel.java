package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.MathUtils;

/** Pure 1:1 crop geometry, independent from input and OpenGL. */
final class GdxAvatarCropModel {

    static final float MIN_RELATIVE_SIZE = 1f / 8f;

    private GdxAvatarCropModel() {
    }

    static Crop centered(int imageWidth, int imageHeight) {
        return crop(imageWidth, imageHeight, imageWidth / 2f,
                imageHeight / 2f, Math.min(imageWidth, imageHeight));
    }

    static Crop crop(int imageWidth, int imageHeight, float centerX,
            float centerY, float size) {
        validateDimensions(imageWidth, imageHeight);
        float maximum = Math.min(imageWidth, imageHeight);
        float minimum = Math.max(1f, maximum * MIN_RELATIVE_SIZE);
        float safeSize = MathUtils.clamp(size, minimum, maximum);
        float half = safeSize / 2f;
        float clampedX = MathUtils.clamp(centerX, half,
                imageWidth - half);
        float clampedY = MathUtils.clamp(centerY, half,
                imageHeight - half);
        return new Crop(clampedX - half, clampedY - half, safeSize,
                clampedX, clampedY);
    }

    static Crop move(Crop current, int imageWidth, int imageHeight,
            float sourceDeltaX, float sourceDeltaY) {
        return crop(imageWidth, imageHeight,
                current.centerX() + sourceDeltaX,
                current.centerY() + sourceDeltaY, current.size());
    }

    static Crop resizeCentered(Crop current, int imageWidth, int imageHeight,
            float factor) {
        return crop(imageWidth, imageHeight, current.centerX(),
                current.centerY(), current.size() * factor);
    }

    static Crop centerHorizontally(Crop current, int imageWidth,
            int imageHeight) {
        return crop(imageWidth, imageHeight, imageWidth / 2f,
                current.centerY(), current.size());
    }

    static Crop centerVertically(Crop current, int imageWidth,
            int imageHeight) {
        return crop(imageWidth, imageHeight, current.centerX(),
                imageHeight / 2f, current.size());
    }

    static Crop resizeFromCorner(Crop current, int imageWidth,
            int imageHeight, Corner corner, float pointerX,
            float pointerY) {
        validateDimensions(imageWidth, imageHeight);
        float oppositeX;
        float oppositeY;
        float horizontal;
        float vertical;
        switch (corner) {
            case TOP_LEFT -> {
                oppositeX = current.x() + current.size();
                oppositeY = current.y() + current.size();
                horizontal = oppositeX - pointerX;
                vertical = oppositeY - pointerY;
            }
            case TOP_RIGHT -> {
                oppositeX = current.x();
                oppositeY = current.y() + current.size();
                horizontal = pointerX - oppositeX;
                vertical = oppositeY - pointerY;
            }
            case BOTTOM_LEFT -> {
                oppositeX = current.x() + current.size();
                oppositeY = current.y();
                horizontal = oppositeX - pointerX;
                vertical = pointerY - oppositeY;
            }
            case BOTTOM_RIGHT -> {
                oppositeX = current.x();
                oppositeY = current.y();
                horizontal = pointerX - oppositeX;
                vertical = pointerY - oppositeY;
            }
            default -> throw new IllegalArgumentException(
                    "A corner is required for resizing");
        }
        float requested = Math.max(horizontal, vertical);
        float maximum = switch (corner) {
            case TOP_LEFT -> Math.min(oppositeX, oppositeY);
            case TOP_RIGHT -> Math.min(imageWidth - oppositeX, oppositeY);
            case BOTTOM_LEFT -> Math.min(oppositeX,
                    imageHeight - oppositeY);
            case BOTTOM_RIGHT -> Math.min(imageWidth - oppositeX,
                    imageHeight - oppositeY);
        };
        float minimum = Math.max(1f, Math.min(imageWidth, imageHeight)
                * MIN_RELATIVE_SIZE);
        float size = MathUtils.clamp(requested, minimum, maximum);
        float x = switch (corner) {
            case TOP_LEFT, BOTTOM_LEFT -> oppositeX - size;
            case TOP_RIGHT, BOTTOM_RIGHT -> oppositeX;
        };
        float y = switch (corner) {
            case TOP_LEFT, TOP_RIGHT -> oppositeY - size;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> oppositeY;
        };
        return new Crop(x, y, size, x + size / 2f, y + size / 2f);
    }

    private static void validateDimensions(int imageWidth, int imageHeight) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException(
                    "Image dimensions must be positive");
        }
    }

    enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    record Crop(float x, float y, float size, float centerX, float centerY) {
    }
}
