package com.fongmi.android.tv.ui.custom;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;

import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.TimeBar;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.motion.TvMotion;
import com.fongmi.android.tv.utils.TvTheme;

/**
 * The stock bar retains seeking, chapters and accessibility. A quiet played
 * tint becomes a brighter gradient, the buffered range remains visible, and a
 * small pulsing thumb appears on D-pad focus. Remains a DefaultTimeBar for
 * PlayerSeekView's constructor check.
 */
public class TvTimeBar extends DefaultTimeBar {

    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bufferedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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

    public TvTimeBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        barHeightPx = getResources().getDimensionPixelSize(R.dimen.tv_seek_bar_height);
        scrubberPaddingPx = getResources().getDimensionPixelSize(R.dimen.tv_seek_scrubber_size) / 2;
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
        if (duration <= 0 || current < 0) return;
        float left = scrubberPaddingPx;
        float width = getWidth() - 2f * scrubberPaddingPx;
        float right = left + Math.min(current / (float) duration, 1f) * width;
        float top = (getHeight() - barHeightPx) / 2f;
        track.set(left, top, left + width, top + barHeightPx);
        trackPaint.setColor(Color.argb(38, 255, 255, 255));
        canvas.drawRoundRect(track, barHeightPx / 2f, barHeightPx / 2f, trackPaint);
        if (bufferedPosition > 0) {
            float bufferedRight = left + Math.min(bufferedPosition / (float) duration, 1f) * width;
            bufferedPaint.setColor(Color.argb(76, 255, 255, 255));
            track.set(left, top, Math.max(bufferedRight, left + 1f), top + barHeightPx);
            canvas.drawRoundRect(track, barHeightPx / 2f, barHeightPx / 2f, bufferedPaint);
        }
        segment.set(left, top, Math.max(right, left + 1f), top + barHeightPx);
        if (shaderWidth != (int) width) {
            shaderWidth = (int) width;
            gradientPaint.setShader(new LinearGradient(left, top, left + width, top,
                    TvTheme.color(getContext(), R.attr.tvColorAccent), TvTheme.color(getContext(), R.attr.tvColorFocus), Shader.TileMode.CLAMP));
        }
        gradientPaint.setAlpha(Math.round(96 + 159 * focusAmount));
        canvas.drawRoundRect(segment, barHeightPx / 2f, barHeightPx / 2f, gradientPaint);
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
}
