package com.fongmi.android.tv.api;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Path;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

/**
 * Disk cache for danmaku sources found by the automatic search.
 *
 * <p>A full source is around 10 MB and the search service is slow (about 0.4–1.3 MB/s, with
 * occasional stalls), so the search and download take 15–40 seconds. Comment URLs change between
 * searches, so entries are keyed by the searched name and episode instead of the URL. The body is
 * copied to disk while the player's danmaku loader streams it, and kept only after it parsed.
 */
public final class DanmakuCache {

    private static final long MAX_AGE = TimeUnit.DAYS.toMillis(7);
    private static final long MAX_SIZE = 200L * 1024 * 1024;
    private static final long STALE_TEMP_AGE = TimeUnit.HOURS.toMillis(1);
    private static final String DATA = ".xml";
    private static final String NAME = ".name";
    private static final String TEMP = ".tmp";

    private static final Map<String, Entry> TRACKED = new ConcurrentHashMap<>();
    private static OkHttpClient client;

    private DanmakuCache() {
    }

    /** Returns the HTTP client for danmaku sources, which copies tracked responses to disk. */
    public static synchronized OkHttpClient client() {
        if (client != null) return client;
        return client = OkHttp.player().newBuilder().addInterceptor(DanmakuCache::intercept).build();
    }

    /** Returns the cached source for a search, or {@code null} if none is fresh. */
    @Nullable
    public static Danmaku find(String name, String episode) {
        String key = key(name, episode);
        File data = file(key, DATA);
        if (!data.isFile() || System.currentTimeMillis() - data.lastModified() > MAX_AGE) return null;
        String title = Path.read(file(key, NAME));
        return Danmaku.from(title.isEmpty() ? null : title, data.getAbsolutePath());
    }

    /** Copies {@code item} to the cache the next time the danmaku loader downloads it. */
    public static void track(Danmaku item, String name, String episode) {
        String url = normalize(item.getUrl());
        if (url == null) return;
        TRACKED.clear();
        TRACKED.put(url, new Entry(key(name, episode), item.getName()));
    }

    /** Keeps the downloaded copy of {@code uri} once the loader has parsed {@code count} items. */
    public static void onLoaded(Uri uri, int count) {
        String url = normalize(uri.toString());
        Entry entry = url == null ? null : TRACKED.remove(url);
        if (entry == null) return;
        if (count > 0) Task.execute(entry::commit);
        else Task.execute(entry::discard);
    }

    /** Drops a cached source that could not be loaded so the next playback downloads it again. */
    public static void onFailed(Uri uri) {
        if (!"file".equals(uri.getScheme()) || uri.getPath() == null) return;
        File data = new File(uri.getPath());
        if (!dir().equals(data.getParentFile()) || !data.getName().endsWith(DATA)) return;
        String key = data.getName().substring(0, data.getName().length() - DATA.length());
        Task.execute(() -> {
            data.delete();
            file(key, NAME).delete();
        });
    }

    private static Response intercept(Interceptor.Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        Entry entry = TRACKED.get(chain.request().url().toString());
        ResponseBody body = response.body();
        if (entry == null || !response.isSuccessful() || body == null) return response;
        File temp = entry.begin();
        if (temp == null) return response;
        BufferedSink sink;
        try {
            sink = Okio.buffer(Okio.sink(temp));
        } catch (IOException e) {
            temp.delete();
            return response;
        }
        Source source = new TeeSource(body.source(), sink, temp);
        return response.newBuilder().body(ResponseBody.create(Okio.buffer(source), body.contentType(), body.contentLength())).build();
    }

    @Nullable
    private static String normalize(String url) {
        HttpUrl parsed = HttpUrl.parse(url);
        return parsed == null ? null : parsed.toString();
    }

    private static String key(String name, String episode) {
        return Crypto.md5(name + "\n" + episode);
    }

    private static File dir() {
        return Path.cache("danmaku");
    }

    private static File file(String key, String suffix) {
        return new File(dir(), key + suffix);
    }

    private static synchronized void trim() {
        File[] files = dir().listFiles();
        if (files == null) return;
        long now = System.currentTimeMillis();
        long total = 0;
        for (File file : files) {
            if (file.getName().endsWith(TEMP) && now - file.lastModified() > STALE_TEMP_AGE) file.delete();
            else if (file.getName().endsWith(DATA)) total += file.length();
        }
        File[] data = dir().listFiles((dir, name) -> name.endsWith(DATA));
        if (data == null) return;
        Arrays.sort(data, Comparator.comparingLong(File::lastModified));
        for (File file : data) {
            boolean expired = now - file.lastModified() > MAX_AGE;
            if (!expired && total <= MAX_SIZE) continue;
            total -= file.length();
            String key = file.getName().substring(0, file.getName().length() - DATA.length());
            file.delete();
            file(key, NAME).delete();
        }
    }

    private static final class Entry {

        private final String key;
        private final String name;
        private File temp;

        Entry(String key, String name) {
            this.key = key;
            this.name = name;
        }

        @Nullable
        synchronized File begin() {
            discard();
            File dir = dir();
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            return temp = new File(dir, key + "." + System.nanoTime() + TEMP);
        }

        synchronized void commit() {
            if (temp == null || temp.length() == 0) return;
            File data = file(key, DATA);
            data.delete();
            if (!temp.renameTo(data)) {
                discard();
                return;
            }
            temp = null;
            try (FileOutputStream output = new FileOutputStream(file(key, NAME))) {
                output.write(name.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
            trim();
        }

        synchronized void discard() {
            if (temp != null) temp.delete();
            temp = null;
        }
    }

    /** Passes the body through unchanged and writes a copy; a failed write only loses the copy. */
    private static final class TeeSource extends ForwardingSource {

        private final BufferedSink sink;
        private final File temp;
        private boolean failed;

        TeeSource(Source source, BufferedSink sink, File temp) {
            super(source);
            this.sink = sink;
            this.temp = temp;
        }

        @Override
        public long read(@NonNull Buffer buffer, long byteCount) throws IOException {
            long read = super.read(buffer, byteCount);
            if (read > 0 && !failed) {
                try {
                    buffer.copyTo(sink.getBuffer(), buffer.size() - read, read);
                    sink.emitCompleteSegments();
                } catch (IOException e) {
                    // An empty file is never committed.
                    failed = true;
                    temp.delete();
                }
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                try {
                    sink.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
