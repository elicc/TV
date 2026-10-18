package com.fongmi.android.tv.api;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.collection.ArrayMap;

import com.fongmi.android.tv.App;
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
    private static final long[] AUTO_RETRY_DELAYS_MS = {2_000, 5_000};

    private static Prefetch prefetch;

    public static boolean canSearch() {
        return DanmakuSetting.isLoad() && DanmakuSetting.isAuto() && !TextUtils.isEmpty(DanmakuSetting.getEffectiveApiUrl());
    }

    public static void cancel() {
        REQUEST_ID.incrementAndGet();
        prefetch = null;
        OkHttp.cancel(TAG);
    }

    /**
     * Starts the automatic search while the play URL is still resolving. The search takes 6–13
     * seconds, and {@link #search(String, String, Consumer)} picks up the result for the same
     * name and episode instead of starting over.
     */
    public static void prefetch(String name, String episode) {
        Prefetch next = new Prefetch(name, episode);
        next.requestId = search(name, episode, next::complete, error -> next.complete(List.of()), true);
        prefetch = next;
    }

    /** Runs the automatic search, retrying network failures, and reports the first result. */
    public static void search(String name, String episode, Consumer<Danmaku> found) {
        Prefetch pending = prefetch;
        prefetch = null;
        if (pending != null && pending.matches(name, episode)) pending.deliverTo(found);
        else search(name, episode, items -> items.stream().findFirst().ifPresent(found), error -> {}, true);
    }

    public static void search(String name, String episode, Consumer<List<Danmaku>> success, Consumer<Exception> error) {
        search(name, episode, success, error, false);
    }

    private static int search(String name, String episode, Consumer<List<Danmaku>> success, Consumer<Exception> error, boolean retry) {
        int requestId = begin();
        enqueue(name, episode, requestId, retry ? 0 : AUTO_RETRY_DELAYS_MS.length, success, error);
        return requestId;
    }

    private static void enqueue(String name, String episode, int requestId, int attempt, Consumer<List<Danmaku>> success, Consumer<Exception> error) {
        Consumer<Exception> failure = e -> {
            if (attempt < AUTO_RETRY_DELAYS_MS.length && isRetryable(e)) retry(name, episode, requestId, attempt, success, error);
            else error.accept(e instanceof SearchException ? e : new SearchException(R.string.danmaku_search_network_error));
        };
        try {
            Call call = newCall(name, episode);
            call.enqueue(new ApiCallback<>(requestId, REQUEST_ID, response -> parse(read(response)), success, e -> {
                // Paths and query parameters can contain service tokens and viewing history.
                Log.w(TAG, "search failed: " + call.request().method() + " " + call.request().url().host() + " " + e.getClass().getSimpleName());
                failure.accept(e);
            }));
        } catch (IllegalArgumentException e) {
            ApiCallback.post(requestId, REQUEST_ID, () -> error.accept(new SearchException(R.string.danmaku_api_invalid_url)));
        }
    }

    private static void retry(String name, String episode, int requestId, int attempt, Consumer<List<Danmaku>> success, Consumer<Exception> error) {
        App.post(() -> {
            if (REQUEST_ID.get() == requestId) enqueue(name, episode, requestId, attempt + 1, success, error);
        }, AUTO_RETRY_DELAYS_MS[attempt]);
    }

    private static boolean isRetryable(Exception e) {
        return !(e instanceof SearchException) || ((SearchException) e).retryable;
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
            int code = response.code();
            throw new SearchException(ResUtil.getString(R.string.danmaku_search_http_error, code), code >= 500 || code == 429);
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

        private final boolean retryable;

        SearchException(int message) {
            this(ResUtil.getString(message), false);
        }

        SearchException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }
    }

    /** An automatic search started early, waiting for the playback that needs its result. */
    private static class Prefetch {

        private final String name;
        private final String episode;
        private int requestId;
        private boolean done;
        private Danmaku result;
        private Consumer<Danmaku> found;

        Prefetch(String name, String episode) {
            this.name = name;
            this.episode = episode;
        }

        boolean matches(String name, String episode) {
            return requestId == REQUEST_ID.get() && this.name.equals(name) && this.episode.equals(episode);
        }

        void complete(List<Danmaku> items) {
            done = true;
            result = items.isEmpty() ? null : items.get(0);
            if (found != null && result != null) found.accept(result);
        }

        void deliverTo(Consumer<Danmaku> found) {
            // Deliver on a later message, as a fresh search would: the playback that asked has
            // not created its media item yet.
            if (!done) this.found = found;
            else if (result != null) App.post(() -> {
                if (requestId == REQUEST_ID.get()) found.accept(result);
            });
        }
    }
}
