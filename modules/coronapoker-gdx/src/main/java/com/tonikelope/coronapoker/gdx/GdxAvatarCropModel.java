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
        float cornerX = switch (corner) {
            case TOP_LEFT, BOTTOM_LEFT -> current.x();
            case TOP_RIGHT, BOTTOM_RIGHT -> current.x() + current.size();
        };
        float cornerY = switch (corner) {
            case TOP_LEFT, TOP_RIGHT -> current.y();
            case BOTTOM_LEFT, BOTTOM_RIGHT -> current.y() + current.size();
        };
        return resizeFromCornerDelta(current, imageWidth, imageHeight, corner,
                pointerX - cornerX, pointerY - cornerY);
    }

    static Crop resizeFromCornerDelta(Crop current, int imageWidth,
            int imageHeight, Corner corner, float sourceDeltaX,
            float sourceDeltaY) {
        validateDimensions(imageWidth, imageHeight);
        float oppositeX;
        float oppositeY;
        float horizontalGrowth;
        float verticalGrowth;
        switch (corner) {
            case TOP_LEFT -> {
                oppositeX = current.x() + current.size();
                oppositeY = current.y() + current.size();
                horizontalGrowth = -sourceDeltaX;
                verticalGrowth = -sourceDeltaY;
            }
            case TOP_RIGHT -> {
                oppositeX = current.x();
                oppositeY = current.y() + current.size();
                horizontalGrowth = sourceDeltaX;
                verticalGrowth = -sourceDeltaY;
            }
            case BOTTOM_LEFT -> {
                oppositeX = current.x() + current.size();
                oppositeY = current.y();
                horizontalGrowth = -sourceDeltaX;
                verticalGrowth = sourceDeltaY;
            }
            case BOTTOM_RIGHT -> {
                oppositeX = current.x();
                oppositeY = current.y();
                horizontalGrowth = sourceDeltaX;
                verticalGrowth = sourceDeltaY;
            }
            default -> throw new IllegalArgumentException(
                    "A corner is required for resizing");
        }
        float requested = current.size()
                + dominant(horizontalGrowth, verticalGrowth);
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

    private static float dominant(float first, float second) {
        return Math.abs(first) >= Math.abs(second) ? first : second;
    }

    static DragTarget dragTargetAt(float left, float bottom, float size,
            float pointerX, float pointerY, float handleRadius) {
        if (size <= 0f || handleRadius < 0f) {
            throw new IllegalArgumentException(
                    "Selection size must be positive and radius non-negative");
        }
        float right = left + size;
        float top = bottom + size;
        float squaredRadius = handleRadius * handleRadius;
        if (distanceSquared(pointerX, pointerY, left, top)
                <= squaredRadius) {
            return DragTarget.TOP_LEFT;
        }
        if (distanceSquared(pointerX, pointerY, right, top)
                <= squaredRadius) {
            return DragTarget.TOP_RIGHT;
        }
        if (distanceSquared(pointerX, pointerY, left, bottom)
                <= squaredRadius) {
            return DragTarget.BOTTOM_LEFT;
        }
        if (distanceSquared(pointerX, pointerY, right, bottom)
                <= squaredRadius) {
            return DragTarget.BOTTOM_RIGHT;
        }
        return pointerX >= left && pointerX <= right
                && pointerY >= bottom && pointerY <= top
                ? DragTarget.MOVE : DragTarget.NONE;
    }

    private static float distanceSquared(float x1, float y1,
            float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return dx * dx + dy * dy;
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

    enum DragTarget {
        NONE(null),
        MOVE(null),
        TOP_LEFT(Corner.TOP_LEFT),
        TOP_RIGHT(Corner.TOP_RIGHT),
        BOTTOM_LEFT(Corner.BOTTOM_LEFT),
        BOTTOM_RIGHT(Corner.BOTTOM_RIGHT);

        private final Corner corner;

        DragTarget(Corner corner) {
            this.corner = corner;
        }

        Corner corner() {
            if (corner == null) {
                throw new IllegalStateException(
                        "Only resize targets have a corner");
            }
            return corner;
        }
    }

    record Crop(float x, float y, float size, float centerX, float centerY) {
    }
}
