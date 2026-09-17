package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.motion.TvMotion;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.TvTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * Remote-hint footer: keycap chips light up in the skin accent for ~320ms when
 * the matching key is pressed (Stitch key-pressed-active feedback). The bar is
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

    public TvKeycapsBar(Context context) {
        this(context, null);
    }

    public TvKeycapsBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        // Press feedback scales keycaps outside their resting bounds. In particular,
        // the first cap grows into the start padding and must not lose its left edge.
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Rebuild the bar from the given keycaps and their action hints. */
    public void setCaps(Cap[] caps, String[] hints) {
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
        if (event.getAction() != KeyEvent.ACTION_DOWN) return;
        for (int i = 0; i < caps.size(); i++) if (matches(caps.get(i), event.getKeyCode())) flash(keycaps.get(i));
    }

    private boolean matches(Cap cap, int keyCode) {
        for (int code : cap.codes) if (code == keyCode) return true;
        return false;
    }

    private void flash(TextView keycap) {
        if (flashing != null) flashing.removeCallbacks(pendingRevert);
        flashing = keycap;
        keycap.setTextColor(TvTheme.color(getContext(), R.attr.tvColorOnAccent));
        keycap.setBackgroundResource(R.drawable.tv_keycap_active);
        keycap.animate().scaleX(1.15f).scaleY(1.15f).setDuration(TvMotion.PRESS).start();
        keycap.postDelayed(pendingRevert, TvMotion.KEYCAP);
    }

    private final Runnable pendingRevert = () -> {
        if (flashing == null) return;
        flashing.setBackgroundResource(R.drawable.tv_keycap);
        flashing.setTextColor(ContextCompat.getColor(getContext(), R.color.tv_text_secondary));
        flashing.animate().scaleX(1f).scaleY(1f).setDuration(TvMotion.PRESS).start();
        flashing = null;
    };

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
