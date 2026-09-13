package com.fongmi.android.tv.ui.custom;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.motion.TvMotion;

/**
 * A skewed highlight band sweeping across its parent once every shimmer cycle
 * (Stitch shimmerSheen 4.5s). The parent should clip children so the band only
 * appears over the badge; the view itself is never focusable.
 */
public class ShimmerView extends View {

    private final Runnable cycle = this::sweep;
    private Animator animator;

    public ShimmerView(Context context) {
        this(context, null);
    }

    public ShimmerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setBackgroundResource(R.drawable.tv_shimmer_sheen);
    }

    private void sweep() {
        if (getWidth() == 0 || !(getParent() instanceof View)) return;
        View parent = (View) getParent();
        Interpolator interpolator = AnimationUtils.loadInterpolator(getContext(), R.interpolator.tv_interp_decelerate);
        animator = ObjectAnimator.ofFloat(this, "translationX", -getWidth(), parent.getWidth());
        animator.setDuration(TvMotion.SHIMMER * 2 / 5);
        animator.setInterpolator(interpolator);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                postDelayed(cycle, TvMotion.SHIMMER * 3 / 5);
            }
        });
        animator.start();
    }

    private void stop() {
        removeCallbacks(cycle);
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (TvMotion.motionEnabled(this)) post(cycle);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && TvMotion.motionEnabled(this)) post(cycle);
        else stop();
    }
}
