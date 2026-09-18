package com.fongmi.android.tv.model;

import android.os.SystemClock;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.playback.PlaybackResult;
import com.fongmi.android.tv.playback.vod.VodDataSource;
import com.fongmi.android.tv.playback.vod.VodDetailResult;
import com.fongmi.android.tv.playback.vod.VodPlayRequest;
import com.fongmi.android.tv.playback.vod.VodPlaybackController;
import com.fongmi.android.tv.playback.vod.VodPlaybackHost;
import com.fongmi.android.tv.playback.vod.VodPlaybackState;
import com.fongmi.android.tv.metadata.MetadataProvider;
import com.fongmi.android.tv.metadata.MetadataRepository;
import com.fongmi.android.tv.metadata.MetadataState;
import com.fongmi.android.tv.metadata.MovieIdentity;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.Task;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentMap;

public class VideoViewModel extends SiteViewModel implements VodDataSource {

    private static final String TRACE_TAG = "PlaybackTrace";
    private static final long DETAIL_CACHE_TTL_MS = 60_000L;
    private static final ConcurrentMap<String, CachedDetail> DETAIL_CACHE = new ConcurrentHashMap<>();

    private final MutableLiveData<VodDetailResult> detail;
    private final MutableLiveData<PlaybackResult<VodPlayRequest>> preload;
    private final MutableLiveData<PlaybackResult<VodPlayRequest>> playback;
    private final ViewModelTaskRunner<TaskType> requestTasks;
    private final VodPlaybackState playbackState;
    private final MutableLiveData<MetadataState> metadata;
    private final AtomicLong metadataGeneration;
    private final MetadataRepository metadataRepository;
    private Vod metadataVod;

    public VideoViewModel() {
        detail = new MutableLiveData<>();
        preload = new MutableLiveData<>();
        playback = new MutableLiveData<>();
        requestTasks = new ViewModelTaskRunner<>(TaskType.class);
        playbackState = new VodPlaybackState();
        metadata = new MutableLiveData<>();
        metadataGeneration = new AtomicLong();
        metadataRepository = MetadataRepository.get();
    }

    public LiveData<VodDetailResult> getDetail() {
        return detail;
    }

    public LiveData<PlaybackResult<VodPlayRequest>> getPreload() {
        return preload;
    }

    public LiveData<PlaybackResult<VodPlayRequest>> getPlayback() {
        return playback;
    }

    public LiveData<MetadataState> getMetadata() {
        return metadata;
    }

    public void loadMetadata(String sourceKey, String sourceId, Vod vod) {
        long generation = metadataGeneration.incrementAndGet();
        metadataVod = vod;
        MovieIdentity identity = MovieIdentity.from(sourceKey, sourceId, vod);
        if (SiteApi.PUSH.equals(sourceKey)) {
            metadata.postValue(metadataRepository.sourceOnly(identity, vod));
            return;
        }
        metadataRepository.load(identity, vod, state -> {
            if (generation == metadataGeneration.get()) metadata.postValue(state);
        });
    }

    public void selectMetadataProvider(MetadataProvider provider) {
        MetadataState state = metadata.getValue();
        if (state != null) metadata.postValue(state.select(provider));
    }

    public void confirmMetadata(com.fongmi.android.tv.metadata.MetadataCandidate candidate) {
        MetadataState current = metadata.getValue();
        if (current == null || metadataVod == null || current.getIdentity() == null) return;
        long generation = metadataGeneration.incrementAndGet();
        metadata.postValue(MetadataState.loading(current.getIdentity(), current.getSource()));
        metadataRepository.confirm(current.getIdentity(), metadataVod, candidate, state -> {
            if (generation == metadataGeneration.get()) metadata.postValue(state);
        });
    }

    public VodPlaybackController createPlaybackController(VodPlaybackHost host) {
        return new VodPlaybackController(host, this, playbackState);
    }

