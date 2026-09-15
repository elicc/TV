package com.fongmi.android.tv.ui.custom;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.motion.TvMotion;

/**
 * Container for skeleton placeholder blocks. Breathes its opacity while visible
 * so unfetched metadata reads as loading instead of missing. Children are plain
 * tinted blocks declared by the enclosing layout; this view only drives the pulse
 * and is never focusable.
 */
public class SkeletonLayout extends LinearLayoutCompat {

    private static final long PULSE = 900L;
    private static final float DIM_ALPHA = 0.35f;

    private ObjectAnimator animator;

    public SkeletonLayout(Context context) {
        this(context, null);
    }

    public SkeletonLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private void pulse() {
        stop();
        if (!isShown() || !TvMotion.motionEnabled(this)) return;
        animator = ObjectAnimator.ofFloat(this, View.ALPHA, getAlpha(), DIM_ALPHA);
        animator.setDuration(PULSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(AnimationUtils.loadInterpolator(getContext(), R.interpolator.tv_interp_decelerate));
        animator.start();
    }

    private void stop() {
        if (animator == null) return;
        animator.cancel();
        animator = null;
        setAlpha(1f);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        pulse();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) pulse();
        else stop();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) pulse();
        else stop();
    }
}
