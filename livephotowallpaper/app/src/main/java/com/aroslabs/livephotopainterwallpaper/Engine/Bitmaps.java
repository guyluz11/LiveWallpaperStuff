package com.aroslabs.livephotopainterwallpaper.Engine;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by Ryan on 1/25/2017.
 */

public class Bitmaps {

    private static final String TAG = "Bitmaps";

    public static Uri getBitmapFromGallery(Context context) {
        Cursor cursor = context.getContentResolver()
                .query(
                        MediaStore.Files.getContentUri("external"),
                        new String[]{
                                MediaStore.Files.FileColumns._ID,
                                MediaStore.Files.FileColumns.DATA,
                                MediaStore.Files.FileColumns.DATE_ADDED,
                                MediaStore.Files.FileColumns.MEDIA_TYPE,
                                MediaStore.Images.ImageColumns.BUCKET_ID,
                                MediaStore.Images.ImageColumns.DATE_TAKEN,
                                MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
                                MediaStore.Images.ImageColumns.ORIENTATION,
                                MediaStore.Video.VideoColumns.DURATION
                        },
                        MediaStore.Files.FileColumns.MEDIA_TYPE+" in(?, ?)",
                        new String[]{ MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + ""},
                        MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC");

        //TODO: we need a default bitmap
        if (cursor == null) {
            return null;
        }

        cursor.moveToFirst();
        List<Uri> pics = new ArrayList<>();

        for (int i=0; i < cursor.getCount(); i++) {
            //TODO: get orientation

            //TODO: calculate default crop (rotate landscape image and crop, portrait just leave them, always crop to fit
            pics.add(getUri(cursor, MediaStore.Images.ImageColumns.DATA));
            cursor.moveToNext();
        }

        Random random = new Random(System.currentTimeMillis());
        return pics.get(random.nextInt(cursor.getCount()));
    }

    /**
     * Load bitmap and scale via cpu to fit onto the gpu, orient if necessary.
     */
    public static Bitmap loadScaledBitmap(Uri uri, int desiredWidth, int desiredHeight) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(new File(uri.getPath()).getAbsolutePath(), options);

        //scale and rotate
        float scaleWidth = 0, scaleHeight = 0;
        ExifInterface exifInterface = new ExifInterface(uri.getPath());
        int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                scaleWidth = desiredWidth / (float)bitmap.getWidth();
                scaleHeight = desiredHeight / (float)bitmap.getHeight();
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                scaleWidth = desiredWidth / (float)bitmap.getWidth();
                scaleHeight = desiredHeight / (float)bitmap.getHeight();
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                scaleWidth = desiredWidth / (float)bitmap.getWidth();
                scaleHeight = desiredHeight / (float)bitmap.getHeight();
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                scaleWidth = desiredWidth / (float)bitmap.getHeight();
                scaleHeight = desiredHeight / (float)bitmap.getWidth();
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                scaleWidth = desiredWidth / (float)bitmap.getHeight();
                scaleHeight = desiredHeight / (float)bitmap.getWidth();
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                scaleWidth = desiredWidth / (float)bitmap.getHeight();
                scaleHeight = desiredHeight / (float)bitmap.getWidth();
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                scaleWidth = desiredWidth / (float)bitmap.getHeight();
                scaleHeight = desiredHeight / (float)bitmap.getWidth();
                break;
            case ExifInterface.ORIENTATION_NORMAL:
                scaleWidth = desiredWidth / (float)bitmap.getWidth();
                scaleHeight = desiredHeight / (float)bitmap.getHeight();
                break;
            default:
                Log.d(TAG, "hmmmmmm");
                break;
        }

        try {
            //scale
            matrix.postScale(scaleWidth, scaleHeight);
            if (bitmap.getWidth() <= 0) {
                Log.d(TAG, "strange");
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }

    public static RectF getDefaultFillCrop(float bitmapAspectRatio, float desiredAspectRatio, int bitmapWidth, int bitmapHeight) {

        RectF cropRect;
        if(bitmapAspectRatio > desiredAspectRatio) {
            float newWidth = bitmapHeight * desiredAspectRatio;
            float widthDifference = (bitmapWidth - newWidth) / 2.0f;
            float deltaRatio = widthDifference / bitmapWidth;
            cropRect = new RectF(deltaRatio, 0.0f, 1-deltaRatio, 1.0f);

        } else {
            float newHeight = bitmapWidth / desiredAspectRatio;
            float heightDifference = (bitmapHeight - newHeight)/2;
            float deltaRatio = heightDifference / bitmapHeight;
            float offset = deltaRatio*0.5f;
            cropRect = new RectF(0.0f, deltaRatio-offset, 1.0f, 1-deltaRatio-offset);
        }

        return cropRect;
    }

    public static RectF getDefaultFitCrop(float intrinsicAspectRatio, int intrinsicWidth, int intrinsicHeight, float desiredAspectRatio) {
        RectF cropRect;
/*
        //clamp to height of image
        if(intrinsicAspectRatio < desiredAspectRatio) {
            float newWidth = intrinsicHeight * desiredAspectRatio;
            float widthDifference = (intrinsicWidth - newWidth) / 2.0f;
            float deltaRatio = widthDifference / intrinsicWidth;
            cropRect = new RectF(deltaRatio, 0.0f, 1-deltaRatio, 1.0f);

        } else {
            float newHeight = intrinsicWidth / desiredAspectRatio;
            float heightDifference = (intrinsicHeight - newHeight)/2.0f;
            float deltaRatio = heightDifference / intrinsicHeight;

            cropRect = new RectF(0.0f, deltaRatio, 1.0f, 1-deltaRatio);
        }

        Log.d("Crop Rect FIT", cropRect.toShortString());
        */
        return null;
    }

    public static Uri getUri(Cursor cursor, String columnName) {
        Uri uri = null;
        int index = cursor.getColumnIndex(columnName);
        if (index == -1) {
            return null;
        } else if (cursor.isNull(index)) {
            return null;
        }
        String path = cursor.getString(index);

        if (path == null) {
            return null;
        }

        if (new File(path).exists()) {
            uri = Uri.fromFile(new File(path));
        }
        return uri;
    }

}
