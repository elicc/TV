package com.fongmi.android.tv.metadata;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Normalizes the two verified Douban list contracts, without changing provider order. */
final class DoubanListParser {
    static List<MovieMetadata> parse(JsonElement root, boolean recommendations) {
        JsonElement payload = recommendations ? root : object(root).get("subject_collection_items");
        if (payload == null || !payload.isJsonArray()) throw new IllegalArgumentException("Invalid Douban list envelope");
        List<MovieMetadata> result = new ArrayList<>();
        for (JsonElement element : payload.getAsJsonArray()) {
            JsonObject item = object(element);
            String id = text(item, "id"), title = text(item, "title"), type = text(item, "type");
            if (id.isEmpty() || title.isEmpty() || !(type.equals("movie") || type.equals("tv"))) continue;
            MovieMetadata movie = new MovieMetadata();
            movie.setProvider(MetadataProvider.DOUBAN);
            movie.setExternalId(id);
            movie.setExternalType(type);
            movie.setTitle(title);
            movie.setOriginalTitle(text(item, "original_title"));
            movie.setYear(text(item, "year"));
            String poster = text(object(item.get("cover")), "url");
            if (poster.isEmpty()) poster = text(object(item.get("pic")), "large");
            if (poster.isEmpty()) poster = text(object(item.get("pic")), "normal");
            if (!poster.isEmpty()) movie.setPoster(poster + "@Referer=https://movie.douban.com/@User-Agent=Mozilla/5.0");
            JsonObject rating = object(item.get("rating"));
            movie.setRating(number(rating, "value"));
            movie.setRatingCount((int) number(rating, "count"));
            String info = text(item, "info");
            String subtitle = text(item, "card_subtitle");
            String[] parts = (info.isEmpty() ? subtitle : info).split("\\s*/\\s*", -1);
            int offset = 0;
            // card_subtitle starts with a year; info does not. Never shift an
            // unfamiliar subtitle as if its first word were a country.
            if (info.isEmpty()) {
                if (parts.length == 0 || !parts[0].matches("[0-9]{4}")) {
                    movie.setType(subtitle);
                    parts = new String[0];
                } else {
                    if (movie.getYear().isEmpty()) movie.setYear(parts[0]);
                    offset = 1;
                }
            }
            if (parts.length > offset) movie.setArea(parts[offset]);
            if (parts.length > offset + 1) movie.setType(parts[offset + 1]);
            if (parts.length > offset + 2) movie.setDirectors(parts[offset + 2]);
            if (parts.length > offset + 3) movie.setActors(parts[offset + 3]);
            String directors = names(item.get("directors")), actors = names(item.get("actors"));
            if (!directors.isEmpty()) movie.setDirectors(directors);
            if (!actors.isEmpty()) movie.setActors(actors);
            result.add(movie);
        }
        return result;
    }

    private static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && !value.getAsJsonPrimitive().isBoolean()
                ? value.getAsString().trim() : "";
    }

    private static double number(JsonObject object, String key) {
        try {
            double value = Double.parseDouble(text(object, key));
            return Double.isFinite(value) && value >= 0 ? value : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String names(JsonElement element) {
        if (element == null || !element.isJsonArray()) return "";
        List<String> names = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) {
            String name = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    ? value.getAsString().trim() : text(object(value), "name");
            if (!name.isEmpty()) names.add(name);
        }
        return String.join("、", names);
    }
}
