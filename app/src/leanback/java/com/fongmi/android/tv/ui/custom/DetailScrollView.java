package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.widget.NestedScrollView;

/** Owns vertical focus reveal for the detail body's stacked, horizontal choice rows. */
public class DetailScrollView extends NestedScrollView {

    private final Rect focusBounds = new Rect();

    public DetailScrollView(Context context) {
        this(context, null);
    }

    public DetailScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DetailScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (!isLayoutRequested()) revealFocusedRow(focused);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        View focused = findFocus();
        // RecyclerView can request another reveal after laying out its selected child.
        // Keep one synchronous vertical owner instead of racing smooth-scroll animations.
        if (focused != null && focused != this) return revealFocusedRow(focused);
        return super.requestChildRectangleOnScreen(child, rectangle, immediate);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // Optional rows arrive asynchronously; use final measured bounds, not a delayed post.
        revealFocusedRow(findFocus());
    }

    private boolean revealFocusedRow(View focused) {
        int available = getHeight() - getPaddingTop() - getPaddingBottom();
        if (focused == null || focused == this || !focused.isShown()
                || getChildCount() == 0 || available <= 0 || focused.getHeight() == 0) return false;
        View content = getChildAt(0);
        View target = focused;
        View ancestor = focused;
        while (ancestor != content) {
            if (ancestor.getHeight() <= available) target = ancestor;
            if (!(ancestor.getParent() instanceof View)) return false;
            ancestor = (View) ancestor.getParent();
        }
        // Prefer the complete label + choices section. If a recommendation section is
        // taller than the viewport, reveal its card row instead of clipping the cards
        // merely to keep the heading on screen. Never resize the cards to force a fit.
        target.getDrawingRect(focusBounds);
        offsetDescendantRectToMyCoords(target, focusBounds);
        int before = getScrollY();
        scrollBy(0, computeScrollDeltaToGetChildRectOnScreen(focusBounds));
        return before != getScrollY();
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        // AndroidX's default calculation includes the padding in its visible viewport,
        // even with clipToPadding=true. That leaves the first row hidden behind padding
        // when navigating UP. Use the same viewport for measurement and clipping.
        int top = getScrollY() + getPaddingTop();
        int bottom = getScrollY() + getHeight() - getPaddingBottom();
        if (bottom <= top) return 0;
        if (rect.height() <= bottom - top) {
            if (rect.top < top) return rect.top - top;
            if (rect.bottom > bottom) return rect.bottom - bottom;
        } else {
            // Oversized content cannot fit: reveal the nearest edge without oscillating.
            if (rect.top > top) return rect.top - top;
            if (rect.bottom < bottom) return rect.bottom - bottom;
        }
        return 0;
    }
}
