package com.fongmi.android.tv.metadata;

import android.text.TextUtils;

import androidx.collection.ArrayMap;

import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.Response;

/** Direct Douban client. Keep credentials here until the proxy migration is implemented. */
public class DoubanProvider implements MetadataProviderClient {
    private static final String BASE_URL = "https://frodo.douban.com";
    // TODO: move these values to the proxy/configuration before publishing a production build.
    private static final String API_KEY = "0ac44ae016490db2204ce0a042db2916";
    private static final Map<String, String> HEADERS = new LinkedHashMap<>();

    static {
        HEADERS.put("Host", "frodo.douban.com");
        HEADERS.put("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_1_1) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.46 NetType/WIFI Language/zh_CN");
        HEADERS.put("Referer", "https://servicewechat.com/wx2f9b06c1de1ccfca");
        HEADERS.put("Content-Type", "application/json");
    }

    @Override
    public MetadataProvider provider() {
        return MetadataProvider.DOUBAN;
    }

    @Override
    public List<MovieMetadata> search(MovieIdentity identity) throws IOException {
        ArrayMap<String, String> params = new ArrayMap<>();
        params.put("q", MetadataMatcher.queryTitle(identity.title()));
        params.put("start", "0");
        params.put("count", "5");
        JsonObject root = request("/api/v2/search/weixin", params);
        List<MovieMetadata> result = new ArrayList<>();
        JsonArray items = array(root, "items");
        for (JsonElement element : items) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            JsonObject target = object(item, "target");
            JsonObject data = target.entrySet().isEmpty() ? item : target;
            String targetType = string(item, "target_type");
            if (!"movie".equals(targetType) && !"tv".equals(targetType)) continue;
            String externalId = string(item, "target_id");
            if (externalId.isEmpty()) externalId = string(data, "id");
            if (externalId.isEmpty()) continue;
            MovieMetadata metadata = new MovieMetadata();
            metadata.setProvider(MetadataProvider.DOUBAN);
            metadata.setExternalId(externalId);
            metadata.setExternalType(targetType);
            metadata.setTitle(string(data, "title"));
            metadata.setYear(string(data, "year"));
            metadata.setPoster(string(data, "cover_url"));
            if (metadata.getPoster().isEmpty()) metadata.setPoster(nestedString(data, "pic", "normal", "nomal"));
            JsonObject rating = object(data, "rating");
            metadata.setRating(number(rating, "value"));
            metadata.setRatingCount((int) number(rating, "count"));
            result.add(metadata);
        }
        return result;
    }

    @Override
    public MovieMetadata detail(String externalId, boolean tv) throws IOException {
        String path = (tv ? "/api/v2/tv/" : "/api/v2/movie/") + externalId;
        JsonObject object = request(path, new ArrayMap<>());
        MovieMetadata metadata = new MovieMetadata();
        metadata.setProvider(MetadataProvider.DOUBAN);
        metadata.setExternalId(externalId);
        metadata.setExternalType(tv ? "tv" : "movie");
        metadata.setTitle(string(object, "title"));
        metadata.setOriginalTitle(string(object, "original_title"));
        metadata.setYear(join(array(object, "pubdate")));
        metadata.setArea(join(array(object, "countries")));
        metadata.setType(join(array(object, "genres")));
        metadata.setDirectors(names(array(object, "directors")));
        metadata.setActors(names(array(object, "actors")));
        metadata.setSummary(string(object, "intro"));
        metadata.setPoster(nestedString(object, "pic", "normal", "nomal"));
        if (metadata.getPoster().isEmpty()) metadata.setPoster(nestedString(object, "cover", "url"));
        JsonObject rating = object(object, "rating");
        metadata.setRating(number(rating, "value"));
        metadata.setRatingCount((int) number(rating, "count"));
        metadata.setDuration((int) number(object, "durations"));
        // Photos are display-only and must never make an otherwise valid detail
        // response fail. The CDN requires the movie site referer (see
        // artworkUrl), so retain that header on the URL rather than changing the
        // global image loader behaviour.
        try {
            enrichArtwork(metadata, tv);
        } catch (IOException ignored) {
            // Older/blocked records still have useful title metadata and poster.
        }
        return metadata;
    }

    @Override
    public void enrichArtwork(MovieMetadata metadata, boolean tv) throws IOException {
        if (metadata == null || TextUtils.isEmpty(metadata.getExternalId())
                || (metadata.hasBackdrop() && metadata.hasArtworks())) return;
        if ("tv".equals(metadata.getExternalType())) tv = true;
        else if ("movie".equals(metadata.getExternalType())) tv = false;
        ArrayMap<String, String> params = new ArrayMap<>();
        params.put("start", "0");
        params.put("count", "20");
        JsonObject root = request((tv ? "/api/v2/tv/" : "/api/v2/movie/") + metadata.getExternalId() + "/photos", params);
        List<Photo> photos = choosePhotos(array(root, "photos"));
        // Some records put portraits first. One bounded follow-up page improves
        // coverage without turning a focus change into an unbounded crawl.
        if (photos.isEmpty() && number(root, "total") > 20) {
            params.put("start", "20");
            photos = choosePhotos(array(request((tv ? "/api/v2/tv/" : "/api/v2/movie/") + metadata.getExternalId() + "/photos", params), "photos"));
        }
        if (!photos.isEmpty()) {
            Photo best = photos.get(0);
            metadata.setBackdrop(artworkUrl(best.url));
            metadata.setBackdropWidth(best.width);
            metadata.setBackdropHeight(best.height);
            List<MovieArtwork> artworks = new ArrayList<>();
            for (Photo photo : photos) artworks.add(new MovieArtwork(artworkUrl(photo.url), photo.width, photo.height));
            metadata.setArtworks(artworks);
        }
    }

    private JsonObject request(String path, ArrayMap<String, String> params) throws IOException {
        params.put("apiKey", API_KEY);
        try (Response response = OkHttp.newCall(BASE_URL + path, HEADERS, params).execute()) {
            if (response.body() == null || !response.isSuccessful()) throw new IOException("Douban HTTP " + response.code());
            String body = response.body().string();
            if (TextUtils.isEmpty(body)) throw new IOException("Douban empty response");
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static String nestedString(JsonObject object, String key, String... names) {
        JsonObject child = object(object, key);
        for (String name : names) {
            String value = string(child, name);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static double number(JsonObject object, String key) {
        try {
            String value = string(object, key);
            return value.isEmpty() ? 0d : Double.parseDouble(value);
        } catch (RuntimeException ignored) {
            return 0d;
        }
    }

    private static String join(JsonArray array) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) if (element.isJsonPrimitive()) values.add(element.getAsString());
        return String.join(" / ", values);
    }

    private static String names(JsonArray array) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                String name = string(element.getAsJsonObject(), "name");
                if (!name.isEmpty()) values.add(name);
            }
        }
        return String.join("、", values);
    }

    private static List<Photo> choosePhotos(JsonArray photos) {
        List<PhotoScore> scored = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (JsonElement element : photos) {
            if (!element.isJsonObject()) continue;
            JsonObject photo = element.getAsJsonObject();
            JsonObject image = object(photo, "image");
            JsonObject large = object(image, "large");
            String url = string(large, "url");
            int width = integer(large, "width");
            int height = integer(large, "height");
            if (url.isEmpty()) {
                JsonObject normal = object(image, "normal");
                url = string(normal, "url");
                width = integer(normal, "width");
                height = integer(normal, "height");
            }
            if (url.isEmpty()) url = string(photo, "url");
            if (url.isEmpty() || width < 800 || height < 450 || width < height || !seen.add(url)) continue;
            double ratio = (double) width / height;
            if (ratio < 1.45d) continue;
            // Prefer 16:9-ish photos, then resolution. The photos endpoint is
            // ordered by provider relevance, so position remains a small tie-breaker.
            double ratioScore = 1d - Math.min(1d, Math.abs(ratio - (16d / 9d)) / 1.2d);
            double resolutionScore = Math.min(1d, (double) width * height / 2_000_000d);
            double score = ratioScore * 0.65d + resolutionScore * 0.35d;
            scored.add(new PhotoScore(new Photo(url, width, height), score));
        }
        scored.sort((left, right) -> Double.compare(right.score, left.score));
        List<Photo> result = new ArrayList<>();
        for (int i = 0; i < Math.min(10, scored.size()); i++) result.add(scored.get(i).photo);
        return result;
    }

    private static String artworkUrl(String url) {
        return url.contains("@Referer=") ? url : url + "@Referer=https://movie.douban.com/@User-Agent=Mozilla/5.0";
    }

    private static int integer(JsonObject object, String key) {
        try {
            String value = string(object, key);
            return value.isEmpty() ? 0 : Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static final class Photo {
        private final String url;
        private final int width;
        private final int height;

        private Photo(String url, int width, int height) {
            this.url = url;
            this.width = width;
            this.height = height;
        }
    }

    private static final class PhotoScore {
        private final Photo photo;
        private final double score;

        private PhotoScore(Photo photo, double score) {
            this.photo = photo;
            this.score = score;
        }
    }
}
