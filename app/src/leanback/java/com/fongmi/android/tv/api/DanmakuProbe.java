package com.fongmi.android.tv.api;

import android.net.Uri;
import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.media3.ui.danmaku.parser.BiliParser;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** An explicit, bounded diagnostic. It never cancels or changes playback searches. */
public final class DanmakuProbe {

    private static final int SEARCH_LIMIT = 1024 * 1024;
    private static final int XML_LIMIT = 8 * 1024 * 1024;
    private static final int TIMEOUT_SECONDS = 15;

    public enum Code {
        SUCCESS, NO_RESULTS, INVALID_URL, SEARCH_HTTP, SEARCH_FORMAT, SITE_CONFIG,
        XML_HTTP, XML_FORMAT, NETWORK_ERROR, LIMIT_EXCEEDED
    }

    public static final class Result {
        public final Code code;
        public final int httpStatus;
        public final int candidateCount;
        public final int commentCount;

        private Result(Code code, int httpStatus, int candidateCount, int commentCount) {
            this.code = code;
            this.httpStatus = httpStatus;
            this.candidateCount = candidateCount;
            this.commentCount = commentCount;
        }
    }

    private int generation;
    private Call current;

    public synchronized void cancel() {
        generation++;
        if (current != null) current.cancel();
        current = null;
    }

    public synchronized void start(String url, String name, String episode, Consumer<Result> callback) {
        cancel();
        int id = generation;
        if (url == null || HttpUrl.parse(url.trim()) == null) {
            finish(id, callback, new Result(Code.INVALID_URL, 0, 0, 0));
            return;
        }
        String title = Trans.t2s(name == null ? "" : name);
        String number = Trans.t2s(episode == null ? "" : episode);
        String template = url.trim();
        Request.Builder request = new Request.Builder();
        try {
            if (template.contains("{name}") || template.contains("{episode}")) {
                request.url(template.replace("{name}", Uri.encode(title)).replace("{episode}", Uri.encode(number)));
            } else {
                request.url(template).post(new FormBody.Builder().add("name", title).add("episode", number).build());
            }
        } catch (IllegalArgumentException e) {
            finish(id, callback, new Result(Code.INVALID_URL, 0, 0, 0));
            return;
        }
        OkHttpClient client = OkHttp.client().newBuilder()
                .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build();
        enqueue(id, client, request.build(), 0, false, callback);
    }

