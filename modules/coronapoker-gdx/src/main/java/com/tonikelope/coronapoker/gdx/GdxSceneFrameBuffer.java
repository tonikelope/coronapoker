/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL30C.*;

/** A sampleable scene layer with the same coverage sampling as the window. */
final class GdxSceneFrameBuffer extends FrameBuffer {
    private int drawTarget, colour, depth;

    GdxSceneFrameBuffer(Pixmap.Format format, int width, int height, boolean withDepth) {
        super(format, width, height, withDepth);
        if (!GL.getCapabilities().OpenGL30) return;
        int previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int previousRenderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING);
        try {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
            int samples = Math.min(glGetInteger(GL_SAMPLES), glGetInteger(GL_MAX_SAMPLES));
            if (samples < 2) return;
            drawTarget = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, drawTarget);
            colour = attach(GL_COLOR_ATTACHMENT0, GL_RGBA8, width, height, samples);
            if (withDepth) depth = attach(GL_DEPTH_ATTACHMENT, GL_DEPTH_COMPONENT24, width, height, samples);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                releaseMultisample();
                Gdx.app.log("MSAA", "Scene target unavailable at " + samples + " samples");
            }
        } finally {
            glBindRenderbuffer(GL_RENDERBUFFER, previousRenderbuffer);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDraw);
        }
    }

    private static int attach(int attachment, int format, int width, int height, int samples) {
        int handle = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, handle);
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, format, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, attachment, GL_RENDERBUFFER, handle);
        return handle;
    }

    @Override public void bind() {
        if (drawTarget == 0) super.bind();
        else glBindFramebuffer(GL_FRAMEBUFFER, drawTarget);
    }

    @Override public void end(int x, int y, int width, int height) {
        if (drawTarget != 0) {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, drawTarget);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, getFramebufferHandle());
            boolean scissor = glIsEnabled(GL_SCISSOR_TEST);
            if (scissor) glDisable(GL_SCISSOR_TEST);
            glBlitFramebuffer(0, 0, getWidth(), getHeight(), 0, 0, getWidth(), getHeight(),
                    GL_COLOR_BUFFER_BIT, GL_NEAREST);
            if (scissor) glEnable(GL_SCISSOR_TEST);
        }
        super.end(x, y, width, height);
    }

    private void releaseMultisample() {
        if (depth != 0) glDeleteRenderbuffers(depth);
        if (colour != 0) glDeleteRenderbuffers(colour);
        if (drawTarget != 0) glDeleteFramebuffers(drawTarget);
        depth = colour = drawTarget = 0;
    }

    @Override public void dispose() {
        releaseMultisample();
        super.dispose();
    }
}
