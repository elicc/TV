package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;

import androidx.leanback.widget.HorizontalGridView;

/** The source rail starts at its first card, rather than restoring its status footer. */
public class SourceGridView extends HorizontalGridView {

    private boolean resetAfterLayout;

    public SourceGridView(Context context) {
        this(context, null);
    }

    public SourceGridView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SourceGridView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void resetToStartAfterLayout() {
        resetAfterLayout = true;
        requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // Leanback applies insertion offsets during layout. Resetting before it
        // finishes lets the old index-zero footer move the selection to the end.
        if (resetAfterLayout && getAdapter() != null && getAdapter().getItemCount() > 0) {
            resetAfterLayout = false;
            setSelectedPosition(0);
        }
    }

    @Override
    public boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        // Internal LEFT/RIGHT navigation and async updates retain their position.
        // Only entry from another row (or focus on the grid itself) starts over.
        if (!hasFocus() || isFocused()) setSelectedPosition(0);
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }
}
