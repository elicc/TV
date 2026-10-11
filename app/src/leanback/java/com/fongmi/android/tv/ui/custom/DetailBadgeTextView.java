package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.Layout;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textview.MaterialTextView;

/** Centers visible glyphs, rather than the font line box, in compact detail badges. */
public class DetailBadgeTextView extends MaterialTextView {

    private final Rect textBounds = new Rect();

    public DetailBadgeTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        Layout layout = getLayout();
        if (layout == null || layout.getLineCount() != 1) {
            super.onDraw(canvas);
            return;
        }
        // Layout's text includes ellipsizing; hidden suffixes must not affect centering.
        String text = layout.getText().toString();
        getPaint().getTextBounds(text, 0, text.length(), textBounds);
        if (textBounds.isEmpty()) {
            super.onDraw(canvas);
            return;
        }
        float center = getScrollY() + (getHeight() + getCompoundPaddingTop() - getCompoundPaddingBottom()) / 2f;
        float textCenter = getBaseline() + textBounds.exactCenterY();
        int save = canvas.save();
        // The background has already been drawn by View.draw(); only text moves.
        canvas.translate(0, Math.round(center - textCenter));
        super.onDraw(canvas);
        canvas.restoreToCount(save);
    }
}
