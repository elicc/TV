package com.fongmi.android.tv.utils;

import java.util.Arrays;

/** Dependency-free regression checks: compile alongside AtmosphereBlur and run with java. */
public class AtmosphereBlurTest {
    public static void main(String[] args) {
        int[] uniform = new int[320 * 180];
        Arrays.fill(uniform, 0xff235a9c);
        AtmosphereBlur.apply(uniform, 320, 180, 12);
        for (int pixel : uniform) check(pixel == 0xff235a9c, "constant color must remain constant");

        int[] single = {0xff345678};
        AtmosphereBlur.apply(single, 1, 1, 12);
        check(single[0] == 0xff345678, "large radius must clamp at edges");

        int[] edges = {0xff000000, 0xffffffff, 0xff000000};
        AtmosphereBlur.apply(edges, 3, 1, 1);
        check(edges[0] == edges[2], "blur must be symmetric");
        check((edges[1] & 255) > 0 && (edges[1] & 255) < 255, "impulse must spread");
        for (int pixel : edges) check((pixel >>> 24) == 255, "opaque alpha must remain opaque");

        int[] vertical = {0xff000000, 0xffffffff, 0xff000000};
        AtmosphereBlur.apply(vertical, 1, 3, 1);
        check(Arrays.equals(edges, vertical), "horizontal and vertical kernels must agree");

        boolean rejected = false;
        try {
            AtmosphereBlur.apply(new int[1], 2, 1, 12);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "invalid dimensions must fail before indexing");
        int[] sample = new int[7 * 5];
        java.util.Random random = new java.util.Random(42);
        for (int i = 0; i < sample.length; i++) sample[i] = random.nextInt();
        int[] expected = sample.clone();
        for (int pass = 0; pass < 2; pass++) {
            expected = reference(expected, 7, 5, 12, true);
            expected = reference(expected, 7, 5, 12, false);
        }
        AtmosphereBlur.apply(sample, 7, 5, 12);
        check(Arrays.equals(sample, expected), "sliding kernel must match naive clamped reference");
        System.out.println("AtmosphereBlurTest: 7 checks passed");
    }

    private static int[] reference(int[] input, int width, int height, int radius, boolean horizontal) {
        int[] result = new int[input.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = 0;
                for (int channel = 0; channel < 4; channel++) {
                    int sum = 0;
                    for (int d = -radius; d <= radius; d++) {
                        int px = horizontal ? Math.max(0, Math.min(width - 1, x + d)) : x;
                        int py = horizontal ? y : Math.max(0, Math.min(height - 1, y + d));
                        sum += (input[py * width + px] >>> (channel * 8)) & 255;
                    }
                    pixel |= (sum / (radius * 2 + 1)) << (channel * 8);
                }
                result[y * width + x] = pixel;
            }
        }
        return result;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
