package com.aroslabs.livephotopainterwallpaper.Engine;

import android.opengl.GLES20;

public class FrameBuffer {

    private int offscreenTexture;
    private int framebuffer;
    private int renderBuffer;

    public int getOffscreenTexture() {
        return offscreenTexture;
    }

    public int getFramebuffer() {
        return framebuffer;
    }

    public int getRenderBuffer() {
        return renderBuffer;
    }

    public void prepareFramebuffer(int width, int height, boolean setViewport) {
        int[] values = new int[1];

        if (setViewport) {
            GLES20.glViewport(0, 0, width, height);
        }

        // Create a texture object and bind it.  This will be the color buffer.
        GLES20.glGenTextures(1, values, 0);
        offscreenTexture = values[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, offscreenTexture);
        GLUtil.checkGlError("glBindTexture " + offscreenTexture);

        // Create texture storage.
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        // Set parameters.  We're probably using non-power-of-two dimensions, so
        // some values may not be available for use.
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GLUtil.checkGlError("glTexParameter");

        // Create framebuffer object and bind it.
        GLES20.glGenFramebuffers(1, values, 0);
        GLUtil.checkGlError("glGenFramebuffers");
        framebuffer = values[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
        GLUtil.checkGlError("glBindFramebuffer " + framebuffer);

        // Create a render buffer and bind it.
        GLES20.glGenRenderbuffers(1, values, 0);
        GLUtil.checkGlError("glGenRenderbuffers");
        renderBuffer = values[0];
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, renderBuffer);
        GLUtil.checkGlError("glBindRenderbuffer " + renderBuffer);

        // Allocate storage for the render buffer.
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16,
                width, height);
        GLUtil.checkGlError("glRenderbufferStorage");

        // Attach the render buffer and the texture (color buffer) to the framebuffer object.
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
                GLES20.GL_RENDERBUFFER, renderBuffer);
        GLUtil.checkGlError("glFramebufferRenderbuffer");
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, offscreenTexture, 0);
        GLUtil.checkGlError("glFramebufferTexture2D");

        // See if GLES is happy with all this.
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer not complete, status=" + status);
        }

        GLUtil.checkGlError("prepareFramebuffer done");
    }

    public void releaseFrameBuffer() {
        int values[] = new int[1];

        if (offscreenTexture > 0) {
            values[0] = offscreenTexture;
            GLES20.glDeleteTextures(1, values, 0);
            offscreenTexture = -1;
        }
        if (framebuffer > 0) {
            values[0] = framebuffer;
            GLES20.glDeleteFramebuffers(1, values, 0);
            framebuffer = -1;
        }
        if (renderBuffer > 0) {
            values[0] = renderBuffer;
            GLES20.glDeleteRenderbuffers(1, values, 0);
            renderBuffer = -1;
        }
    }

}