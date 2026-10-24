package com.fongmi.android.tv.api;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Connection;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Requests a danmaku source while the player is still buffering.
 *
 * <p>The danmaku loader waits for the first ready state so that it takes no bandwidth or CPU from
 * video startup. A search service builds a source when it is first requested, which takes 2–5
 * seconds, so the request is sent early and its response handed to the loader when it asks for the
 * same URL.
 *
 * <p>Until then the socket receive buffer is small, so only tens of KB arrive while the video
 * buffers: a 256 KB buffer measurably delayed video startup on a TV. It is shrunk after the
 * connection is made, though, which keeps the large window scale of the default buffer, so that
 * enlarging it again for the loader restores the download rate. A buffer limited from the start
 * kept a 64 KB window and slowed the download behind playing video to about 70 KB/s.
 */
public final class DanmakuEarlyRequest implements Callback {

    private static final int BUFFERING_RECEIVE_BUFFER_SIZE = 32 * 1024;
    private static final int LOADING_RECEIVE_BUFFER_SIZE = 2 * 1024 * 1024;
    private static final long MAX_UNCLAIMED_MS = TimeUnit.MINUTES.toMillis(1);
    private static final long CANCEL_CHECK_MS = 200;

    private static OkHttpClient client;
    private static DanmakuEarlyRequest pending;

    private final String url;
    private final Call call;
    private Socket socket;
    private Response response;
    private boolean done;
    private boolean discarded;

    private DanmakuEarlyRequest(String url) {
        this.url = url;
        this.call = client().newCall(new Request.Builder().url(url).tag(DanmakuEarlyRequest.class, this).build());
    }

    /** Sends the request for {@code uri} now, replacing an unclaimed one for another source. */
    public static synchronized void start(@Nullable Uri uri) {
        HttpUrl url = uri == null ? null : HttpUrl.parse(uri.toString());
        if (url == null) return;
        if (pending != null && pending.url.equals(url.toString())) return;
        if (pending != null) pending.discard();
        DanmakuEarlyRequest request = pending = new DanmakuEarlyRequest(url.toString());
        request.call.enqueue(request);
        App.post(() -> expire(request), MAX_UNCLAIMED_MS);
    }

    /**
     * Returns the early response for the request of {@code chain}, waiting for it if needed, or
     * {@code null} if there is none or it failed and the request should be sent normally.
     */
    @Nullable
    static Response take(Interceptor.Chain chain) throws IOException {
        DanmakuEarlyRequest request;
        synchronized (DanmakuEarlyRequest.class) {
            request = pending;
            pending = null;
        }
        if (request == null) return null;
        if (!request.url.equals(chain.request().url().toString())) {
            request.discard();
            return null;
        }
        Response response = request.await(chain.call());
        return response == null ? null : response.newBuilder().request(chain.request()).build();
    }

    private static synchronized void expire(DanmakuEarlyRequest request) {
        if (pending != request) return;
        pending = null;
        request.discard();
    }

    private static synchronized OkHttpClient client() {
        if (client != null) return client;
        // Own dispatcher and pool: the loader cancels its client's calls, and a socket with a
        // small buffer must not be reused for other requests.
        return client = OkHttp.player().newBuilder().dispatcher(new Dispatcher()).connectionPool(new ConnectionPool()).addNetworkInterceptor(DanmakuEarlyRequest::limitReceiveBuffer).build();
    }

    private static Response limitReceiveBuffer(Interceptor.Chain chain) throws IOException {
        DanmakuEarlyRequest request = chain.request().tag(DanmakuEarlyRequest.class);
        Connection connection = chain.connection();
        if (request != null && connection != null) request.setSocket(connection.socket());
        return chain.proceed(chain.request());
    }

    private synchronized void setSocket(Socket socket) {
        this.socket = socket;
        setReceiveBufferSize(BUFFERING_RECEIVE_BUFFER_SIZE);
    }

    private void setReceiveBufferSize(int size) {
        try {
            if (socket != null) socket.setReceiveBufferSize(size);
        } catch (SocketException ignored) {
            // Only the bandwidth share changes.
        }
    }

    @Nullable
    private synchronized Response await(Call owner) throws IOException {
        try {
            while (!done) {
                if (owner.isCanceled()) throw new IOException("Canceled");
                wait(CANCEL_CHECK_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            discard();
            throw new InterruptedIOException();
        } catch (IOException e) {
            discard();
            throw e;
        }
        if (response != null) setReceiveBufferSize(LOADING_RECEIVE_BUFFER_SIZE);
        return response;
    }

    private synchronized void discard() {
        discarded = true;
        Response unclaimed = response;
        response = null;
        // Expiry and replacement run on the main thread, and closing an unread body reads the rest
        // of it from the socket, so both the cancel and the close happen in the background.
        Task.execute(() -> {
            call.cancel();
            if (unclaimed != null) unclaimed.close();
        });
    }

    @Override
    public synchronized void onResponse(@NonNull Call call, @NonNull Response response) {
        done = true;
        notifyAll();
        if (discarded || !response.isSuccessful()) response.close();
        else this.response = response;
    }

    @Override
    public synchronized void onFailure(@NonNull Call call, @NonNull IOException e) {
        done = true;
        notifyAll();
    }
}
