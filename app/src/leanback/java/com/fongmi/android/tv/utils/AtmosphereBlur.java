package com.fongmi.android.tv.utils;

/** Bounded, separable box blur for static low-resolution atmosphere bitmaps. */
public final class AtmosphereBlur {
    private AtmosphereBlur() {
    }

    public static void apply(int[] pixels, int width, int height, int radius) {
        if (width <= 0 || height <= 0 || (long) width * height != pixels.length || radius < 1 || radius > 32) {
            throw new IllegalArgumentException("Invalid blur dimensions or radius");
        }
        int[] scratch = new int[pixels.length];
        // Two passes soften poster lettering without processing any foreground UI.
        for (int pass = 0; pass < 2; pass++) {
            blur(pixels, scratch, width, height, radius, true);
            blur(scratch, pixels, width, height, radius, false);
        }
    }

    private static void blur(int[] input, int[] output, int width, int height, int radius, boolean horizontal) {
        int lines = horizontal ? height : width;
        int length = horizontal ? width : height;
        int stride = horizontal ? 1 : width;
        int count = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int start = horizontal ? line * width : line;
            int alpha = 0, red = 0, green = 0, blue = 0;
            for (int i = -radius; i <= radius; i++) {
                int pixel = input[start + Math.max(0, Math.min(length - 1, i)) * stride];
                alpha += pixel >>> 24;
                red += (pixel >> 16) & 255;
                green += (pixel >> 8) & 255;
                blue += pixel & 255;
            }
            for (int i = 0; i < length; i++) {
                output[start + i * stride] = (alpha / count << 24) | (red / count << 16) | (green / count << 8) | blue / count;
                int remove = input[start + Math.max(0, i - radius) * stride];
                int add = input[start + Math.min(length - 1, i + radius + 1) * stride];
                alpha += (add >>> 24) - (remove >>> 24);
                red += ((add >> 16) & 255) - ((remove >> 16) & 255);
                green += ((add >> 8) & 255) - ((remove >> 8) & 255);
                blue += (add & 255) - (remove & 255);
            }
        }
    }
}
