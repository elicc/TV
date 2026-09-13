package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.motion.TvMotion;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Two slowly drifting radial glows behind the page content (Stitch ambientBreath 11s /
 * ambientDrift 14s). The gradients are static drawables; only render properties
 * (alpha, scale, translation) animate, so weak TV GPUs never re-rasterize.
 */
public class AmbientGlowView extends FrameLayout {

    private final List<android.animation.ObjectAnimator> animators = new ArrayList<>();
    private boolean running;

    public AmbientGlowView(Context context) {
        this(context, null);
    }

    public AmbientGlowView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutInflater.from(context).inflate(R.layout.view_ambient_glow, this, true);
        createAnimations();
    }

    private void createAnimations() {
        View breath = findViewById(R.id.glowBreath);
        View drift = findViewById(R.id.glowDrift);
        Interpolator ease = AnimationUtils.loadInterpolator(getContext(), R.interpolator.tv_interp_enter);
        // Leading accent glow breathes in place with a gentle south-west drift.
        animators.add(loop(breath, "alpha", 0.72f, 0.92f, TvMotion.BREATH, ease));
        animators.add(loop(breath, "scaleX", 1f, 1.06f, TvMotion.BREATH, ease));
        animators.add(loop(breath, "scaleY", 1f, 1.06f, TvMotion.BREATH, ease));
        animators.add(loop(breath, "translationX", 0f, -ResUtil.dp2px(15), TvMotion.BREATH, ease));
        animators.add(loop(breath, "translationY", 0f, ResUtil.dp2px(12), TvMotion.BREATH, ease));
        // Trailing umber glow drifts wider and dimmer for depth contrast.
        animators.add(loop(drift, "alpha", 0.18f, 0.32f, TvMotion.DRIFT, ease));
        animators.add(loop(drift, "scaleX", 1f, 1.12f, TvMotion.DRIFT, ease));
        animators.add(loop(drift, "scaleY", 1f, 1.12f, TvMotion.DRIFT, ease));
        animators.add(loop(drift, "translationX", 0f, ResUtil.dp2px(25), TvMotion.DRIFT, ease));
        animators.add(loop(drift, "translationY", 0f, -ResUtil.dp2px(20), TvMotion.DRIFT, ease));
    }

    private android.animation.ObjectAnimator loop(View target, String property, float from, float to, long duration, Interpolator interpolator) {
        android.animation.ObjectAnimator animator = android.animation.ObjectAnimator.ofFloat(target, property, from, to);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);
        animator.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        animator.setRepeatMode(android.animation.ObjectAnimator.REVERSE);
        return animator;
    }

    private void startLoops() {
        if (running || !TvMotion.motionEnabled(this)) return;
        running = true;
        for (android.animation.ObjectAnimator animator : animators) animator.start();
    }

    private void stopLoops() {
        running = false;
        for (android.animation.ObjectAnimator animator : animators) animator.cancel();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startLoops();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopLoops();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) startLoops();
        else stopLoops();
    }
}
