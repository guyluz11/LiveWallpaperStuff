package com.aroslabs.livephotopainterwallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;
import android.view.MotionEvent;

import com.aroslabs.livephotopainterwallpaper.Engine.Bitmaps;
import com.aroslabs.livephotopainterwallpaper.Engine.GLUtil;
import com.aroslabs.livephotopainterwallpaper.Engine.GLWallpaperService;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import jp.co.cyberagent.android.gpuimage.GPUImageFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageKuwaharaFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageSketchFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageSmoothToonFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageToonFilter;

/**
 * Created by Ryan on 1/22/2017.
 */
public class LivePhotoPainterRenderer implements GLWallpaperService.Renderer{

    private static String TAG = "LivePhotoPainterRenderer";

    private Context context;
    private int viewWidth;
    private int viewHeight;
    private float viewAspectRatio;
    private RectF crop;

    public static final String vertexShader =
            "uniform mat4 uMVPMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec2 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "    gl_Position = uMVPMatrix*aPosition;\n" +
            "    vTextureCoord = aTextureCoord;\n" +
            "}";

    public static final String fragmentShader =
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform sampler2D sTexture;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}";

    private final float[] uvData = {
            1.0f, 0.0f,
            0.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 1.0f,
    };

    final float vertexDataGpuImage[] = {
            -1.0f, 1.0f, 0f, 0f,
            1.0f, 1.0f, 0f, 0f,
            -1.0f, -1.0f, 0f, 0f,
            1.0f, -1.0f, 0f, 0f,
    };

    private final float[] vertexData = {
            1.0f, 0.0f,
            0.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 1.0f,
    };

    //Shader variables
    private int programId;
    private int uMVPMatrixLocation;
    private int aPositionLocation;
    private int aTextureCoordLocation;
    private int uTextureSamplerLocation;

    //data
    private float[] MVPMatrix = new float[16];
    private FloatBuffer vertexCoords;
    private FloatBuffer uvCoords;
    private int[] texIds = new int[1];

    //properties
    private int radius = 5;

    //GPUImage stuff
    private List<GPUImageFilter> filters = new ArrayList<>();
    private int filterIndex = 0;
    private boolean useOffscreenBuffer = false;

    //Simulation logic
    private long lastLoadedTimeMs;

    //Parameters
    private long intervalPeriodMs = 1000;
    private int filterState = 0;

    public LivePhotoPainterRenderer(Context context) {
        this.context = context;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        programId = GLUtil.createProgram(vertexShader, fragmentShader);
        if (programId == 0) {
            return;
        }

        GLES20.glUseProgram(programId);

        uMVPMatrixLocation = GLES20.glGetUniformLocation(programId, "uMVPMatrix");
        GLUtil.checkGlError("glGetUniformLocation uMVPMatrix");
        if (uMVPMatrixLocation == -1) {
            throw new RuntimeException("Couldn't get uniform location for uMVPMatrix");
        }

        aPositionLocation = GLES20.glGetAttribLocation(programId, "aPosition");
        GLUtil.checkGlError("glGetAttribLocation aPosition");
        if (aPositionLocation == -1) {
            throw new RuntimeException("Couldn't get attrib location for aPosition");
        }
        aTextureCoordLocation = GLES20.glGetAttribLocation(programId, "aTextureCoord");
        GLUtil.checkGlError("glGetAttribLocation aTextureCoord");
        if (aTextureCoordLocation == -1) {
            throw new RuntimeException("Couldn't get attrib location for aTextureCoord");
        }
        uTextureSamplerLocation = GLES20.glGetUniformLocation(programId, "sTexture");
        GLUtil.checkGlError("glGetUniformLocation sTexture");
        if (uTextureSamplerLocation == -1) {
            throw new RuntimeException("Couldn't get uniform location for sTexture");
        }

        vertexCoords = ByteBuffer.allocateDirect(vertexDataGpuImage.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexCoords.put(vertexDataGpuImage).position(0);
        uvCoords = ByteBuffer.allocateDirect(uvData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        uvCoords.put(uvData).position(0);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        viewWidth = width;
        viewHeight = height;
        viewAspectRatio = (float)width / height;
        GLES20.glGenTextures(texIds.length, texIds, 0);

        initializeFilters(width, height);

        loadRandomBitmap(width, height);

        GLUtil.checkGlError("init filter");
    }

    @Override
    public void onDrawFrame(GL10 gl) {

        if (System.currentTimeMillis() >= lastLoadedTimeMs + intervalPeriodMs) {
            //loadRandomBitmap(viewWidth, viewHeight);
        }

        if (useOffscreenBuffer) {
            //TODO: Have a current texture id and then have an offscreen buffer texture id where we
            // work on the next frame.  Hook up gpuimage and filter that mofo.
            //get exif rotation, width and height (for aspect)

            GLES20.glUseProgram(programId);

            // Clear Screen And Depth Buffer
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT);

            //Enable blending
            GLES20.glEnable(GLES20.GL_BLEND);
            GLUtil.checkGlError("enable blend");
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLUtil.checkGlError("blend func");

            // No culling of back faces
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLUtil.checkGlError("disable cull face");

            //Disable depth testing
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLUtil.checkGlError("disable depth");

            GLES20.glUniformMatrix4fv(uMVPMatrixLocation, 1, false, MVPMatrix, 0);

            // Vertex positions
            vertexCoords.position(0);
            GLES20.glVertexAttribPointer(aPositionLocation, 2, GLES20.GL_FLOAT, false, 0, vertexCoords);
            GLUtil.checkGlError("glVertexAttribPointer aPositionLocation");
            GLES20.glEnableVertexAttribArray(aPositionLocation);
            GLUtil.checkGlError("glEnableVertexAttribArray aPositionLocation");

            // Texture coords
            uvCoords.position(0);
            GLES20.glVertexAttribPointer(aTextureCoordLocation, 2, GLES20.GL_FLOAT, false, 0, uvCoords);
            GLUtil.checkGlError("glVertexAttribPointer attribTextureCoordLocation");
            GLES20.glEnableVertexAttribArray(aTextureCoordLocation);
            GLUtil.checkGlError("glEnableVertexAttribArray attribTextureCoordLocation");

            //crop via ortho projection
            float[] projectionMatrix = new float[16];

            //crop via ortho projection
            Matrix.setIdentityM(projectionMatrix, 0);
            Matrix.setIdentityM(MVPMatrix, 0);

            int preRotate = 90;
            int rotation = 90;
            RectF crop = new RectF(0, 0, 1, 1);

            if(preRotate == 90){
                Matrix.orthoM(projectionMatrix, 0, crop.top, crop.bottom, crop.right, crop.left, 0, 1);
            } else if(preRotate == 180) {
                Matrix.orthoM(projectionMatrix, 0, crop.left, crop.right, crop.bottom, crop.top, 0, 1);
            }
            else if(preRotate == 270) {
                Matrix.orthoM(projectionMatrix, 0, crop.top, crop.bottom, crop.right, crop.left, 0, 1);
            }
            else {
                Matrix.orthoM(projectionMatrix, 0, crop.left, crop.right, crop.bottom, crop.top, 0, 1);
            }

            //pre-rotate if necessary
            if(preRotate != 0) {
                float[] preRotationMatrix = new float[16];
                Matrix.setIdentityM(preRotationMatrix, 0);
                Matrix.rotateM(preRotationMatrix, 0, -preRotate, 0f, 0f, 1f);
                Matrix.multiplyMM(projectionMatrix, 0, preRotationMatrix, 0, projectionMatrix, 0);
            }

            //user rotation
            float[] rotationMatrix = new float[16];
            Matrix.setIdentityM(rotationMatrix, 0);
            Matrix.translateM(rotationMatrix, 0, 0.5f, 0.5f, 0f);
            Matrix.rotateM(rotationMatrix, 0, rotation, 0f, 0f, 1f);
            Matrix.translateM(rotationMatrix, 0, -0.5f, -0.5f, 0f);

            //compose
            Matrix.multiplyMM(MVPMatrix, 0, projectionMatrix, 0, rotationMatrix, 0);

            GLES20.glUniformMatrix4fv(uMVPMatrixLocation, 1, false, MVPMatrix, 0);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[0]);
            GLES20.glUniform1i(uTextureSamplerLocation, 0);
        } else {
            Matrix.setIdentityM(MVPMatrix, 0);
            Matrix.orthoM(MVPMatrix, 0, crop.left, crop.right, crop.bottom, crop.top, 0, 1);

            float [] twoDConvertedBuffer = new float[8];
            float [] position = new float[16];
            Matrix.multiplyMM(position, 0, MVPMatrix, 0, vertexDataGpuImage, 0);

            //Extract x,y from the 4x4 ortho matrix
            for (int i = 0; i < 4; i++) {
                twoDConvertedBuffer[i*2] = position[i*4];
                twoDConvertedBuffer[i*2+1] = position[i*4 + 1];
            }

            vertexCoords.put(twoDConvertedBuffer).position(0);
            filters.get(filterIndex).onDraw(texIds[0], vertexCoords, uvCoords);
        }
    }

    public void release() {

    }

    public void onTouchEvent(MotionEvent event) {

    }

    private void initializeFilters(int width, int height) {
        for (GPUImageFilter filter : filters) {
            filter.destroy();
        }

        filters.clear();

        //setup filters
        filters.add(new GPUImageKuwaharaFilter(5));
        filters.add(new GPUImageSketchFilter());
        filters.add(new GPUImageToonFilter());
        filters.add(new GPUImageSmoothToonFilter());


        for (GPUImageFilter filter : filters) {
            filter.init();
            filter.onOutputSizeChanged(width, height);
        }
    }

    private void selectFilter() {
        Random random = new Random(System.currentTimeMillis());
        filterIndex = random.nextInt(filters.size());
    }

    private void loadRandomBitmap(int width, int height) {
        //Get random bitmap uri
        Uri uri = Bitmaps.getBitmapFromGallery(context);

        //get filter
        selectFilter();


        try {
            //Scale by matching the largest dimension of both dimensions (view and bitmap) and making a ratio
            int desiredWidth, desiredHeight;
            float scaleRatio = 1.0f;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(new File(uri.getPath()).getAbsolutePath(),
                    options);

            int largestDimension = width > height ? width : height;
            int largestBitmapDimension = options.outWidth > options.outHeight ? options.outWidth :
                    options.outHeight;

            if (largestBitmapDimension > largestDimension) {
                scaleRatio = largestDimension / (float)largestBitmapDimension;
            }

            desiredWidth = (int)(options.outWidth * scaleRatio);
            desiredHeight = (int)(options.outHeight * scaleRatio);
            Bitmap scaledBitmap = Bitmaps.loadScaledBitmap(uri, desiredWidth, desiredHeight);

            crop = Bitmaps.getDefaultFillCrop(scaledBitmap.getWidth() / (float)scaledBitmap.getHeight(), viewAspectRatio, scaledBitmap.getWidth(), scaledBitmap.getHeight());

            //Load into texture
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, scaledBitmap, 0);

            //TODO create crop
            //float bitmapAspectRatio = bitmap.getWidth()/(float)bitmap.getHeight();
            //float desiredAspectRatio = width/(float)height;

            lastLoadedTimeMs = System.currentTimeMillis();
        } catch (IOException e) {
            Log.d(TAG, "cannot open file?");
            e.printStackTrace();
        }
    }
}
