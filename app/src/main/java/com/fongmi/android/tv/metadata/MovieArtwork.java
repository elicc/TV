package com.fongmi.android.tv.metadata;

/** Display-only artwork returned by a metadata provider. */
public class MovieArtwork {
    private String url = "";
    private int width;
    private int height;
    private String kind = "photo";

    public MovieArtwork() {
    }

    public MovieArtwork(String url, int width, int height) {
        this.url = url;
        this.width = width;
        this.height = height;
    }

    public String getUrl() { return url == null ? "" : url; }
    public void setUrl(String url) { this.url = url; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public String getKind() { return kind == null ? "photo" : kind; }
    public void setKind(String kind) { this.kind = kind; }
}
