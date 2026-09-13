package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;

import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.TimeBar;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.TvTheme;

/**
 * DefaultTimeBar with a champagne gradient painted over the played segment
 * (Stitch: #E5A958 to #FCD58B) while the stock view keeps bars, chapters, ad
 * markers and scrubbing untouched. Remains a DefaultTimeBar so the forked
 * PlayerSeekView's constructor check still passes.
 */
public class TvTimeBar extends DefaultTimeBar {

    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF segment = new RectF();
    private final int barHeightPx;
    private final int scrubberPaddingPx;
    private long position = -1;
    private long duration = -1;
    private long scrubPosition = -1;
    private boolean scrubbing;
    private int shaderWidth;

    public TvTimeBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        barHeightPx = getResources().getDimensionPixelSize(R.dimen.tv_seek_bar_height);
        scrubberPaddingPx = getResources().getDimensionPixelSize(R.dimen.tv_seek_scrubber_size) / 2;
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
        segment.set(left, top, Math.max(right, left + 1f), top + barHeightPx);
        if (shaderWidth != (int) width) {
            shaderWidth = (int) width;
            gradientPaint.setShader(new LinearGradient(left, top, left + width, top,
                    TvTheme.color(getContext(), R.attr.tvColorAccent), TvTheme.color(getContext(), R.attr.tvColorFocus), Shader.TileMode.CLAMP));
        }
        canvas.drawRoundRect(segment, barHeightPx / 2f, barHeightPx / 2f, gradientPaint);
    }
}
