package com.fongmi.android.tv.player.exo;

import android.net.Uri;

import androidx.media3.common.MediaItem;

/** Plugin-owned proxies have their own unbounded fan-out; do not add speculative requests to them. */
final class ExoPreloadPolicy {

    private ExoPreloadPolicy() {}

    static boolean isProxy(MediaItem item) {
        return item != null && item.localConfiguration != null && isProxy(item.localConfiguration.uri);
    }

    static boolean isProxy(Uri uri) {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        String host = uri.getHost();
        String path = uri.getPath();
        return "localhost".equalsIgnoreCase(host) || "::1".equals(host) || "[::1]".equals(host)
                || "0.0.0.0".equals(host) || host != null && host.matches("127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")
                || "/proxy".equals(path) || path != null && path.startsWith("/proxy/");
    }
}
