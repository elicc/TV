package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.signature.ObjectKey;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.TvTheme;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Crisp, landscape artwork for the home hero. It is intentionally independent
 * from {@link FilmAtmosphereView}: a missing/blocked provider image can reveal
 * the existing blurred atmosphere underneath without affecting focus or rows.
 */
public final class FilmBackdropView extends View {
    // Keep focus changes affordable on low-memory TV SoCs. The view scales this
    // 16:9 bitmap to 4K without making every visited title a multi-megabyte
    // retained software allocation.
    private static final int TARGET_WIDTH = 960;
    private static final int TARGET_HEIGHT = 540;

    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint maskPaint = new Paint();
    private final Rect crop = new Rect();
    private final Rect bounds = new Rect();
    private final int background;
    private final int scrim;
    private final int strongScrim;
    private final Runnable load = this::loadImage;
    private Bitmap bitmap;
    private RequestManager requests;
    private CustomTarget<Bitmap> target;
    private String sourceKey = "";
    private String imageUrl = "";
    private String requestSource = "";
    private String requestUrl = "";
    private long generation;
    private LinearGradient leftMask;
    private LinearGradient topMask;
    private LinearGradient bottomMask;

    public FilmBackdropView(Context context) {
        this(context, null);
    }

    public FilmBackdropView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FilmBackdropView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        background = TvTheme.color(context, R.attr.tvColorBackground);
        scrim = TvTheme.color(context, R.attr.tvColorScrim);
        strongScrim = TvTheme.color(context, R.attr.tvColorScrimStrong);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Main-thread API. The old image is retained for the short network transition. */
    public void setImage(@Nullable String source, @Nullable String url) {
        String nextSource = source == null ? "" : source;
        String nextUrl = url == null ? "" : url;
        if (requestSource.equals(nextSource) && requestUrl.equals(nextUrl)) return;
        generation++;
        removeCallbacks(load);
        CustomTarget<Bitmap> old = target;
        target = null;
        if (old != null && requests != null) requests.clear(old);
        requestSource = nextSource;
        requestUrl = nextUrl;
        if (TextUtils.isEmpty(nextUrl)) {
            bitmap = null;
            sourceKey = "";
            imageUrl = "";
            invalidate();
        } else if (isAttachedToWindow()) {
            postDelayed(load, 350);
        }
    }

    public void clear() {
        setImage("", "");
    }

    private void loadImage() {
        if (!isAttachedToWindow() || !isShown() || TextUtils.isEmpty(requestUrl)) return;
        final long expected = generation;
        try {
            Object model = ImgUtil.getUrl(requestUrl);
            if (model == null) return;
            requests = Glide.with(this);
            ObjectKey signature = signature(requestSource, requestUrl, model);
            target = new CustomTarget<Bitmap>(TARGET_WIDTH, TARGET_HEIGHT) {
                @Override
                public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                    if (expected != generation || target != this || !isAttachedToWindow()) return;
                    bitmap = resource;
                    sourceKey = requestSource;
                    imageUrl = requestUrl;
                    invalidate();
                }

                @Override
                public void onLoadCleared(@Nullable Drawable placeholder) {
                    if (expected != generation || target != this) return;
                    bitmap = null;
                    invalidate();
                }

                @Override
                public void onLoadFailed(@Nullable Drawable errorDrawable) {
                    if (expected != generation || target != this) return;
                    target = null;
                    bitmap = null;
                    sourceKey = "";
                    imageUrl = "";
                    invalidate();
                }
            };
            requests.asBitmap().load(model).signature(signature)
                    .override(TARGET_WIDTH, TARGET_HEIGHT)
                    .downsample(DownsampleStrategy.AT_MOST)
                    // Focus changes can visit dozens of titles in one session. Do not
                    // retain every 1280x720 software bitmap in Glide's memory cache;
                    // the disk cache still makes revisits inexpensive.
                    .disallowHardwareConfig().skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .dontAnimate().into(target);
        } catch (RuntimeException ignored) {
            bitmap = null;
            invalidate();
        }
    }

    private static ObjectKey signature(String source, String url, Object model) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, source);
            update(digest, url);
            if (model instanceof GlideUrl) {
                GlideUrl glideUrl = (GlideUrl) model;
                update(digest, glideUrl.toStringUrl());
                for (Map.Entry<String, String> header : new TreeMap<>(glideUrl.getHeaders()).entrySet()) {
                    update(digest, header.getKey());
                    update(digest, header.getValue());
                }
            }
            StringBuilder key = new StringBuilder(64);
            for (byte value : digest.digest()) key.append(Character.forDigit((value & 255) >>> 4, 16)).append(Character.forDigit(value & 15, 16));
            return new ObjectKey(key.toString());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;
        digest.update(new byte[]{(byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length});
        digest.update(bytes);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        bounds.set(0, 0, width, height);
        if (width <= 0 || height <= 0) return;
        leftMask = new LinearGradient(0, 0, width * .8f, 0, strongScrim, 0x00111111, Shader.TileMode.CLAMP);
        topMask = new LinearGradient(0, 0, 0, height * .35f, strongScrim, 0x00111111, Shader.TileMode.CLAMP);
        bottomMask = new LinearGradient(0, height * .45f, 0, height, 0x00111111, background, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || bounds.isEmpty()) return;
        int save = canvas.save();
        try {
            canvas.clipRect(bounds);
            canvas.drawColor(background);
            float scale = Math.max((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
            int width = Math.min(bitmap.getWidth(), Math.round(getWidth() / scale));
            int height = Math.min(bitmap.getHeight(), Math.round(getHeight() / scale));
            int left = (bitmap.getWidth() - width) / 2;
            int top = (bitmap.getHeight() - height) / 2;
            crop.set(left, top, left + width, top + height);
            canvas.drawBitmap(bitmap, crop, bounds, imagePaint);
            canvas.drawColor(scrim);
            maskPaint.setShader(leftMask);
            canvas.drawRect(bounds, maskPaint);
            maskPaint.setShader(topMask);
            canvas.drawRect(bounds, maskPaint);
            maskPaint.setShader(bottomMask);
            canvas.drawRect(bounds, maskPaint);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!TextUtils.isEmpty(requestUrl)) postDelayed(load, 350);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(load);
        CustomTarget<Bitmap> old = target;
        target = null;
        if (old != null && requests != null) requests.clear(old);
        super.onDetachedFromWindow();
    }
}
