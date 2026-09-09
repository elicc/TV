package com.fongmi.android.tv.utils;

import android.content.Context;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.utils.Prefers;

/** TV-only theme preferences. Wallpaper and film imagery never change the skin. */
public final class TvTheme {

    private static final String KEY_SKIN = "tv_skin";
    private static final String KEY_ATMOSPHERE = "tv_film_atmosphere";
    // Persisted IDs: append new skins, never reorder or reuse an existing ID.
    private static final int[] THEMES = {R.style.Theme_Tv_Champagne, R.style.Theme_Tv_Ice, R.style.Theme_Tv_Jade};
    private static final int[] LABELS = {R.string.tv_skin_champagne, R.string.tv_skin_ice, R.string.tv_skin_jade};

    private TvTheme() {
    }

    public static int getSkin() {
        return validSkin(Prefers.getInt(KEY_SKIN, 0));
    }

    public static void setSkin(int skin) {
        Prefers.put(KEY_SKIN, validSkin(skin));
    }

    private static int validSkin(int skin) {
        return skin >= 0 && skin < THEMES.length ? skin : 0;
    }

    public static int getSkinCount() {
        return THEMES.length;
    }

    public static String[] getSkinLabels(Context context) {
        String[] labels = new String[LABELS.length];
        for (int i = 0; i < labels.length; i++) labels[i] = context.getString(LABELS[i]);
        return labels;
    }

    @StyleRes
    public static int getThemeRes() {
        return THEMES[getSkin()];
    }

    public static boolean isAtmosphereEnabled() {
        // Preserve an existing custom image/GIF/video until the user opts in.
        boolean builtInWallpaper = Setting.getWall() > 0 && Setting.getWallType() == 0;
        return Prefers.getBoolean(KEY_ATMOSPHERE, builtInWallpaper);
    }

    public static void setAtmosphereEnabled(boolean enabled) {
        Prefers.put(KEY_ATMOSPHERE, enabled);
    }

    @ColorInt
    public static int color(Context context, @AttrRes int attr) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, value, true)) {
            throw new IllegalArgumentException("Missing TV theme attribute: " + attr);
        }
        return value.resourceId != 0 ? ContextCompat.getColor(context, value.resourceId) : value.data;
    }
}
