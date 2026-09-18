package com.fongmi.android.tv.metadata;

public enum MetadataProvider {
    SOURCE("Source"),
    DOUBAN("Douban"),
    TMDB("TMDB");

    private final String label;

    MetadataProvider(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
