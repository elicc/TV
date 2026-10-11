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
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.playback.PlaybackResult;
import com.fongmi.android.tv.playback.vod.VodDataSource;
import com.fongmi.android.tv.playback.vod.VodDetailResult;
import com.fongmi.android.tv.playback.vod.VodPlayRequest;
import com.fongmi.android.tv.playback.vod.VodPlaybackController;
import com.fongmi.android.tv.playback.vod.VodPlaybackHost;
import com.fongmi.android.tv.playback.vod.VodPlaybackState;
import com.fongmi.android.tv.playback.vod.VodSourceResult;
import com.fongmi.android.tv.metadata.MetadataProvider;
import com.fongmi.android.tv.metadata.MetadataRepository;
import com.fongmi.android.tv.metadata.MetadataState;
import com.fongmi.android.tv.metadata.MovieCast;
import com.fongmi.android.tv.metadata.RecommendationState;
import com.fongmi.android.tv.metadata.MovieIdentity;
import com.fongmi.android.tv.metadata.MovieMetadata;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.Task;

import java.util.List;
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
    private final VodSourceSearchRunner sourceSearches = new VodSourceSearchRunner();
    private final MutableLiveData<VodSourceResult> sources = new MutableLiveData<>();
    private final MutableLiveData<MetadataState> metadata;
    private final MutableLiveData<List<MovieCast>> cast;
    private final AtomicLong metadataGeneration;
    private final AtomicLong castGeneration;
    private final MetadataRepository metadataRepository;
    private Vod metadataVod;
    private final MutableLiveData<RecommendationState> recommendations = new MutableLiveData<>(RecommendationState.idle());
    private String recommendationKey = "";

    public LiveData<RecommendationState> getRecommendations() { return recommendations; }

    public void retryRecommendations() {
        RecommendationState current = recommendations.getValue();
        if (current == null || current.getStatus() != RecommendationState.Status.ERROR) return;
        recommendationKey = "";
        loadRecommendations(metadataGeneration.get(), metadata.getValue());
    }

    private void clearRecommendations() {
        recommendationKey = "";
        recommendations.setValue(RecommendationState.idle());
    }

    private void loadRecommendations(long generation, MetadataState state) {
        if (generation != metadataGeneration.get()) return;
        MovieMetadata resolved = RecommendationState.resolvedMovie(state);
        if (resolved == null) return;
        String key = resolved.getExternalType() + ":" + resolved.getExternalId();
        if (key.equals(recommendationKey)) return;
        recommendationKey = key;
        recommendations.setValue(RecommendationState.loading());
        metadataRepository.loadRecommendations(resolved, items -> {
            if (generation == metadataGeneration.get() && key.equals(recommendationKey)) {
                recommendations.setValue(RecommendationState.success(items));
            }
        }, error -> {
            if (generation == metadataGeneration.get() && key.equals(recommendationKey)) {
                recommendations.setValue(RecommendationState.error());
            }
        });
    }


    public VideoViewModel() {
        detail = new MutableLiveData<>();
        preload = new MutableLiveData<>();
        playback = new MutableLiveData<>();
        requestTasks = new ViewModelTaskRunner<>(TaskType.class);
        playbackState = new VodPlaybackState();
        metadata = new MutableLiveData<>();
        cast = new MutableLiveData<>(List.of());
        metadataGeneration = new AtomicLong();
        castGeneration = new AtomicLong();
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

    public LiveData<List<MovieCast>> getCast() {
        return cast;
    }

    public void clearMetadata() {
        metadataGeneration.incrementAndGet();
        castGeneration.incrementAndGet();
        metadataVod = null;
        metadata.setValue(null);
        cast.setValue(List.of());
        clearRecommendations();
    }

    public void loadMetadata(String sourceKey, String sourceId, Vod vod) {
        long generation = metadataGeneration.incrementAndGet();
        castGeneration.incrementAndGet();
        cast.setValue(List.of());
        clearRecommendations();
        metadataVod = vod;
        MovieIdentity identity = MovieIdentity.from(sourceKey, sourceId, vod);
        if (SiteApi.PUSH.equals(sourceKey)) {
            metadata.setValue(metadataRepository.sourceOnly(identity, vod));
            return;
        }
        metadataRepository.load(identity, vod, state -> {
            if (generation != metadataGeneration.get()) return;
            metadata.setValue(state);
            loadCast(generation, state);
            loadRecommendations(generation, state);
        });
    }

    public void selectMetadataProvider(MetadataProvider provider) {
        MetadataState state = metadata.getValue();
        if (state != null) {
            MetadataState selected = state.select(provider);
            metadata.setValue(selected);
            loadCast(metadataGeneration.get(), selected);
        }
    }

    public void confirmMetadata(com.fongmi.android.tv.metadata.MetadataCandidate candidate) {
        MetadataState current = metadata.getValue();
        if (current == null || metadataVod == null || current.getIdentity() == null) return;
        long generation = metadataGeneration.incrementAndGet();
        castGeneration.incrementAndGet();
        cast.setValue(List.of());
        clearRecommendations();
        metadata.setValue(MetadataState.loading(current.getIdentity(), current.getSource()));
        metadataRepository.confirm(current.getIdentity(), metadataVod, candidate, state -> {
            if (state.getStatus() == MetadataState.Status.SUCCESS
                    && state.getSelectedProvider() != MetadataProvider.SOURCE) {
                RefreshEvent.metadata();
            }
            if (generation == metadataGeneration.get()) metadata.setValue(state);
            if (generation == metadataGeneration.get()) {
                loadCast(generation, state);
                loadRecommendations(generation, state);
            }
        });
    }

    private void loadCast(long generation, MetadataState state) {
        if (generation != metadataGeneration.get()) return;
        // Invalidate even when switching to SOURCE while Douban is in flight.
        long castRequest = castGeneration.incrementAndGet();
        if (state == null || state.getStatus() != MetadataState.Status.SUCCESS
                || state.getSelectedProvider() != MetadataProvider.DOUBAN) {
            cast.setValue(List.of());
            return;
        }
        MovieMetadata selected = state.getSelected();
        if (selected == null) {
            cast.setValue(List.of());
            return;
        }
        metadataRepository.loadCast(state.getIdentity(), selected, result -> {
            if (generation == metadataGeneration.get() && castRequest == castGeneration.get()) {
                // Repository callbacks run on main; do not queue an unchecked
                // postValue that could outlive the provider selection.
                cast.setValue(result == null ? List.of() : List.copyOf(result));
            }
        });
    }

    public VodPlaybackController createPlaybackController(VodPlaybackHost host) {
        return new VodPlaybackController(host, this, playbackState);
    }

    public LiveData<VodSourceResult> getSources() {
        return sources;
    }

    @Override
    public void searchSources(long generation, List<Site> sites, String keyword) {
        sourceSearches.start(generation, sites, keyword, sources::setValue);
    }

    @Override
    public void searchSources(long generation, List<Site> sites, String keyword, boolean incremental) {
        sourceSearches.start(generation, sites, keyword, incremental, sources::setValue);
    }

    @Override
    public void loadMoreSources(long generation) {
        sourceSearches.loadMore(generation);
    }

    @Override
    public void cancelSourceSearch() {
        sourceSearches.stop();
        sources.setValue(null);
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
                error -> detail.postValue(new VodDetailResult(key, id, handleDetailError(error))));
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
                error -> output.postValue(new PlaybackResult<>(request, handlePlaybackError(error))));
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

    private Result handleDetailError(Throwable error) {
        error.printStackTrace();
        String message = error.getMessage();
        if (message == null || message.isEmpty()) message = error.getClass().getSimpleName();
        return Result.error(message);
    }

    private Result handlePlaybackError(Throwable error) {
        error.printStackTrace();
        return error instanceof ExtractException ? Result.error(error.getMessage()) : Result.empty();
    }

    @Override
    protected void onCleared() {
        sourceSearches.stop();
        requestTasks.cancelAll();
        metadataGeneration.incrementAndGet();
        castGeneration.incrementAndGet();
        recommendationKey = "";
        playbackState.reset();
        super.onCleared();
    }

    private record CachedDetail(String json, long createdAt) {
    }

    private enum TaskType {DETAIL, PLAYBACK, PRELOAD}
}
