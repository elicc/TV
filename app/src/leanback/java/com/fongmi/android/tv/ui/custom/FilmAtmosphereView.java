package com.fongmi.android.tv.ui.custom;

import android.animation.ValueAnimator;
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
import com.fongmi.android.tv.ui.motion.TvMotion;
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
    private ValueAnimator artworkFade;
    private float artworkAlpha = 1f;
    private RequestManager requests;
    private CustomTarget<Bitmap> target;
    /** Identity of the artwork currently on screen (updated only on load success). */
    private String sourceKey = "";
    private String imageUrl = "";
    /** Identity of the most recent setImage request, satisfied or not. */
    private String requestSource = "";
    private String requestUrl = "";
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
        saturation.setSaturation(0.72f);
        imagePaint.setColorFilter(new ColorMatrixColorFilter(saturation));
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /**
     * Main-thread API. Full request identity is retained, including source-provided headers.
     * The previously displayed artwork stays on screen until the new one is ready, so
     * focus-driven swaps never flash the fallback background.
     */
    public void setImage(@Nullable String sourceKey, @Nullable String url) {
        String nextSource = sourceKey == null ? "" : sourceKey;
        String nextUrl = url == null ? "" : url;
        if (requestSource.equals(nextSource) && requestUrl.equals(nextUrl)) return;
        // Supersede any in-flight request for the previous identity without dropping
        // the displayed bitmap; the stale target fails its generation check and is
        // discarded when it lands.
        generation++;
        pending = false;
        removeCallbacks(load);
        CustomTarget<Bitmap> old = target;
        target = null;
        if (old != null && requests != null) requests.clear(old);
        requestSource = nextSource;
        requestUrl = nextUrl;
        schedule();
    }

    /** Releases artwork immediately; never substitutes an app logo for missing film imagery. */
    public void clear() {
        imageUrl = "";
        sourceKey = "";
        requestSource = "";
        requestUrl = "";
        releaseRequest();
    }

    private void schedule() {
        removeCallbacks(load);
        pending = false;
        if (bitmap != null && sourceKey.equals(requestSource) && imageUrl.equals(requestUrl)) return;
        if (isAttachedToWindow() && getWindowVisibility() == VISIBLE && isShown() && !TextUtils.isEmpty(requestUrl)) pending = postDelayed(load, 300);
    }

    private void releaseRequest() {
        generation++;
        pending = false;
        removeCallbacks(load);
        cancelArtworkFade();
        bitmap = null;
        CustomTarget<Bitmap> old = target;
        target = null;
        if (old != null && requests != null) requests.clear(old);
        invalidate();
    }

    private void startArtworkFade() {
        cancelArtworkFade();
        if (!TvMotion.motionEnabled(this)) {
            invalidate();
            return;
        }
        artworkAlpha = 0f;
        artworkFade = ValueAnimator.ofFloat(0f, 1f);
        artworkFade.setDuration(TvMotion.HERO_ARTWORK_FADE);
        artworkFade.addUpdateListener(value -> {
            artworkAlpha = (float) value.getAnimatedValue();
            invalidate();
        });
        artworkFade.start();
    }

    private void cancelArtworkFade() {
        if (artworkFade != null) {
            artworkFade.cancel();
            artworkFade = null;
        }
        artworkAlpha = 1f;
    }

    private void loadImage() {
        pending = false;
        if (!isAttachedToWindow() || getWindowVisibility() != VISIBLE || !isShown() || TextUtils.isEmpty(requestUrl)) return;
        final long expected = generation;
        // Retire a still-pending target for this same request (e.g. re-attach) before
        // issuing a duplicate; detach the field first so its callbacks self-discard.
        CustomTarget<Bitmap> previous = target;
        target = null;
        if (previous != null && requests != null) requests.clear(previous);
        try {
            Object model = ImgUtil.getUrl(requestUrl);
            if (model == null) return;
            ObjectKey signature = signature(requestSource, requestUrl, model);
            requests = Glide.with(this);
            target = new CustomTarget<Bitmap>(AtmosphereTransformation.WIDTH, AtmosphereTransformation.HEIGHT) {
                @Override
                public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                    if (expected != generation || target != this || !isAttachedToWindow()) return;
                    bitmap = resource;
                    sourceKey = requestSource;
                    imageUrl = requestUrl;
                    startArtworkFade();
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
                    // A failed fetch keeps the previously displayed artwork; blanking
                    // the whole screen over one broken poster is the worse failure.
                    target = null;
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
            // With no artwork this decorative layer stays fully transparent so
            // underlying chrome (e.g. Home's ambient glow) remains visible.
            if (bitmap == null || bounds.isEmpty()) return;
            canvas.drawColor(background);
            float scale = Math.max((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
            int width = Math.min(bitmap.getWidth(), Math.round(getWidth() / scale));
            int height = Math.min(bitmap.getHeight(), Math.round(getHeight() / scale));
            int left = (bitmap.getWidth() - width) / 2;
            int top = (bitmap.getHeight() - height) / 2;
            crop.set(left, top, left + width, top + height);
            imagePaint.setAlpha(Math.round(255 * artworkAlpha));
            canvas.drawBitmap(bitmap, crop, bounds, imagePaint);
            imagePaint.setAlpha(255);
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
