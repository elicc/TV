package com.fongmi.android.tv.api;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.collection.ArrayMap;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.impl.ApiCallback;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Response;

public class DanmakuApi {

    private static final String TAG = DanmakuApi.class.getSimpleName();
    private static final AtomicInteger REQUEST_ID = new AtomicInteger();

    public static boolean canSearch() {
        return DanmakuSetting.isLoad() && DanmakuSetting.isAuto() && !TextUtils.isEmpty(DanmakuSetting.getEffectiveApiUrl());
    }

    public static void cancel() {
        REQUEST_ID.incrementAndGet();
        OkHttp.cancel(TAG);
    }

    public static void search(String name, String episode, Consumer<Danmaku> found) {
        search(name, episode, items -> items.stream().findFirst().ifPresent(found), error -> {});
    }

    public static void search(String name, String episode, Consumer<List<Danmaku>> success, Consumer<Exception> error) {
        int requestId = begin();
        Consumer<Exception> failure = e -> error.accept(e instanceof SearchException ? e : new SearchException(R.string.danmaku_search_network_error));
        try {
            Call call = newCall(name, episode);
            call.enqueue(new ApiCallback<>(requestId, REQUEST_ID, response -> parse(read(response)), success, e -> {
                // Paths and query parameters can contain service tokens and viewing history.
                Log.w(TAG, "search failed: " + call.request().method() + " " + call.request().url().host() + " " + e.getClass().getSimpleName());
                failure.accept(e);
            }));
        } catch (IllegalArgumentException e) {
            ApiCallback.post(requestId, REQUEST_ID, () -> failure.accept(new SearchException(R.string.danmaku_api_invalid_url)));
        }
    }

    private static int begin() {
        cancel();
        return REQUEST_ID.get();
    }

    private static Call newCall(String name, String episode) {
        name = Trans.t2s(name);
        episode = Trans.t2s(episode);
        String url = DanmakuSetting.getEffectiveApiUrl();
        return url.contains("{name}") || url.contains("{episode}") ? getCall(url, name, episode) : postCall(url, name, episode);
    }

    public static boolean isValidUrl(String url) {
        return url != null && HttpUrl.parse(url) != null;
    }

    private static Call getCall(String url, String name, String episode) {
        return OkHttp.newCall(url.replace("{name}", Uri.encode(name)).replace("{episode}", Uri.encode(episode)), TAG);
    }

    private static Call postCall(String url, String name, String episode) {
        ArrayMap<String, String> params = new ArrayMap<>();
        params.put("name", name);
        params.put("episode", episode);
        return OkHttp.newCall(url, OkHttp.toBody(params), TAG);
    }

    private static String read(Response response) throws IOException {
        if (!response.isSuccessful()) {
            Log.w(TAG, "search HTTP " + response.code() + ": " + response.request().method() + " " + response.request().url().host());
            throw new SearchException(ResUtil.getString(R.string.danmaku_search_http_error, response.code()));
        }
        String text = response.body() == null ? "" : response.body().string();
        if (text.trim().isEmpty()) throw new SearchException(R.string.danmaku_search_empty_response);
        return text;
    }

    private static List<Danmaku> parse(String text) throws IOException {
        try {
            JsonElement root = JsonParser.parseString(text);
            if (root.isJsonObject() && (root.getAsJsonObject().has("sites") || root.getAsJsonObject().has("spider"))) {
                throw new SearchException(R.string.danmaku_api_site_config);
            }
            if (!root.isJsonArray()) throw new SearchException(R.string.danmaku_api_invalid_response);
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) throw new SearchException(R.string.danmaku_api_invalid_response);
                JsonObject item = element.getAsJsonObject();
                if (!isString(item.get("url")) || item.get("url").getAsString().trim().isEmpty()) {
                    throw new SearchException(R.string.danmaku_api_invalid_response);
                }
                // A missing/null name is supported: Danmaku.getName() falls back to the URL.
                if (item.has("name") && !item.get("name").isJsonNull() && !isString(item.get("name"))) {
                    throw new SearchException(R.string.danmaku_api_invalid_response);
                }
            }
            return Danmaku.arrayFrom(text);
        } catch (RuntimeException e) {
            throw new SearchException(R.string.danmaku_api_invalid_response);
        }
    }

    private static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private static class SearchException extends IOException {
        SearchException(int message) {
            this(ResUtil.getString(message));
        }

        SearchException(String message) {
            super(message);
        }
    }
}
