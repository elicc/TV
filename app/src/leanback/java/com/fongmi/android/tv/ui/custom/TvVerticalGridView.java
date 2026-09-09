package com.fongmi.android.tv.ui.custom;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.VerticalGridView;

/** Keeps pointer browsing independent from Leanback's D-pad selection alignment. */
@SuppressLint("RestrictedApi")
public class TvVerticalGridView extends VerticalGridView {

    private boolean pointerMode;
    private boolean previousTouchFocusable;
    private boolean previousFocusSearchDisabled;
    private int previousScrollStrategy;

    public TvVerticalGridView(@NonNull Context context) {
        this(context, null);
    }

    public TvVerticalGridView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TvVerticalGridView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private void beginPointerBrowsing() {
        if (pointerMode) return;
        pointerMode = true;
        previousScrollStrategy = getFocusScrollStrategy();
        previousFocusSearchDisabled = isFocusSearchDisabled();
        previousTouchFocusable = isFocusableInTouchMode();
        // Leanback 1.0.0 re-aligns mFocusPosition during layout in ALIGNED mode,
        // even when a drag has moved that old selection off screen.
        setFocusScrollStrategy(FOCUS_SCROLL_ITEM);
        setFocusSearchDisabled(true);
        setFocusableInTouchMode(true);
        requestFocus();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) beginPointerBrowsing();
        // Do not reset on UP: fling and asynchronous image layouts happen later.
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_SCROLL
                && event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) beginPointerBrowsing();
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        boolean navigation = key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
                || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (pointerMode && navigation && event.getAction() == KeyEvent.ACTION_DOWN) {
            stopScroll();
            setFocusSearchDisabled(previousFocusSearchDisabled);
            View target = null;
            int bestVisibleHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != VISIBLE || !child.hasFocusable()) continue;
                int visibleHeight = Math.min(child.getBottom(), getHeight() - getPaddingBottom())
                        - Math.max(child.getTop(), getPaddingTop());
                if (visibleHeight > bestVisibleHeight) {
                    target = child;
                    bestVisibleHeight = visibleHeight;
                }
            }
            // Update selection while still using ITEM, before re-enabling keyline
            // alignment. First key establishes a visible focus, never opens a film.
            boolean focused = target != null && target.requestFocus();
            setFocusScrollStrategy(previousScrollStrategy);
            setFocusableInTouchMode(previousTouchFocusable);
            pointerMode = false;
            if (focused) return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
