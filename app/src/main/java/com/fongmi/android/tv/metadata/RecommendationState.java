package com.fongmi.android.tv.metadata;

import java.util.List;

/** Optional related-title rail state, independent of playback and selected info tab. */
public final class RecommendationState {
    public enum Status { IDLE, LOADING, READY, EMPTY, ERROR }
    private final Status status;
    private final List<MovieMetadata> items;

    private RecommendationState(Status status, List<MovieMetadata> items) {
        this.status = status;
        this.items = List.copyOf(items);
    }

    public static RecommendationState idle() { return new RecommendationState(Status.IDLE, List.of()); }
    public static RecommendationState loading() { return new RecommendationState(Status.LOADING, List.of()); }
    public static RecommendationState error() { return new RecommendationState(Status.ERROR, List.of()); }
    public static RecommendationState success(List<MovieMetadata> items) {
        return new RecommendationState(items.isEmpty() ? Status.EMPTY : Status.READY, items);
    }
    public Status getStatus() { return status; }
    public List<MovieMetadata> getItems() { return items; }

    /** Candidates are not trusted identities, even when selected for preview. */
    public static MovieMetadata resolvedMovie(MetadataState state) {
        if (state == null || state.getStatus() != MetadataState.Status.SUCCESS) return null;
        MovieMetadata movie = state.getProviders().get(MetadataProvider.DOUBAN);
        if (movie == null || movie.getProvider() != MetadataProvider.DOUBAN || !movie.getExternalId().matches("[0-9]+")) return null;
        return "movie".equals(movie.getExternalType()) || "tv".equals(movie.getExternalType()) ? movie : null;
    }
}
