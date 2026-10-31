package com.fongmi.android.tv.player.exo;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;

import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

/** Rejects short/changed range responses before CacheDataSource can mistake them for the real EOF. */
final class ExoRangeDataSource extends BaseDataSource {

    private static final Pattern RANGE = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)", Pattern.CASE_INSENSITIVE);
    private final ExoReadAhead session;
    private final Map<String, String> headers;
    private final long totalLength;
    private Call call;
    private Response response;
    private InputStream input;
    private long remaining;
    private boolean opened;

    ExoRangeDataSource(ExoReadAhead session, Map<String, String> headers, long totalLength) {
        super(true);
        this.session = session;
        this.headers = headers;
        this.totalLength = totalLength;
    }

    static long parseTotal(String header, long start, long end) throws InvalidRangeException {
        Matcher match = RANGE.matcher(header == null ? "" : header.trim());
        try {
            if (match.matches() && Long.parseLong(match.group(1)) == start && Long.parseLong(match.group(2)) == end) {
                long total = Long.parseLong(match.group(3));
                if (start >= 0 && end >= start && total > end) return total;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new InvalidRangeException("Invalid Content-Range");
    }

    @Override
    public long open(@NonNull DataSpec spec) throws IOException {
        transferInitializing(spec);
        if (spec.length <= 0 || spec.position < 0 || spec.position > totalLength - spec.length) {
            throw new InvalidRangeException("Range outside resource");
        }
        long end = spec.position + spec.length - 1;
        Request.Builder request = new Request.Builder().url(spec.uri.toString());
        for (Map.Entry<String, String> header : headers.entrySet()) request.header(header.getKey(), header.getValue());
        request.header("Range", "bytes=" + spec.position + "-" + end).header("Accept-Encoding", "identity");
        call = OkHttp.player().newCall(request.build());
        session.track(call);
        response = call.execute();
        if (response.code() != 206 || parseTotal(response.header("Content-Range"), spec.position, end) != totalLength) {
            throw new InvalidRangeException("Range response changed or unsupported");
        }
        String encoding = response.header("Content-Encoding");
        if (encoding != null && !"identity".equalsIgnoreCase(encoding)) throw new InvalidRangeException("Encoded range response");
        if (response.body() == null || response.body().contentLength() != C.LENGTH_UNSET && response.body().contentLength() != spec.length) {
            throw new InvalidRangeException("Range body length mismatch");
        }
        input = response.body().byteStream();
        remaining = spec.length;
        opened = true;
        transferStarted(spec);
        return remaining;
    }

    @Override
    public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        if (remaining == 0) return C.RESULT_END_OF_INPUT;
        int read = input.read(buffer, offset, (int) Math.min(remaining, length));
        if (read == -1) throw new InvalidRangeException("Truncated range body");
        remaining -= read;
        bytesTransferred(read);
        return read;
    }

    @Override
    public Uri getUri() {
        return response == null ? null : Uri.parse(response.request().url().toString());
    }

    @NonNull
    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return response == null ? Map.of() : response.headers().toMultimap();
    }

    @Override
    public void close() {
        try {
            if (call != null) call.cancel();
            if (response != null) response.close();
        } finally {
            if (call != null) session.untrack(call);
            call = null;
            response = null;
            input = null;
            if (opened) {
                opened = false;
                transferEnded();
            }
        }
    }

    static final class InvalidRangeException extends IOException {
        InvalidRangeException(String message) { super(message); }
    }
}
