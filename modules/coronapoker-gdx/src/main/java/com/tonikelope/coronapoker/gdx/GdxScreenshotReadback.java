/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.utils.Disposable;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;

/** One reusable pixel-pack buffer, owned by the table's render thread. */
final class GdxScreenshotReadback implements Disposable {
    private static final int PACK_BUFFER = GL21C.GL_PIXEL_PACK_BUFFER;

    record Frame(Path file, int width, int height, byte[] rgba) {
    }

    private record Pending(Path file, int width, int height, long fence) {
    }

    private int buffer;
    private int byteCount;
    private Pending pending;
    private boolean disabled;

    boolean hasPending() {
        return pending != null;
    }

    boolean submit(Path file, int width, int height) {
        if (disabled || !GL.getCapabilities().OpenGL32) return false;
        if (hasPending()) throw new IllegalStateException("Screenshot readback still pending");

        int size = Math.multiplyExact(Math.multiplyExact(width, height), 4);
        int previousBuffer = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        int previousAlignment = GL11C.glGetInteger(GL11C.GL_PACK_ALIGNMENT);
        if (buffer == 0) {
            buffer = GL15C.glGenBuffers();
            if (buffer == 0) throw new IllegalStateException("Could not create screenshot buffer");
        }

        GL15C.glBindBuffer(PACK_BUFFER, buffer);
        try {
            if (byteCount != size) {
                GL15C.glBufferData(PACK_BUFFER, size, GL15C.GL_STREAM_READ);
                if (GL15C.glGetBufferParameteri(PACK_BUFFER, GL15C.GL_BUFFER_SIZE) != size) {
                    throw new IllegalStateException("Could not allocate screenshot buffer");
                }
                byteCount = size;
            }
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, 1);
            GL11C.glReadPixels(0, 0, width, height, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, 0L);
            long fence = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            if (fence == 0L) throw new IllegalStateException("Could not fence screenshot readback");
            pending = new Pending(file, width, height, fence);
        } finally {
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, previousAlignment);
            GL15C.glBindBuffer(PACK_BUFFER, previousBuffer);
        }
        GL11C.glFlush();
        return true;
    }

    Frame poll(boolean closing) {
        if (!hasPending()) return null;
        Pending shot = pending;
        if (!closing) {
            int status = GL32C.glClientWaitSync(shot.fence(), 0, 0L);
            if (status == GL32C.GL_TIMEOUT_EXPIRED) return null;
            if (status == GL32C.GL_WAIT_FAILED) {
                throw new IllegalStateException("Screenshot readback fence failed");
            }
        }

        // At shutdown mapping can wait so the last captured frame can still be saved.
        byte[] rgba = new byte[byteCount];
        int previousBuffer = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        GL15C.glBindBuffer(PACK_BUFFER, buffer);
        try {
            ByteBuffer mapped = GL30C.glMapBufferRange(PACK_BUFFER, 0, byteCount, GL30C.GL_MAP_READ_BIT);
            if (mapped == null) throw new IllegalStateException("Could not map screenshot buffer");
            try {
                mapped.get(rgba);
            } finally {
                if (!GL15C.glUnmapBuffer(PACK_BUFFER)) {
                    throw new IllegalStateException("Screenshot buffer contents became invalid");
                }
            }
            return new Frame(shot.file(), shot.width(), shot.height(), rgba);
        } finally {
            GL15C.glBindBuffer(PACK_BUFFER, previousBuffer);
            GL32C.glDeleteSync(shot.fence());
            pending = null;
        }
    }

    void disable() {
        disabled = true;
        dispose();
    }

    @Override
    public void dispose() {
        if (hasPending()) {
            GL32C.glDeleteSync(pending.fence());
            pending = null;
        }
        if (buffer != 0) {
            GL15C.glDeleteBuffers(buffer);
            buffer = 0;
            byteCount = 0;
        }
    }
}
