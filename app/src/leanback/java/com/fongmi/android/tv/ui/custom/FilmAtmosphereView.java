package com.fongmi.android.tv.ui.custom;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
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
import com.fongmi.android.tv.utils.AtmosphereTransformation;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.TvTheme;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/** Decorative film-only layer. Foreground content must remain outside this view. */
public final class FilmAtmosphereView extends View implements ComponentCallbacks2 {
    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint maskPaint = new Paint();
    private final Rect crop = new Rect();
    private final Rect bounds = new Rect();
    private final Runnable load = this::loadImage;
    private final int background;
    private final int scrim;
    private final int strongScrim;
    private Shader leftMask;
    private Shader bottomMask;
    private Shader topMask;
    private Bitmap bitmap;
    private RequestManager requests;
    private CustomTarget<Bitmap> target;
    private String sourceKey = "";
    private String imageUrl = "";
    private long generation;
    private boolean pending;

    public FilmAtmosphereView(Context context) {
        this(context, null);
    }

    public FilmAtmosphereView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FilmAtmosphereView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        background = TvTheme.color(context, R.attr.tvColorBackground);
        scrim = TvTheme.color(context, R.attr.tvColorScrim);
        strongScrim = TvTheme.color(context, R.attr.tvColorScrimStrong);
        ColorMatrix saturation = new ColorMatrix();
        saturation.setSaturation(0.55f);
        imagePaint.setColorFilter(new ColorMatrixColorFilter(saturation));
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Main-thread API. Full request identity is retained, including source-provided headers. */
    public void setImage(@Nullable String sourceKey, @Nullable String url) {
        String nextSource = sourceKey == null ? "" : sourceKey;
        String nextUrl = url == null ? "" : url;
        if (this.sourceKey.equals(nextSource) && imageUrl.equals(nextUrl) && (bitmap != null || target != null || pending)) return;
        releaseRequest();
        this.sourceKey = nextSource;
        imageUrl = nextUrl;
        schedule();
    }

    /** Releases artwork immediately; never substitutes an app logo for missing film imagery. */
    public void clear() {
        imageUrl = "";
        sourceKey = "";
        releaseRequest();
    }

    private void schedule() {
        removeCallbacks(load);
        pending = false;
        if (target != null || bitmap != null) return;
        if (isAttachedToWindow() && getWindowVisibility() == VISIBLE && isShown() && !TextUtils.isEmpty(imageUrl)) pending = postDelayed(load, 300);
    }

    private void releaseRequest() {
        generation++;
        pending = false;
        removeCallbacks(load);
        bitmap = null;
        CustomTarget<Bitmap> old = target;
        target = null;
        if (old != null && requests != null) requests.clear(old);
        invalidate();
    }

    private void loadImage() {
        pending = false;
        if (!isAttachedToWindow() || getWindowVisibility() != VISIBLE || !isShown() || TextUtils.isEmpty(imageUrl)) return;
        final long expected = generation;
        try {
            Object model = ImgUtil.getUrl(imageUrl);
            if (model == null) return;
            ObjectKey signature = signature(sourceKey, imageUrl, model);
            requests = Glide.with(this);
            target = new CustomTarget<Bitmap>(AtmosphereTransformation.WIDTH, AtmosphereTransformation.HEIGHT) {
                @Override
                public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                    if (expected != generation || target != this || !isAttachedToWindow()) return;
                    bitmap = resource;
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
                    bitmap = null;
                    invalidate();
                }
            };
            requests.asBitmap().load(model).signature(signature)
                    .override(AtmosphereTransformation.WIDTH, AtmosphereTransformation.HEIGHT)
                    .downsample(DownsampleStrategy.AT_MOST).disallowHardwareConfig()
                    .transform(new AtmosphereTransformation()).diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .dontAnimate().into(target);
        } catch (RuntimeException ignored) {
            // Malformed source metadata or a torn-down context must not interrupt browsing.
            releaseRequest();
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
        if (width == 0 || height == 0) return;
        int transparent = background & 0x00ffffff;
        leftMask = new LinearGradient(0, 0, width * 0.8f, 0, strongScrim, transparent, Shader.TileMode.CLAMP);
        topMask = new LinearGradient(0, 0, 0, height * 0.35f, strongScrim, transparent, Shader.TileMode.CLAMP);
        bottomMask = new LinearGradient(0, height * 0.45f, 0, height, transparent, background, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        // Leanback parents intentionally disable child clipping for focus zoom. Keep
        // this decorative layer inside its own bounds, even while showing fallback.
        int save = canvas.save();
        try {
            canvas.clipRect(0, 0, getWidth(), getHeight());
            canvas.drawColor(background);
            if (bitmap == null || bounds.isEmpty()) return;
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
        getContext().getApplicationContext().registerComponentCallbacks(this);
        schedule();
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseRequest();
        getContext().getApplicationContext().unregisterComponentCallbacks(this);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) schedule();
        else releaseRequest();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        // XML inflation can dispatch visibility callbacks before our fields exist.
        if (load == null) return;
        if (visibility == VISIBLE) schedule();
        else releaseRequest();
    }

    @Override
    public void onTrimMemory(int level) {
        if (level >= TRIM_MEMORY_RUNNING_LOW) releaseRequest();
    }

    @Override
    public void onLowMemory() {
        releaseRequest();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration configuration) {
        // The owning Activity recreates views when applying a new skin.
    }
}
