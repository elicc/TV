package com.fongmi.android.tv.utils;

import java.nio.file.Files;
import java.nio.file.Path;

/** Source invariants for the Leanback unclipped-parent regression; run from repository root.
 * This is not a replacement for the emulator screenshot regression. */
public class FilmAtmosphereViewContractTest {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of("app/src/leanback/java/com/fongmi/android/tv/ui/custom/FilmAtmosphereView.java"));
        String draw = source.substring(source.indexOf("protected void onDraw"), source.indexOf("protected void onAttachedToWindow"));
        int save = draw.indexOf("canvas.save()");
        int clip = draw.indexOf("canvas.clipRect(0, 0, getWidth(), getHeight())");
        int fallback = draw.indexOf("canvas.drawColor(background)");
        int missingImage = draw.indexOf("if (bitmap == null");
        int restore = draw.indexOf("canvas.restoreToCount(save)");
        check(save >= 0 && clip > save && fallback > clip, "even fallback must be clipped before drawing");
        check(missingImage > fallback && restore > missingImage && draw.contains("} finally {"), "early fallback return must restore parent canvas");
        String visibility = source.substring(source.indexOf("protected void onVisibilityChanged"), source.indexOf("public void onTrimMemory"));
        check(visibility.contains("else releaseRequest()"), "GONE or invisible ancestors must release requests");
        check(source.contains("!isShown() || TextUtils.isEmpty(imageUrl)"), "hidden ancestors must prevent delayed loading");
        System.out.println("FilmAtmosphereViewContractTest: 4 source invariants passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
