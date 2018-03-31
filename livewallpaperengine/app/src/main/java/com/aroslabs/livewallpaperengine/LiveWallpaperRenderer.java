package com.aroslabs.livewallpaperengine;

import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.view.MotionEvent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Example of a simple renderer that draws a colored quad.
 */
public class LiveWallpaperRenderer implements GLWallpaperService.Renderer{

    private static String TAG = "LiveWallpaperRenderer";

    public static final String vertexShader =
            "uniform mat4 uMVPMatrix;" +
            "attribute vec4 aPosition;" +
            "void main() {" +
            "  gl_Position = uMVPMatrix*aPosition;" +
            "}";

    public static final String fragmentShader =
            "precision mediump float;" +
            "void main() {" +
            "  gl_FragColor = vec4(0.5,0,0,1);" +
            "}";

    private final float[] coordsData = {
            1.0f, 0.0f,
            0.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 1.0f,
    };

    private int programId;
    private int uniformMVPMatrixLocation;
    private int attribPositionLocation;
    private float[] MVPMatrix = new float[16];
    private FloatBuffer vertexCoords;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

        programId = createProgram(vertexShader, fragmentShader);
        if (programId == 0) {
            return;
        }

        uniformMVPMatrixLocation = GLES20.glGetUniformLocation(programId, "uMVPMatrix");
        checkGlError("glGetUniformLocation uMVPMatrix");
        if (uniformMVPMatrixLocation == -1) {
            throw new RuntimeException("Couldn't get uniform location for uMVPMatrix");
        }

        attribPositionLocation = GLES20.glGetAttribLocation(programId, "aPosition");
        checkGlError("glGetAttribLocation aPosition");
        if (attribPositionLocation == -1) {
            throw new RuntimeException("Couldn't get attrib location for aPosition");
        }

        vertexCoords = ByteBuffer.allocateDirect(coordsData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexCoords.put(coordsData).position(0);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glUseProgram(programId);

        // Clear Screen And Depth Buffer
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT);

        //Enable blending
        GLES20.glEnable(GLES20.GL_BLEND);
        checkGlError("enable blend");
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        checkGlError("blend func");

        // No culling of back faces
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        checkGlError("disable cull face");

        //Disable depth testing
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        checkGlError("disable depth");

        GLES20.glUniformMatrix4fv(uniformMVPMatrixLocation, 1, false, MVPMatrix, 0);

        // Vertex positions
        GLES20.glVertexAttribPointer(attribPositionLocation, 2, GLES20.GL_FLOAT, false, 0, vertexCoords);
        checkGlError("glVertexAttribPointer attribPositionLocation");
        GLES20.glEnableVertexAttribArray(attribPositionLocation);
        checkGlError("glEnableVertexAttribArray attribPositionLocation");

        //crop via ortho projection
        float[] projectionMatrix = new float[16];
        RectF crop = new RectF(0, 0, 1, 1);

        Matrix.setIdentityM(MVPMatrix, 0);
        Matrix.setIdentityM(projectionMatrix, 0);
        Matrix.orthoM(projectionMatrix, 0, crop.left, crop.right, crop.bottom, crop.top, 0, 1);
        Matrix.multiplyMM(MVPMatrix, 0, projectionMatrix, 0, MVPMatrix, 0);
        GLES20.glUniformMatrix4fv(uniformMVPMatrixLocation, 1, false, MVPMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    public void release() {
        //Release any textures etc here
    }

    public void onTouchEvent(MotionEvent event) {

    }

    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        checkGlError("glCreateShader type=" + shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + shaderType + ":");
            Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }
    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0) {
            return 0;
        }
        int program = GLES20.glCreateProgram();
        checkGlError("glCreateProgram");
        if (program == 0) {
            Log.e(TAG, "Could not create program");
        }
        GLES20.glAttachShader(program, vertexShader);
        checkGlError("glAttachShader");
        GLES20.glAttachShader(program, pixelShader);
        checkGlError("glAttachShader");
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ");
            Log.e(TAG, GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
        } else {
            //After program is linked detach and delete to clean up
            GLES20.glDetachShader(program, vertexShader);
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDetachShader(program, pixelShader);
            GLES20.glDeleteShader(pixelShader);
        }
        return program;
    }
    public void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }
}
