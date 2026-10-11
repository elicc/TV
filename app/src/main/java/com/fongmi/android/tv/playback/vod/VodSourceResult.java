package com.fongmi.android.tv.playback.vod;

import com.fongmi.android.tv.bean.Vod;

import java.util.List;

/** A complete search snapshot, safe to replay after the playback service reconnects. */
public record VodSourceResult(long generation, String keyword, List<Vod> items,
                              int total, int completed, int failed, int requested) {

    public enum Status {IDLE, LOADING, READY, EMPTY, ERROR, DISABLED}

    public VodSourceResult {
        items = List.copyOf(items);
    }

    public VodSourceResult(long generation, String keyword, List<Vod> items,
                           int total, int completed, int failed) {
        this(generation, keyword, items, total, completed, failed, total);
    }

    public static VodSourceResult idle() {
        return new VodSourceResult(0, "", List.of(), 0, 0, 0);
    }

    public boolean isLoading() {
        return completed < requested;
    }

    public boolean hasMore() {
        return requested < total;
    }

    public Status status() {
        if (generation == 0) return Status.IDLE;
        if (!items.isEmpty()) return Status.READY;
        if (isLoading()) return Status.LOADING;
        if (total == 0) return Status.DISABLED;
        return failed > 0 ? Status.ERROR : Status.EMPTY;
    }

    public VodSourceResult withItems(List<Vod> items) {
        return new VodSourceResult(generation, keyword, items, total, completed, failed, requested);
    }

    public static String identity(String key, String id) {
        return key + '\u0000' + id;
    }

    public static String identity(Vod item) {
        return identity(item.getSiteKey(), item.getId());
    }
}
