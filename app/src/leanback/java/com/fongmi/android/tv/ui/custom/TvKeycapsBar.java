package com.fongmi.android.tv.ui.custom;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.motion.TvMotion;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.TvTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * Remote-hint footer: a brief neutral text/border highlight acknowledges a key
 * without competing with the actual focus target. The bar is
 * never focusable and never consumes events; activities forward key downs via
 * {@code BaseActivity.dispatchKeyEvent}.
 */
public class TvKeycapsBar extends LinearLayoutCompat {

    /** A keycap glyph plus the key codes that light it up. */
    public static final class Cap {

        final String label;
        final int[] codes;

        public static Cap of(String label, int... codes) {
            return new Cap(label, codes);
        }

        private Cap(String label, int[] codes) {
            this.label = label;
            this.codes = codes;
        }
    }

    private final List<TextView> keycaps = new ArrayList<>();
    private final List<Cap> caps = new ArrayList<>();
    private TextView flashing;
    private ValueAnimator feedbackAnimator;
    private final Runnable pendingRevert = this::clearFeedback;

    public TvKeycapsBar(Context context) {
        this(context, null);
    }

    public TvKeycapsBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Rebuild the bar from the given keycaps and their action hints. */
    public void setCaps(Cap[] caps, String[] hints) {
        clearFeedback();
        this.caps.clear();
        keycaps.clear();
        removeAllViews();
        for (int i = 0; i < caps.length; i++) {
            if (i > 0) addView(spacer());
            this.caps.add(caps[i]);
            keycaps.add(addKeycap(caps[i].label));
            addHint(hints[i]);
        }
    }

    public void onKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) return;
        for (int i = 0; i < caps.size(); i++) if (matches(caps.get(i), event.getKeyCode())) flash(keycaps.get(i));
    }

    private boolean matches(Cap cap, int keyCode) {
        for (int code : cap.codes) if (code == keyCode) return true;
        return false;
    }

    private void flash(TextView keycap) {
        // Repeated navigation within this pulse must not restart or extend it.
        if (flashing == keycap) return;
        clearFeedback();
        flashing = keycap;
        GradientDrawable background = (GradientDrawable) keycap.getBackground().mutate();
        int text = ContextCompat.getColor(getContext(), R.color.tv_text_secondary);
        int activeText = ContextCompat.getColor(getContext(), R.color.tv_keycap_text_pressed);
        int border = TvTheme.color(getContext(), R.attr.tvColorGlassBorder);
        int activeBorder = ContextCompat.getColor(getContext(), R.color.tv_keycap_border_pressed);
        if (!TvMotion.motionEnabled(this)) {
            keycap.setTextColor(activeText);
            background.setStroke(ResUtil.dp2px(1), activeBorder);
            postDelayed(pendingRevert, TvMotion.KEYCAP);
            return;
        }
        // 60ms in, 80ms hold, 120ms out; the glass fill and geometry never change.
        feedbackAnimator = ValueAnimator.ofFloat(0f, TvMotion.KEYCAP);
        feedbackAnimator.setDuration(TvMotion.KEYCAP);
        feedbackAnimator.setInterpolator(new LinearInterpolator());
        feedbackAnimator.addUpdateListener(animation -> {
            float time = (float) animation.getAnimatedValue();
            float amount = Math.max(0f, Math.min(1f, Math.min(time / 60f, (TvMotion.KEYCAP - time) / 120f)));
            keycap.setTextColor(ColorUtils.blendARGB(text, activeText, amount));
            background.setStroke(ResUtil.dp2px(1), ColorUtils.blendARGB(border, activeBorder, amount));
        });
        feedbackAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                clearFeedback();
            }
        });
        feedbackAnimator.start();
    }

    private void clearFeedback() {
        removeCallbacks(pendingRevert);
        if (feedbackAnimator != null) {
            feedbackAnimator.removeAllListeners();
            feedbackAnimator.removeAllUpdateListeners();
            feedbackAnimator.cancel();
            feedbackAnimator = null;
        }
        if (flashing == null) return;
        flashing.setBackgroundResource(R.drawable.tv_keycap);
        flashing.setTextColor(ContextCompat.getColor(getContext(), R.color.tv_text_secondary));
        flashing = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        clearFeedback();
        super.onDetachedFromWindow();
    }

    private TextView addKeycap(String label) {
        TextView view = new TextView(getContext(), null, 0);
        view.setText(label);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(12);
        view.setGravity(Gravity.CENTER);
        view.setPadding(ResUtil.dp2px(8), 0, ResUtil.dp2px(8), 0);
        view.setMinimumWidth(ResUtil.dp2px(28));
        view.setBackgroundResource(R.drawable.tv_keycap);
        view.setTextColor(ContextCompat.getColor(getContext(), R.color.tv_text_secondary));
        view.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, ResUtil.dp2px(24)));
        addView(view);
        return view;
    }

    private void addHint(String hint) {
        TextView view = new TextView(getContext(), null, 0);
        view.setText(hint);
        view.setTextSize(12);
        view.setTextColor(ContextCompat.getColor(getContext(), R.color.tv_text_secondary));
        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.leftMargin = ResUtil.dp2px(8);
        view.setLayoutParams(params);
        addView(view);
    }

    private View spacer() {
        View view = new View(getContext());
        view.setLayoutParams(new LayoutParams(ResUtil.dp2px(36), 1));
        return view;
    }
}
