package com.fongmi.android.tv.metadata;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.MetadataAgentSetting;
import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Optional remote metadata resolver. Every failure is expected to fall back locally. */
public class MetadataAgentClient {

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(20);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public boolean isConfigured() {
        return MetadataAgentSetting.isConfigured();
    }

    public Resolution resolve(MovieIdentity identity, boolean allowAgent) throws IOException {
        Payload payload = new Payload(identity, allowAgent);
        String json = execute(new Request.Builder()
                .url(MetadataAgentSetting.getUrl() + "/api/v1/metadata/resolve")
                .headers(okhttp3.Headers.of(headers()))
                .post(RequestBody.create(App.gson().toJson(payload), JSON))
                .build());
        ResponsePayload response = App.gson().fromJson(json, ResponsePayload.class);
        if (response == null) throw new IOException("Empty metadata agent response");
        return response.toResolution();
    }

    public MovieMetadata detail(String externalId, boolean tv) throws IOException {
        String url = MetadataAgentSetting.getUrl() + "/api/v1/metadata/douban/" + externalId
                + "?external_type=" + (tv ? "tv" : "movie");
        String json = execute(new Request.Builder().url(url).headers(okhttp3.Headers.of(headers())).build());
        MetadataPayload response = App.gson().fromJson(json, MetadataPayload.class);
        if (response == null) throw new IOException("Empty metadata agent detail response");
        return response.toMetadata();
    }

    private String execute(Request request) throws IOException {
        try (Response response = OkHttp.client(TIMEOUT).newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Metadata agent HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        if (!TextUtils.isEmpty(MetadataAgentSetting.getApiKey())) {
            headers.put("Authorization", "Bearer " + MetadataAgentSetting.getApiKey());
        }
        return headers;
    }

    public static class Resolution {
        private final String status;
        private final MovieMetadata selected;
        private final List<MetadataCandidate> candidates;
        private final String message;

        private Resolution(String status, MovieMetadata selected, List<MetadataCandidate> candidates, String message) {
            this.status = value(status);
            this.selected = selected;
            this.candidates = candidates == null ? List.of() : List.copyOf(candidates);
            this.message = value(message);
        }

        public boolean isMatched() { return "matched".equals(status) && selected != null; }
        public boolean needsConfirmation() { return "needs_confirmation".equals(status) && !candidates.isEmpty(); }
        public MovieMetadata getSelected() { return selected; }
        public List<MetadataCandidate> getCandidates() { return candidates; }
        public String getMessage() { return message; }
    }

    private static class Payload {
        private final String sourceInstanceId;
        private final String sourceVodId;
        private final String title;
        private final String year;
        private final String area;
        private final String type;
        private final String director;
        private final String actors;
        private final boolean allowAgent;

        private Payload(MovieIdentity identity, boolean allowAgent) {
            this.sourceInstanceId = identity.sourceInstanceId();
            this.sourceVodId = identity.sourceVodId();
            this.title = identity.title();
            this.year = identity.year();
            this.area = identity.area();
            this.type = identity.type();
            this.director = identity.director();
            this.actors = identity.actor();
            this.allowAgent = allowAgent;
        }
    }

    private static class ResponsePayload {
        private String status;
        private MetadataPayload selected;
        private List<CandidatePayload> candidates;
        private String message;

        private Resolution toResolution() {
            List<MetadataCandidate> result = new ArrayList<>();
            if (candidates != null) for (CandidatePayload item : candidates) {
                if (item != null && item.metadata != null) result.add(item.toCandidate());
            }
            return new Resolution(status, selected == null ? null : selected.toMetadata(), result, message);
        }
    }

    private static class CandidatePayload {
        private MetadataPayload metadata;
        private double deterministicScore;
        private Double modelConfidence;

        private MetadataCandidate toCandidate() {
            double confidence = modelConfidence == null ? deterministicScore : modelConfidence;
            return new MetadataCandidate(metadata.toMetadata(), confidence);
        }
    }

    private static class MetadataPayload {
        private String externalId;
        private String externalType;
        private String title;
        private String originalTitle;
        private String year;
        private String area;
        private String type;
        private String directors;
        private String actors;
        private String summary;
        private String poster;
        private String backdrop;
        private int backdropWidth;
        private int backdropHeight;
        private double rating;
        private int ratingCount;
        private int duration;
        private List<MovieArtwork> artworks;

        private MovieMetadata toMetadata() {
            MovieMetadata result = new MovieMetadata();
            result.setProvider(MetadataProvider.DOUBAN);
            result.setExternalId(value(externalId));
            result.setExternalType(value(externalType));
            result.setTitle(value(title));
            result.setOriginalTitle(value(originalTitle));
            result.setYear(value(year));
            result.setArea(value(area));
            result.setType(value(type));
            result.setDirectors(value(directors));
            result.setActors(value(actors));
            result.setSummary(value(summary));
            result.setPoster(value(poster));
            result.setBackdrop(value(backdrop));
            result.setBackdropWidth(backdropWidth);
            result.setBackdropHeight(backdropHeight);
            result.setRating(rating);
            result.setRatingCount(ratingCount);
            result.setDuration(duration);
            result.setArtworks(artworks == null ? List.of() : artworks);
            return result;
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
