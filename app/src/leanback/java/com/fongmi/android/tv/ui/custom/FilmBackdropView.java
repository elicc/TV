package com.fongmi.android.tv.ui.custom;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
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
import com.fongmi.android.tv.ui.motion.TvMotion;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.TvTheme;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private static final long CAROUSEL_INTERVAL = 7000;

    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint maskPaint = new Paint();
    private final Rect crop = new Rect();
    private final Rect bounds = new Rect();
    private final int background;
    private final int softScrim;
    private final int strongScrim;
    private final Runnable load = this::loadImage;
    private final Runnable advance = this::advanceCarousel;
    private Bitmap bitmap;
    private Bitmap previousBitmap;
    private ValueAnimator artworkFade;
    private float artworkAlpha = 1f;
    private RequestManager requests;
    private CustomTarget<Bitmap> target;
    private String sourceKey = "";
    private String imageUrl = "";
    private String requestSource = "";
    private String requestUrl = "";
    private List<String> carouselUrls = Collections.emptyList();
    private int carouselIndex = -1;
    private OnArtworkChangedListener carouselListener;
    private long generation;
    private LinearGradient leftMask;
    private LinearGradient topMask;
    private LinearGradient bottomMask;

    public interface OnArtworkChangedListener {
        void onArtworkChanged(int position);
    }

    public FilmBackdropView(Context context) {
        this(context, null);
    }

    public FilmBackdropView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FilmBackdropView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        background = TvTheme.color(context, R.attr.tvColorBackground);
        int scrim = TvTheme.color(context, R.attr.tvColorScrim);
        // Keep the text-side gradients strong while allowing the provider's
        // landscape artwork to read clearly on the right half of the screen.
        softScrim = (scrim & 0x00ffffff) | 0x66000000;
        strongScrim = TvTheme.color(context, R.attr.tvColorScrimStrong);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Main-thread API. The old image is retained for the short network transition. */
    public void setImage(@Nullable String source, @Nullable String url) {
        stopCarousel();
        requestImage(source, url);
    }

    /** Starts a calm, looping provider-artwork carousel. */
    public void setCarousel(@Nullable String source, @Nullable List<String> urls, int startPosition,
                            @Nullable OnArtworkChangedListener listener) {
        List<String> next = sanitize(urls);
        String nextSource = source == null ? "" : source;
        boolean unchanged = nextSource.equals(requestSource) && next.equals(carouselUrls);
        removeCallbacks(advance);
        carouselUrls = next;
        carouselListener = listener;
        if (next.isEmpty()) {
            carouselIndex = -1;
            requestImage("", "");
            return;
        }
        int nextPosition = Math.max(0, Math.min(startPosition, next.size() - 1));
        if (unchanged && carouselIndex >= 0) nextPosition = carouselIndex;
        showCarouselItem(nextSource, nextPosition);
    }

    /** Selects a gallery item and continues automatic rotation from it. */
    public void selectCarouselItem(int position) {
        if (carouselUrls.isEmpty()) return;
        int next = Math.floorMod(position, carouselUrls.size());
        removeCallbacks(advance);
        showCarouselItem(requestSource, next);
    }

    private void showCarouselItem(String source, int position) {
        if (carouselUrls.isEmpty()) return;
        carouselIndex = Math.floorMod(position, carouselUrls.size());
        String url = carouselUrls.get(carouselIndex);
        // A repeated binding (for example after onStart) does not start a new
        // Glide request, so restore the gallery highlight immediately when the
        // requested carousel frame is already the one on screen.
        if (bitmap != null && sourceKey.equals(source) && imageUrl.equals(url)) notifyCarouselChanged();
        requestImage(source, url);
        scheduleCarousel();
    }

    private void notifyCarouselChanged() {
        if (carouselListener != null && carouselIndex >= 0) carouselListener.onArtworkChanged(carouselIndex);
    }

    private void requestImage(@Nullable String source, @Nullable String url) {
        animate().cancel();
        setAlpha(1f);
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
            cancelArtworkFade();
            bitmap = null;
            previousBitmap = null;
            sourceKey = "";
            imageUrl = "";
            invalidate();
        } else if (isAttachedToWindow()) {
            postDelayed(load, 350);
        }
    }

    private void startArtworkFade() {
        if (!TvMotion.motionEnabled(this)) {
            previousBitmap = null;
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
        artworkFade.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (artworkFade != animation) return;
                previousBitmap = null;
                artworkFade = null;
                artworkAlpha = 1f;
                invalidate();
            }
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

    public void clear() {
        stopCarousel();
        setImage("", "");
    }

    private void stopCarousel() {
        removeCallbacks(advance);
        carouselUrls = Collections.emptyList();
        carouselIndex = -1;
        carouselListener = null;
    }

    private void advanceCarousel() {
        if (carouselUrls.size() < 2 || !isAttachedToWindow() || !isShown()) return;
        showCarouselItem(requestSource, carouselIndex + 1);
    }

    private void scheduleCarousel() {
        removeCallbacks(advance);
        if (carouselUrls.size() > 1 && isAttachedToWindow() && isShown()) {
            postDelayed(advance, CAROUSEL_INTERVAL);
        }
    }

    private static List<String> sanitize(@Nullable List<String> urls) {
        if (urls == null || urls.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String url : urls) {
            if (!TextUtils.isEmpty(url) && !result.contains(url)) result.add(url);
        }
        return Collections.unmodifiableList(result);
    }

    /** Dissolves stale provider artwork while the next title is being resolved. */
    public void fadeOut() {
        animate().cancel();
        if (bitmap == null || !TvMotion.motionEnabled(this)) {
            clear();
            return;
        }
        animate().alpha(0f).setDuration(TvMotion.HERO_ARTWORK_FADE / 2)
                .withEndAction(() -> {
                    clear();
                    setAlpha(1f);
                }).start();
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
                    cancelArtworkFade();
                    previousBitmap = bitmap;
                    bitmap = resource;
                    sourceKey = requestSource;
                    imageUrl = requestUrl;
                    // Keep the gallery marker aligned with what is actually
                    // entering the crossfade, rather than the earlier request.
                    notifyCarouselChanged();
                    startArtworkFade();
                    scheduleCarousel();
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
                    if (bitmap == null) {
                        sourceKey = "";
                        imageUrl = "";
                    }
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
            if (bitmap == null) {
                sourceKey = "";
                imageUrl = "";
            }
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
            if (previousBitmap != null && artworkAlpha < 1f) {
                drawBitmap(canvas, previousBitmap, 1f - artworkAlpha);
            }
            drawBitmap(canvas, bitmap, artworkAlpha);
            imagePaint.setAlpha(255);
            canvas.drawColor(softScrim);
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

    private void drawBitmap(Canvas canvas, Bitmap image, float alpha) {
        float scale = Math.max((float) getWidth() / image.getWidth(), (float) getHeight() / image.getHeight());
        int width = Math.min(image.getWidth(), Math.round(getWidth() / scale));
        int height = Math.min(image.getHeight(), Math.round(getHeight() / scale));
        int left = (image.getWidth() - width) / 2;
        int top = (image.getHeight() - height) / 2;
        crop.set(left, top, left + width, top + height);
        imagePaint.setAlpha(Math.round(255 * alpha));
        canvas.drawBitmap(image, crop, bounds, imagePaint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!TextUtils.isEmpty(requestUrl)) postDelayed(load, 350);
        scheduleCarousel();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(load);
        removeCallbacks(advance);
        cancelArtworkFade();
        CustomTarget<Bitmap> old = target;
        target = null;
        if (old != null && requests != null) requests.clear(old);
        super.onDetachedFromWindow();
    }
}
