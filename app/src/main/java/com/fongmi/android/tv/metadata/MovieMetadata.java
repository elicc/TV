package com.fongmi.android.tv.metadata;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Vod;

import java.util.ArrayList;
import java.util.List;


/** Display-only metadata. Playback identity and URLs remain in Vod. */
public class MovieMetadata {
    private MetadataProvider provider = MetadataProvider.SOURCE;
    private String externalId = "";
    /** Provider-native kind such as Douban's movie/tv target_type. */
    private String externalType = "";
    private String title = "";
    private String originalTitle = "";
    private String year = "";
    private String area = "";
    private String type = "";
    private String directors = "";
    private String actors = "";
    private String summary = "";
    private String poster = "";
    /** Provider artwork intended for a 16:9 TV hero/backdrop, never for playback. */
    private String backdrop = "";
    private int backdropWidth;
    private int backdropHeight;
    private double rating;
    private int ratingCount;
    private int duration;
    /** Landscape photos suitable for a detail-page horizontal gallery. */
    private List<MovieArtwork> artworks = new ArrayList<>();

    public MovieMetadata() {
    }

    public static MovieMetadata fromSource(Vod vod) {
        MovieMetadata metadata = new MovieMetadata();
        metadata.provider = MetadataProvider.SOURCE;
        metadata.title = vod.getName();
        metadata.year = vod.getYear();
        metadata.area = vod.getArea();
        metadata.type = vod.getTypeName();
        metadata.directors = vod.getDirector();
        metadata.actors = vod.getActor();
        metadata.summary = vod.getContent();
        metadata.poster = vod.getPic();
        return metadata;
    }

    public MovieMetadata copy() {
        MovieMetadata copy = new MovieMetadata();
        copy.provider = provider;
        copy.externalId = externalId;
        copy.externalType = externalType;
        copy.title = title;
        copy.originalTitle = originalTitle;
        copy.year = year;
        copy.area = area;
        copy.type = type;
        copy.directors = directors;
        copy.actors = actors;
        copy.summary = summary;
        copy.poster = poster;
        copy.backdrop = backdrop;
        copy.backdropWidth = backdropWidth;
        copy.backdropHeight = backdropHeight;
        copy.rating = rating;
        copy.ratingCount = ratingCount;
        copy.duration = duration;
        copy.artworks = new ArrayList<>(artworks == null ? List.of() : artworks);
        return copy;
    }

    public MetadataProvider getProvider() { return provider; }
    public void setProvider(MetadataProvider provider) { this.provider = provider == null ? MetadataProvider.SOURCE : provider; }
    public String getExternalId() { return value(externalId); }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getExternalType() { return value(externalType); }
    public void setExternalType(String externalType) { this.externalType = externalType; }
    public String getTitle() { return value(title); }
    public void setTitle(String title) { this.title = title; }
    public String getOriginalTitle() { return value(originalTitle); }
    public void setOriginalTitle(String originalTitle) { this.originalTitle = originalTitle; }
    public String getYear() { return value(year); }
    public void setYear(String year) { this.year = year; }
    public String getArea() { return value(area); }
    public void setArea(String area) { this.area = area; }
    public String getType() { return value(type); }
    public void setType(String type) { this.type = type; }
    public String getDirectors() { return value(directors); }
    public void setDirectors(String directors) { this.directors = directors; }
    public String getActors() { return value(actors); }
    public void setActors(String actors) { this.actors = actors; }
    public String getSummary() { return value(summary); }
    public void setSummary(String summary) { this.summary = summary; }
    public String getPoster() { return value(poster); }
    public void setPoster(String poster) { this.poster = poster; }
    public String getBackdrop() { return value(backdrop); }
    public void setBackdrop(String backdrop) { this.backdrop = backdrop; }
    public int getBackdropWidth() { return backdropWidth; }
    public void setBackdropWidth(int backdropWidth) { this.backdropWidth = backdropWidth; }
    public int getBackdropHeight() { return backdropHeight; }
    public void setBackdropHeight(int backdropHeight) { this.backdropHeight = backdropHeight; }
    public boolean hasBackdrop() { return !getBackdrop().isEmpty(); }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public int getRatingCount() { return ratingCount; }
    public void setRatingCount(int ratingCount) { this.ratingCount = ratingCount; }
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
    public List<MovieArtwork> getArtworks() { return artworks == null ? List.of() : artworks; }
    public void setArtworks(List<MovieArtwork> artworks) { this.artworks = artworks == null ? new ArrayList<>() : new ArrayList<>(artworks); }
    public boolean hasArtworks() { return !getArtworks().isEmpty(); }

    public String getRatingText() {
        return rating > 0 ? String.format(java.util.Locale.US, "%.1f (%d)", rating, ratingCount) : "";
    }

    private static String value(String value) { return TextUtils.isEmpty(value) ? "" : value.trim(); }
}
