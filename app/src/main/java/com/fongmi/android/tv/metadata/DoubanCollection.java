package com.fongmi.android.tv.metadata;

/** Public, seed-free Douban collections. UI labels stay in the TV flavor. */
public enum DoubanCollection {
    HOT_MOVIES("movie_hot_gaia"), HOT_TV("tv_hot"), SHOWING("movie_showing");

    private final String id;

    DoubanCollection(String id) { this.id = id; }
    public String getId() { return id; }
}
