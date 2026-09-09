package com.fongmi.android.tv.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Runs on Glide decode workers, never on the UI drawing path. */
public final class AtmosphereTransformation extends BitmapTransformation {
    public static final int WIDTH = 320;
    public static final int HEIGHT = 180;
    private static final byte[] KEY = "tv.atmosphere.box.v1.320x180.radius12.passes2".getBytes(StandardCharsets.UTF_8);

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap source, int outWidth, int outHeight) {
        Bitmap result = pool.get(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        result.eraseColor(0);
        result.setHasAlpha(true);
        float scale = Math.max((float) WIDTH / source.getWidth(), (float) HEIGHT / source.getHeight());
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((WIDTH - source.getWidth() * scale) / 2f, (HEIGHT - source.getHeight() * scale) / 2f);
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(source, matrix, new Paint(Paint.FILTER_BITMAP_FLAG));
        canvas.setBitmap(null);
        int[] pixels = new int[WIDTH * HEIGHT];
        result.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
        AtmosphereBlur.apply(pixels, WIDTH, HEIGHT, 12);
        result.setPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
        return result;
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest digest) {
        digest.update(KEY);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AtmosphereTransformation;
    }

    @Override
    public int hashCode() {
        return 0x41544d31;
    }
}
