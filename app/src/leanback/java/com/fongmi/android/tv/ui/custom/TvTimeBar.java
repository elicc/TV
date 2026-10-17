package com.fongmi.android.tv.ui.custom;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;

import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.TimeBar;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.motion.TvMotion;
import com.fongmi.android.tv.utils.TvTheme;

/**
 * The stock bar retains seeking, chapters and accessibility. A quiet played
 * tint becomes a brighter gradient, the buffered range remains visible, and a
 * small pulsing thumb appears on D-pad focus. Without a duration the empty
 * track still draws, as a disabled bar. On focus the played segment also
 * thickens and soft crests flow from its start into the playhead, so the bar
 * reads as focused at a glance. Remains a DefaultTimeBar for PlayerSeekView's
 * constructor check.
 */
public class TvTimeBar extends DefaultTimeBar {

    /** One crest every WAVE_LENGTH_DP, each taking WAVE_PERIOD ms to travel that far. */
    private static final float WAVE_LENGTH_DP = 160f;
    private static final long WAVE_PERIOD = 1100;
    private static final float FOCUS_THICKNESS = 0.5f;

    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bufferedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix waveMatrix = new Matrix();
    private final float waveLength;
    private final RectF segment = new RectF();
    private final RectF track = new RectF();
    private final int barHeightPx;
    private final int scrubberPaddingPx;
    private long position = -1;
    private long duration = -1;
    private long bufferedPosition = -1;
    private long scrubPosition = -1;
    private boolean scrubbing;
    private int shaderWidth;
    private float focusAmount;
    private float pulseAmount;
    private ValueAnimator focusAnimator;
    private ValueAnimator pulseAnimator;
    private long waveStart;

    public TvTimeBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        barHeightPx = getResources().getDimensionPixelSize(R.dimen.tv_seek_bar_height);
        scrubberPaddingPx = getResources().getDimensionPixelSize(R.dimen.tv_seek_scrubber_size) / 2;
        waveLength = WAVE_LENGTH_DP * getResources().getDisplayMetrics().density;
        // Each repeat is a crest with a long soft back and a short bright front,
        // so the repeats read as swells pushing toward the playhead.
        wavePaint.setShader(new LinearGradient(0f, 0f, waveLength, 0f,
                new int[]{Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(150, 255, 255, 255), Color.TRANSPARENT},
                new float[]{0f, 0.35f, 0.88f, 1f}, Shader.TileMode.REPEAT));
        // DefaultTimeBar draws its own played segment before TvTimeBar draws the
        // themed gradient. Two independently rounded segments use slightly
        // different endpoints and leave a visible seam at the playhead. Keep
        // the stock track transparent and let this class paint one continuous
        // track/buffered/played pair instead.
        setPlayedColor(Color.TRANSPARENT);
        setBufferedColor(Color.TRANSPARENT);
        setUnplayedColor(Color.TRANSPARENT);
        addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(TimeBar timeBar, long position) {
                scrubbing = true;
                scrubPosition = position;
            }

            @Override
            public void onScrubMove(TimeBar timeBar, long position) {
                scrubPosition = position;
            }