    private synchronized void enqueue(int id, OkHttpClient client, Request request, int candidates, boolean xml, Consumer<Result> callback) {
        if (id != generation) return;
        current = client.newCall(request);
        current.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                finish(id, callback, new Result(Code.NETWORK_ERROR, 0, candidates, 0));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response closeable = response) {
                    if (!response.isSuccessful()) {
                        finish(id, callback, new Result(xml ? Code.XML_HTTP : Code.SEARCH_HTTP, response.code(), candidates, 0));
                        return;
                    }
                    byte[] bytes = read(response.body(), xml ? XML_LIMIT : SEARCH_LIMIT);
                    if (xml) {
                        finish(id, callback, new Result(Code.SUCCESS, response.code(), candidates, countComments(bytes)));
                    } else {
                        SearchResult search = parseSearch(new String(bytes, StandardCharsets.UTF_8));
                        if (search.count == 0) finish(id, callback, new Result(Code.NO_RESULTS, response.code(), 0, 0));
                        else enqueue(id, client, new Request.Builder().url(search.firstUrl).build(), search.count, true, callback);
                    }
                } catch (ProbeException e) {
                    finish(id, callback, new Result(e.code, response.code(), candidates, 0));
                } catch (IOException e) {
                    finish(id, callback, new Result(Code.NETWORK_ERROR, response.code(), candidates, 0));
                } catch (RuntimeException e) {
                    finish(id, callback, new Result(xml ? Code.XML_FORMAT : Code.SEARCH_FORMAT, response.code(), candidates, 0));
                }
            }
        });
    }

    private void finish(int id, Consumer<Result> callback, Result result) {
        App.post(() -> {
            synchronized (DanmakuProbe.this) {
                if (id != generation) return;
                current = null;
                callback.accept(result);
            }
        });
    }

    private static byte[] read(ResponseBody body, int limit) throws IOException {
        if (body == null) return new byte[0];
        if (body.contentLength() > limit) throw new ProbeException(Code.LIMIT_EXCEEDED);
        try (InputStream input = body.byteStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > limit) throw new ProbeException(Code.LIMIT_EXCEEDED);
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static SearchResult parseSearch(String text) throws ProbeException {
        SearchResult result = new SearchResult();
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (key.equals("sites") || key.equals("spider")) throw new ProbeException(Code.SITE_CONFIG);
                    reader.skipValue();
                }
                throw new ProbeException(Code.SEARCH_FORMAT);
            }
            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();
                String url = null;
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (key.equals("url")) {
                        if (reader.peek() != JsonToken.STRING) throw new ProbeException(Code.SEARCH_FORMAT);
                        url = reader.nextString();
                    } else if (key.equals("name")) {
                        if (reader.peek() != JsonToken.STRING && reader.peek() != JsonToken.NULL) throw new ProbeException(Code.SEARCH_FORMAT);
                        reader.skipValue();
                    } else reader.skipValue();
                }
                reader.endObject();
                if (url == null || HttpUrl.parse(url) == null) throw new ProbeException(Code.SEARCH_FORMAT);
                if (result.count++ == 0) result.firstUrl = url;
            }
            reader.endArray();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new ProbeException(Code.SEARCH_FORMAT);
            return result;
        } catch (ProbeException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ProbeException(Code.SEARCH_FORMAT);
        }
    }

    private static int countComments(byte[] bytes) throws ProbeException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            // nextToken exposes declarations before any entity references are consumed.
            // Android's non-validating parser defaults to false; some implementations
            // reject setFeature even for false, so fail closed if processing is enabled.
            if (parser.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL)) throw new ProbeException(Code.XML_FORMAT);
            parser.setInput(new ByteArrayInputStream(bytes), null);
            int count = 0;
            boolean root = false;
            for (int event = parser.nextToken(); event != XmlPullParser.END_DOCUMENT; event = parser.nextToken()) {
                if (event == XmlPullParser.DOCDECL || (event == XmlPullParser.ENTITY_REF && parser.getText() == null)) {
                    throw new ProbeException(Code.XML_FORMAT);
                }
                if (event != XmlPullParser.START_TAG) continue;
                if (parser.getDepth() > 64) throw new ProbeException(Code.LIMIT_EXCEEDED);
                if (parser.getDepth() == 1) {
                    if (root || !"i".equals(parser.getName())) throw new ProbeException(Code.XML_FORMAT);
                    root = true;
                } else if (parser.getDepth() == 2 && "d".equals(parser.getName())) {
                    validateMetadata(parser.getAttributeValue(null, "p"));
                    count++;
                }
            }
            if (!root) throw new ProbeException(Code.XML_FORMAT);
            // Run only after the safe walk: use the bundled player's actual parser so
            // empty text, unsupported positioned payloads, etc. cannot inflate the count.
            int playable = BiliParser.INSTANCE.parse(new ByteArrayInputStream(bytes)).size();
            if (playable != count) throw new ProbeException(Code.XML_FORMAT);
            return playable;
        } catch (ProbeException e) {
            throw e;
        } catch (Exception e) {
            throw new ProbeException(Code.XML_FORMAT);
        }
    }

    private static void validateMetadata(String metadata) throws ProbeException {
        if (metadata == null) throw new ProbeException(Code.XML_FORMAT);
        String[] fields = metadata.split(",", 5);
        if (fields.length < 4) throw new ProbeException(Code.XML_FORMAT);
        try {
            // ParserUtil.parsePAttr uses these same numeric types. The diagnostic is
            // stricter about nonsensical values that playback would silently coerce.
            float time = Float.parseFloat(fields[0]);
            int mode = Integer.parseInt(fields[1]);
            int size = Integer.parseInt(fields[2]);
            long color = Long.parseLong(fields[3]);
            if (Float.isNaN(time) || Float.isInfinite(time) || time < 0 || time * 1000d >= Long.MAX_VALUE
                    || mode < 1 || mode > 7 || size <= 0 || color < 0 || color > 0xFFFFFFL) {
                throw new ProbeException(Code.XML_FORMAT);
            }
        } catch (NumberFormatException e) {
            throw new ProbeException(Code.XML_FORMAT);
        }
    }

    private static final class SearchResult {
        int count;
        String firstUrl;
    }

    private static final class ProbeException extends IOException {
        final Code code;

        ProbeException(Code code) {
            this.code = code;
        }
    }
}