    @Override
    public void detailContent(String key, String id) {
        String cacheKey = cacheKey(key, id);
        CachedDetail cached = DETAIL_CACHE.get(cacheKey);
        long now = SystemClock.uptimeMillis();
        if (cached != null && now - cached.createdAt < DETAIL_CACHE_TTL_MS) {
            trace("DETAIL_CACHE_HIT age=" + (now - cached.createdAt));
            detail.postValue(new VodDetailResult(key, id, Result.objectFrom(cached.json)));
            return;
        }
        requestTasks.execute(
                TaskType.DETAIL,
                Constant.TIMEOUT_VOD,
                () -> {
                    long started = SystemClock.uptimeMillis();
                    trace("DETAIL_IO_BEGIN key=" + key + " id=" + id);
                    Result detailResult = SiteApi.detailContent(key, id);
                    cacheDetail(cacheKey, detailResult);
                    VodDetailResult result = new VodDetailResult(key, id, detailResult);
                    trace("DETAIL_IO_END dur=" + (SystemClock.uptimeMillis() - started));
                    return result;
                },
                detail::postValue,
                error -> detail.postValue(new VodDetailResult(key, id, handleError(error))));
    }

    @Override
    public void playerContent(VodPlayRequest request) {
        loadPlayback(request, TaskType.PLAYBACK, playback);
    }

    @Override
    public void preloadContent(VodPlayRequest request) {
        loadPlayback(request, TaskType.PRELOAD, preload);
    }

    private void loadPlayback(VodPlayRequest request, TaskType type, MutableLiveData<PlaybackResult<VodPlayRequest>> output) {
        requestTasks.execute(
                type,
                Constant.TIMEOUT_VOD,
                () -> {
                    long started = SystemClock.uptimeMillis();
                    trace("PLAY_IO_BEGIN key=" + request.getKey() + " flag=" + request.getFlag());
                    PlaybackResult<VodPlayRequest> result = new PlaybackResult<>(request, SiteApi.playerContent(request.getKey(), request.getFlag(), request.getId()));
                    trace("PLAY_IO_END dur=" + (SystemClock.uptimeMillis() - started));
                    return result;
                },
                output::postValue,
                error -> output.postValue(new PlaybackResult<>(request, handleError(error))));
    }

    private static void trace(String event) {
        if (BuildConfig.DEBUG) Log.d(TRACE_TAG, event + " t=" + SystemClock.uptimeMillis());
    }

    /** Speculatively warms the detail cache for an entry the user is likely to open. */
    public static void prefetchDetail(String key, String id) {
        if (key == null || key.isEmpty() || id == null || id.isEmpty()) return;
        String cacheKey = cacheKey(key, id);
        CachedDetail cached = DETAIL_CACHE.get(cacheKey);
        if (cached != null && SystemClock.uptimeMillis() - cached.createdAt < DETAIL_CACHE_TTL_MS) return;
        Task.execute(() -> {
            long started = SystemClock.uptimeMillis();
            trace("DETAIL_PREFETCH_BEGIN key=" + key + " id=" + id);
            try {
                cacheDetail(cacheKey, SiteApi.detailContent(key, id));
            } catch (Throwable ignored) {
                // A failed prefetch must never surface; the real request retries.
            }
            trace("DETAIL_PREFETCH_END key=" + key + " dur=" + (SystemClock.uptimeMillis() - started));
        });
    }

    private static String cacheKey(String key, String id) {
        return key + "\u0000" + id;
    }

    private static void cacheDetail(String key, Result result) {
        if (result == null || result.getList().isEmpty()) return;
        try {
            DETAIL_CACHE.put(key, new CachedDetail(App.gson().toJson(result), SystemClock.uptimeMillis()));
        } catch (Exception ignored) {
            // A malformed provider response must never break the playback request.
        }
    }

    private Result handleError(Throwable error) {
        error.printStackTrace();
        return error instanceof ExtractException ? Result.error(error.getMessage()) : Result.empty();
    }

    @Override
    protected void onCleared() {
        requestTasks.cancelAll();
        metadataGeneration.incrementAndGet();
        playbackState.reset();
        super.onCleared();
    }

    private record CachedDetail(String json, long createdAt) {
    }

    private enum TaskType {DETAIL, PLAYBACK, PRELOAD}
}