            @Override
            public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                scrubbing = false;
                scrubPosition = -1;
            }
        });
    }

    @Override
    public void setPosition(long position) {
        super.setPosition(position);
        this.position = position;
    }

    @Override
    public void setDuration(long duration) {
        super.setDuration(duration);
        this.duration = duration;
        if (duration <= 0) this.bufferedPosition = -1;
    }

    @Override
    public void setBufferedPosition(long bufferedPosition) {
        super.setBufferedPosition(bufferedPosition);
        this.bufferedPosition = bufferedPosition;
        invalidate();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focusAnimator != null) focusAnimator.cancel();
        if (pulseAnimator != null) pulseAnimator.cancel();
        pulseAmount = 0f;
        if (TvMotion.motionEnabled(this)) {
            focusAnimator = ValueAnimator.ofFloat(focusAmount, focused ? 1f : 0f);
            focusAnimator.setDuration(focused ? TvMotion.FOCUS_ON : TvMotion.FOCUS_OFF);
            focusAnimator.addUpdateListener(animation -> {
                focusAmount = (float) animation.getAnimatedValue();
                invalidate();
            });
            focusAnimator.start();
            if (focused) {
                waveStart = SystemClock.uptimeMillis();
                pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
                pulseAnimator.setDuration(1200);
                pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
                pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
                pulseAnimator.addUpdateListener(animation -> {
                    pulseAmount = (float) animation.getAnimatedValue();
                    invalidate();
                });
                pulseAnimator.start();
            }
        } else {
            focusAmount = focused ? 1f : 0f;
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (focusAnimator != null) focusAnimator.cancel();
        if (pulseAnimator != null) pulseAnimator.cancel();
        super.onDetachedFromWindow();
    }

    /** The forked DefaultTimeBar finalizes onDraw, so paint after the stock pass. */
    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        long current = scrubbing && scrubPosition >= 0 ? scrubPosition : position;
        float left = scrubberPaddingPx;
        float width = getWidth() - 2f * scrubberPaddingPx;
        float barHeight = barHeightPx * (1f + FOCUS_THICKNESS * focusAmount);
        float radius = barHeight / 2f;
        float top = (getHeight() - barHeight) / 2f;
        track.set(left, top, left + width, top + barHeight);
        trackPaint.setColor(Color.argb(38, 255, 255, 255));
        canvas.drawRoundRect(track, radius, radius, trackPaint);
        // Until the media reports a duration (loading, live) only the idle track
        // shows, so the console never has a gap where the bar belongs.
        if (duration <= 0 || current < 0) return;
        float right = left + Math.min(current / (float) duration, 1f) * width;
        if (bufferedPosition > 0) {
            float bufferedRight = left + Math.min(bufferedPosition / (float) duration, 1f) * width;
            bufferedPaint.setColor(Color.argb(76, 255, 255, 255));
            track.set(left, top, Math.max(bufferedRight, left + 1f), top + barHeight);
            canvas.drawRoundRect(track, radius, radius, bufferedPaint);
        }
        segment.set(left, top, Math.max(right, left + 1f), top + barHeight);
        if (shaderWidth != (int) width) {
            shaderWidth = (int) width;
            gradientPaint.setShader(new LinearGradient(left, top, left + width, top,
                    TvTheme.color(getContext(), R.attr.tvColorAccent), TvTheme.color(getContext(), R.attr.tvColorFocus), Shader.TileMode.CLAMP));
        }
        gradientPaint.setAlpha(Math.round(96 + 159 * focusAmount));
        canvas.drawRoundRect(segment, radius, radius, gradientPaint);
        drawWaves(canvas, radius);
        if (!isEnabled()) return;
        float centerY = getHeight() / 2f;
        int focusColor = TvTheme.color(getContext(), R.attr.tvColorFocus);
        float density = getResources().getDisplayMetrics().density;
        if (focusAmount > 0f) {
            thumbPaint.setColor(focusColor);
            thumbPaint.setAlpha(Math.round((35 + 20 * pulseAmount) * focusAmount));
            canvas.drawCircle(right, centerY, (7f + pulseAmount) * density, thumbPaint);
        }
        thumbPaint.setColor(focusColor);
        thumbPaint.setAlpha(Math.round(170 + 85 * focusAmount));
        canvas.drawCircle(right, centerY, (4f + 2f * focusAmount + pulseAmount * 0.3f) * density, thumbPaint);
    }

    /** Crests travel at a steady speed and are clipped to the played segment. */
    private void drawWaves(Canvas canvas, float radius) {
        if (focusAmount <= 0f || !TvMotion.motionEnabled(this)) return;
        long elapsed = (SystemClock.uptimeMillis() - waveStart) % WAVE_PERIOD;
        waveMatrix.setTranslate(segment.left + waveLength * elapsed / WAVE_PERIOD, 0f);
        wavePaint.getShader().setLocalMatrix(waveMatrix);
        wavePaint.setAlpha(Math.round(255 * focusAmount));
        canvas.drawRoundRect(segment, radius, radius, wavePaint);
        postInvalidateOnAnimation();
    }
}
