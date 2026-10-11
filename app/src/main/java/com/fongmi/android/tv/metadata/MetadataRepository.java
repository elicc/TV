package com.fongmi.android.tv.metadata;

import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.utils.Prefers;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Coordinates source metadata, external providers, matching and local caching. */
public class MetadataRepository {
    private static final String TAG = "MetadataRepository";
    private static final long MEMORY_TTL = 10 * 60 * 1000L;
    private static final long DISK_TTL = 30L * 24 * 60 * 60 * 1000L;
    private static final long CANDIDATE_TTL = 24L * 60 * 60 * 1000L;
    private static final long LIST_TTL = 12L * 60 * 60 * 1000L;
    // New namespace: old snapshots were irreversibly score-sorted.
    private static final String LIST_CACHE_KEY = "metadata_douban_lists_v2";
    private static final int MAX_LIST_CACHE_ENTRIES = 32;
    private static final int MAX_CACHE_ENTRIES = 500;
    private static final MetadataRepository INSTANCE = new MetadataRepository();

    private final DoubanProvider douban = new DoubanProvider();
    private final MetadataAgentClient agent = new MetadataAgentClient();
    private final ConcurrentMap<String, CacheEntry> memory = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CandidateEntry> candidates = new ConcurrentHashMap<>();
    private final Object listLock = new Object();
    private final Map<String, List<ListCallback>> listWaiters = new java.util.HashMap<>();
    private java.util.LinkedHashMap<String, ListSnapshot> lists;

    public static MetadataRepository get() {
        return INSTANCE;
    }

    public record PlaybackIdentity(String id, String type, boolean userVerified) {
        public boolean sameWork(PlaybackIdentity other) {
            return other != null && id.equals(other.id) && type.equals(other.type);
        }
    }

    /** Cache-only. Never use getSelected(), which may expose an unconfirmed preview candidate. */
    public PlaybackIdentity getPlaybackIdentity(MovieIdentity identity) {
        if (identity == null || !identity.isValid()) return null;
        try {
            CachedMetadata saved = App.gson().fromJson(Prefers.getString(identity.mappingKey(MetadataProvider.DOUBAN)), CachedMetadata.class);
            if (saved == null || saved.metadata == null || saved.updatedAt <= 0
                    || System.currentTimeMillis() - saved.updatedAt >= DISK_TTL
                    || !identityFingerprint(identity).equals(saved.fingerprint)
                    || MetadataMatcher.resumeConflict(identity, saved.metadata)) return null;
            MovieMetadata metadata = saved.metadata;
            if (!metadata.getExternalId().matches("[0-9]+") || !saved.externalId.equals(metadata.getExternalId())
                    || !("movie".equals(metadata.getExternalType()) || "tv".equals(metadata.getExternalType()))) return null;
            // New cache entries preserve the evidence decision made with the full source details.
            // Legacy/sparse entries must pass the stronger rule now, not merely have a high title score.
            if (!saved.resumeEligible && !MetadataMatcher.isResumeSafe(identity, metadata, saved.userVerified)) return null;
            return new PlaybackIdentity(metadata.getExternalId(), metadata.getExternalType(), saved.userVerified);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public MetadataState initial(MovieIdentity identity, Vod vod) {
        MovieMetadata source = MovieMetadata.fromSource(vod);
        return MetadataState.loading(identity, source);
    }

    public MetadataState sourceOnly(MovieIdentity identity, Vod vod) {
        MovieMetadata source = MovieMetadata.fromSource(vod);
        EnumMap<MetadataProvider, MovieMetadata> providers = new EnumMap<>(MetadataProvider.class);
        providers.put(MetadataProvider.SOURCE, source);
        return state(identity, source, providers, List.of(), MetadataState.Status.FALLBACK, "source only");
    }

    public void loadCollection(DoubanCollection collection, Consumer<List<MovieMetadata>> success, Consumer<Throwable> failure) {
        loadList("collection:" + collection.getId(), () -> douban.collection(collection, 40), success, failure);
    }

    public void loadRecommendations(MovieMetadata confirmed, Consumer<List<MovieMetadata>> success, Consumer<Throwable> failure) {
        if (confirmed == null || confirmed.getProvider() != MetadataProvider.DOUBAN
                || !("movie".equals(confirmed.getExternalType()) || "tv".equals(confirmed.getExternalType()))
                || !confirmed.getExternalId().matches("[0-9]+")) {
            App.post(() -> failure.accept(new IllegalArgumentException("Missing confirmed Douban identity")));
            return;
        }
        String type = confirmed.getExternalType(), id = confirmed.getExternalId();
        loadList("related:" + type + ":" + id, () -> douban.recommendations(type, id, 20), success, failure);
    }

    private void loadList(String key, ListLoader loader, Consumer<List<MovieMetadata>> success, Consumer<Throwable> failure) {
        synchronized (listLock) {
            readLists();
            ListSnapshot cached = lists.get(key);
            if (cached != null && !cached.expired()) {
                List<MovieMetadata> result = List.copyOf(cached.items);
                App.post(() -> success.accept(result));
                return;
            }
            List<ListCallback> waiters = listWaiters.get(key);
            if (waiters != null) {
                waiters.add(new ListCallback(success, failure));
                return;
            }
            waiters = new ArrayList<>();
            waiters.add(new ListCallback(success, failure));
            listWaiters.put(key, waiters);
        }
        Task.execute(() -> {
            List<MovieMetadata> result = null;
            Throwable failureReason = null;
            try {
                result = List.copyOf(loader.load());
                synchronized (listLock) {
                    ListSnapshot snapshot = new ListSnapshot();
                    snapshot.updatedAt = System.currentTimeMillis();
                    snapshot.items = result;
                    lists.remove(key);
                    lists.put(key, snapshot);
                    while (lists.size() > MAX_LIST_CACHE_ENTRIES) lists.remove(lists.keySet().iterator().next());
                    Prefers.put(LIST_CACHE_KEY, App.gson().toJson(lists));
                }
            } catch (Exception error) {
                failureReason = error;
                Log.w(TAG, "Douban list unavailable: " + key, error);
                synchronized (listLock) {
                    // Never substitute another category or another movie's recommendations.
                    ListSnapshot stale = lists.get(key);
                    if (stale != null) result = List.copyOf(stale.items);
                }
            }
            List<ListCallback> callbacks;
            synchronized (listLock) { callbacks = listWaiters.remove(key); }
            List<MovieMetadata> delivered = result;
            Throwable deliveredError = failureReason;
            App.post(() -> {
                for (ListCallback callback : callbacks) {
                    if (delivered != null) callback.success.accept(delivered);
                    else callback.failure.accept(deliveredError);
                }
            });
        });
    }

    private void readLists() {
        if (lists != null) return;
        try {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<java.util.LinkedHashMap<String, ListSnapshot>>() {}.getType();
            lists = App.gson().fromJson(Prefers.getString(LIST_CACHE_KEY, ""), type);
            if (lists != null) lists.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().items == null
                    || entry.getValue().items.stream().anyMatch(java.util.Objects::isNull));
        } catch (RuntimeException error) {
            Log.w(TAG, "Discarding invalid Douban list cache", error);
            lists = null;
            Prefers.remove(LIST_CACHE_KEY);
        }
        if (lists == null) lists = new java.util.LinkedHashMap<>();
        while (lists.size() > MAX_LIST_CACHE_ENTRIES) lists.remove(lists.keySet().iterator().next());
    }

    private interface ListLoader { List<MovieMetadata> load() throws IOException; }

    private static final class ListCallback {
        final Consumer<List<MovieMetadata>> success;
        final Consumer<Throwable> failure;
        ListCallback(Consumer<List<MovieMetadata>> success, Consumer<Throwable> failure) {
            this.success = success;
            this.failure = failure;
        }
    }

    private static final class ListSnapshot {
        long updatedAt;
        List<MovieMetadata> items;
        boolean expired() { return updatedAt <= 0 || System.currentTimeMillis() - updatedAt >= LIST_TTL; }
    }

    public void confirm(MovieIdentity identity, Vod vod, MetadataCandidate candidate, Consumer<MetadataState> callback) {
        MovieMetadata source = MovieMetadata.fromSource(vod);
        Task.execute(() -> {
            try {
                if (candidate == null || candidate.getMetadata() == null) {
                    App.post(() -> callback.accept(fallback(identity, source, "candidate rejected")));
                    return;
                }
                MovieMetadata detail = loadConfirmedDetail(identity, candidate.getMetadata());
                detail.setProvider(MetadataProvider.DOUBAN);
                String key = identity.mappingKey(MetadataProvider.DOUBAN);
                save(key, identity, detail, detail.getExternalId(), true);
                EnumMap<MetadataProvider, MovieMetadata> providers = new EnumMap<>(MetadataProvider.class);
                providers.put(MetadataProvider.SOURCE, source);
                providers.put(MetadataProvider.DOUBAN, detail);
                MetadataState state = state(identity, source, providers, List.of(candidate), MetadataState.Status.SUCCESS, "user confirmed");
                App.post(() -> callback.accept(state));
            } catch (Throwable error) {
                Log.w(TAG, "confirm failed for " + identity.title(), error);
                App.post(() -> callback.accept(fallback(identity, source, error.getMessage())));
            }
        });
    }

    public void load(MovieIdentity identity, Vod vod, Consumer<MetadataState> callback) {
        MovieMetadata source = MovieMetadata.fromSource(vod);
        MetadataState loading = MetadataState.loading(identity, source);
        callback.accept(loading);
        Task.execute(() -> {
            try {
                MetadataState state = resolve(identity, source, true);
                App.post(() -> callback.accept(state));
            } catch (Throwable error) {
                Log.w(TAG, "load failed for " + identity.title(), error);
                App.post(() -> callback.accept(fallback(identity, source, error.getMessage())));
            }
        });
    }

    /** Loads optional cast credits after a trusted provider match. */
    public void loadCast(MovieIdentity identity, MovieMetadata metadata, Consumer<List<MovieCast>> callback) {
        if (identity == null || metadata == null || metadata.getProvider() != MetadataProvider.DOUBAN
                || !identity.isValid() || metadata.getExternalId().isEmpty()) {
            App.post(() -> callback.accept(List.of()));
            return;
        }
        Task.execute(() -> {
            List<MovieCast> result = metadata.getCast();
            try {
                if (!metadata.isCastLoaded()) {
                    boolean loaded = false;
                    if (agent.isConfigured()) {
                        try {
                            result = agent.cast(metadata.getExternalId(), isTv(identity, metadata));
                            loaded = true;
                        } catch (Throwable error) {
                            Log.d(TAG, "remote cast unavailable for " + identity.title() + ", using local provider", error);
                        }
                    }
                    if (!loaded) {
                        douban.enrichCast(metadata, isTv(identity, metadata));
                        result = metadata.getCast();
                    } else {
                        metadata.setCast(result);
                        metadata.markCastLoaded();
                    }
                    save(identity.mappingKey(MetadataProvider.DOUBAN), identity, metadata, metadata.getExternalId(), false);
                }
            } catch (Throwable error) {
                Log.d(TAG, "cast unavailable for " + identity.title(), error);
            }
            List<MovieCast> cast = result == null ? List.of() : List.copyOf(result);
            App.post(() -> callback.accept(cast));
        });
    }

    /**
     * Resolves only the artwork needed by the TV home backdrop. This deliberately
     * reuses the same matching/caching policy as the detail screen: low-confidence
     * candidates never leak their images into the home screen.
     */
    public Future<?> loadArtwork(MovieIdentity identity, Vod vod, Consumer<MovieMetadata> callback) {
        if (identity == null || vod == null || !identity.isValid()) {
            App.post(() -> callback.accept(null));
            return null;
        }
        return Task.submit(() -> {
            MovieMetadata result = null;
            try {
                MetadataState state = resolve(identity, MovieMetadata.fromSource(vod), false);
                if (state.getStatus() == MetadataState.Status.SUCCESS
                        && state.getSelectedProvider() != MetadataProvider.SOURCE) {
                    result = state.getSelected();
                    if (result != null && (!result.hasBackdrop() || !result.hasArtworks())) {
                        douban.enrichArtwork(result, isTv(identity, result));
                        if (result.hasBackdrop() || result.hasArtworks()) save(identity.mappingKey(MetadataProvider.DOUBAN), identity,
                                result, result.getExternalId(), false);
                    }
                }
            } catch (Throwable error) {
                Log.d(TAG, "artwork unavailable for " + identity.title(), error);
            }
            MovieMetadata artwork = result;
            App.post(() -> callback.accept(artwork));
        });
    }

    private MetadataState resolve(MovieIdentity identity, MovieMetadata source, boolean allowAgent) throws Exception {
        EnumMap<MetadataProvider, MovieMetadata> providers = new EnumMap<>(MetadataProvider.class);
        providers.put(MetadataProvider.SOURCE, source);
        if (identity == null || !identity.isValid()) return state(identity, source, providers, List.of(), MetadataState.Status.FALLBACK, "invalid identity");

        String mappingKey = identity.mappingKey(MetadataProvider.DOUBAN);
        CacheEntry cached = memory.get(mappingKey);
        if (cached != null && !cached.expired(MEMORY_TTL)) {
            MovieMetadata metadata = cached.metadata;
            enrichCachedArtwork(identity, metadata);
            providers.put(MetadataProvider.DOUBAN, metadata);
            return state(identity, source, providers, List.of(), MetadataState.Status.SUCCESS, "memory");
        }

        String persisted = Prefers.getString(mappingKey, "");
        if (!persisted.isEmpty()) {
            CachedMetadata saved = App.gson().fromJson(persisted, CachedMetadata.class);
            if (saved != null && saved.updatedAt > 0 && System.currentTimeMillis() - saved.updatedAt < DISK_TTL
                    && mappingMatches(identity, saved) && !saved.externalId.isEmpty()) {
                MovieMetadata metadata = saved.metadata;
                if (metadata == null) metadata = douban.detail(saved.externalId, isTv(identity));
                metadata.setProvider(MetadataProvider.DOUBAN);
                enrichCachedArtwork(identity, metadata);
                // Migrate older fingerprints that included optional detail fields.
                // Home history projections intentionally omit those fields, so the
                // stable title-only fingerprint is shared by both screens.
                if (!identityFingerprint(identity).equals(saved.fingerprint)) {
                    save(mappingKey, identity, metadata, metadata.getExternalId(), saved.userVerified);
                } else {
                    putBounded(memory, mappingKey, new CacheEntry(metadata, System.currentTimeMillis()));
                }
                providers.put(MetadataProvider.DOUBAN, metadata);
                return state(identity, source, providers, List.of(), MetadataState.Status.SUCCESS, "disk");
            }
            Prefers.remove(mappingKey);
        }

        MetadataState remote = resolveRemote(identity, source, allowAgent);
        if (remote != null) return remote;

        String candidateKey = "metadata_candidates_douban_" + identity.sourceInstanceId() + "_" + identity.sourceVodId();
        CandidateEntry candidateEntry = candidates.get(candidateKey);
        List<MetadataCandidate> ranked = candidateEntry != null && !candidateEntry.expired(CANDIDATE_TTL)
                ? candidateEntry.items : search(identity);
        putBounded(candidates, candidateKey, new CandidateEntry(ranked, System.currentTimeMillis()));
        if (ranked.isEmpty()) return state(identity, source, providers, ranked, MetadataState.Status.FALLBACK, "no match");

        double best = ranked.get(0).getConfidence();
        double second = ranked.size() > 1 ? ranked.get(1).getConfidence() : 0d;
        Log.d(TAG, "candidates title=" + identity.title() + " best=" + best + " second=" + second);
        if (!MetadataMatcher.isAutomatic(best, second)) {
            return state(identity, source, providers, ranked, MetadataState.Status.FALLBACK, "confirmation required");
        }

        MovieMetadata selected = ranked.get(0).getMetadata();
        MovieMetadata detail = douban.detail(selected.getExternalId(), isTv(identity, selected));
        detail.setProvider(MetadataProvider.DOUBAN);
        save(mappingKey, identity, detail, detail.getExternalId(), false);
        providers.put(MetadataProvider.DOUBAN, detail);
        return state(identity, source, providers, ranked, MetadataState.Status.SUCCESS, "auto matched");
    }

    private MetadataState resolveRemote(MovieIdentity identity, MovieMetadata source, boolean allowAgent) {
        if (!agent.isConfigured()) return null;
        try {
            MetadataAgentClient.Resolution result = agent.resolve(identity, allowAgent);
            if (result.isMatched()) {
                MovieMetadata selected = result.getSelected();
                save(identity.mappingKey(MetadataProvider.DOUBAN), identity, selected, selected.getExternalId(), false);
                EnumMap<MetadataProvider, MovieMetadata> providers = new EnumMap<>(MetadataProvider.class);
                providers.put(MetadataProvider.SOURCE, source);
                providers.put(MetadataProvider.DOUBAN, selected);
                return state(identity, source, providers, result.getCandidates(), MetadataState.Status.SUCCESS, "remote matched");
            }
            if (result.needsConfirmation()) {
                EnumMap<MetadataProvider, MovieMetadata> providers = new EnumMap<>(MetadataProvider.class);
                providers.put(MetadataProvider.SOURCE, source);
                return state(identity, source, providers, result.getCandidates(), MetadataState.Status.FALLBACK, result.getMessage());
            }
        } catch (Throwable error) {
            Log.d(TAG, "remote resolver unavailable for " + identity.title() + ", using local provider", error);
        }
        return null;
    }

    private MovieMetadata loadConfirmedDetail(MovieIdentity identity, MovieMetadata candidate) throws Exception {
        if (agent.isConfigured()) {
            try {
                return agent.detail(candidate.getExternalId(), isTv(identity, candidate));
            } catch (Throwable error) {
                Log.d(TAG, "remote detail unavailable for " + identity.title() + ", using local provider", error);
            }
        }
        return douban.detail(candidate.getExternalId(), isTv(identity, candidate));
    }

    private List<MetadataCandidate> search(MovieIdentity identity) throws Exception {
        List<MovieMetadata> results = douban.search(identity);
        List<MetadataCandidate> ranked = new ArrayList<>();
        for (MovieMetadata metadata : results) {
            double score = MetadataMatcher.score(identity, metadata);
            if (MetadataMatcher.isCandidate(score)) ranked.add(new MetadataCandidate(metadata, score));
        }
        ranked.sort(Comparator.comparingDouble(MetadataCandidate::getConfidence).reversed());
        return ranked;
    }

    private void save(String key, MovieIdentity identity, MovieMetadata metadata, String externalId, boolean userVerified) {
        CachedMetadata saved = new CachedMetadata();
        saved.externalId = externalId;
        saved.fingerprint = identityFingerprint(identity);
        saved.updatedAt = System.currentTimeMillis();
        // Artwork enrichment rewrites the same cache record. Never let that
        // optional refresh downgrade an explicit viewer confirmation.
        saved.userVerified = userVerified || isUserVerified(key, externalId);
        PlaybackIdentity previous = getPlaybackIdentity(identity);
        saved.resumeEligible = MetadataMatcher.isResumeSafe(identity, metadata, saved.userVerified)
                || (previous != null && previous.id().equals(externalId) && previous.type().equals(metadata.getExternalType()));
        saved.metadata = metadata;
        Prefers.put(key, App.gson().toJson(saved));
        putBounded(memory, key, new CacheEntry(metadata, saved.updatedAt));
    }

    /** Backfills photos for metadata written before the artwork gallery existed. */
    private void enrichCachedArtwork(MovieIdentity identity, MovieMetadata metadata) {
        if (metadata == null || metadata.getProvider() != MetadataProvider.DOUBAN
                || (metadata.hasBackdrop() && metadata.hasArtworks())) return;
        try {
            douban.enrichArtwork(metadata, isTv(identity, metadata));
            if (metadata.hasBackdrop() || metadata.hasArtworks()) {
                save(identity.mappingKey(MetadataProvider.DOUBAN), identity, metadata, metadata.getExternalId(), false);
            }
        } catch (IOException ignored) {
            // Artwork is optional; retain the already trusted title metadata.
        }
    }

    private static <T> void putBounded(ConcurrentMap<String, T> map, String key, T value) {
        if (map.size() >= MAX_CACHE_ENTRIES) {
            String first = map.keySet().stream().findFirst().orElse(null);
            if (first != null) map.remove(first);
        }
        map.put(key, value);
    }

    private static MetadataState fallback(MovieIdentity identity, MovieMetadata source, String message) {
        EnumMap<MetadataProvider, MovieMetadata> providers = new EnumMap<>(MetadataProvider.class);
        providers.put(MetadataProvider.SOURCE, source);
        return state(identity, source, providers, List.of(), MetadataState.Status.ERROR, message == null ? "metadata unavailable" : message);
    }

    private static MetadataState state(MovieIdentity identity, MovieMetadata source, Map<MetadataProvider, MovieMetadata> providers,
                                       List<MetadataCandidate> candidates, MetadataState.Status status, String message) {
        MetadataProvider selected = status == MetadataState.Status.SUCCESS && providers.containsKey(MetadataProvider.DOUBAN)
                ? MetadataProvider.DOUBAN : MetadataProvider.SOURCE;
        return new MetadataState(identity, source, providers, candidates, selected, status, message);
    }

    private static boolean isTv(MovieIdentity identity) {
        String type = (identity.type() + " " + identity.title()).toLowerCase(Locale.ROOT);
        boolean series = type.contains("电视剧") || type.contains("连续剧") || type.contains("剧集")
                || (type.contains("剧") && !type.contains("剧情"));
        return type.contains("tv") || series || type.contains("综艺") || type.contains("番");
    }

    private static boolean isTv(MovieIdentity identity, MovieMetadata metadata) {
        if (metadata != null) {
            if ("tv".equals(metadata.getExternalType())) return true;
            if ("movie".equals(metadata.getExternalType())) return false;
        }
        return isTv(identity);
    }

    private static String identityFingerprint(MovieIdentity identity) {
        // sourceInstanceId + sourceVodId already scope the mapping key. Only the
        // normalized lookup title is stable across the rich detail object and
        // the intentionally sparse history projection used by the TV home.
        String plain = MetadataMatcher.normalize(MetadataMatcher.queryTitle(identity.title()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : bytes) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception ignored) {
            return plain;
        }
    }

    private static boolean mappingMatches(MovieIdentity identity, CachedMetadata saved) {
        if (identityFingerprint(identity).equals(saved.fingerprint)) return true;
        // A manual choice is authoritative for this source + VOD id. Optional
        // source fields may legitimately be absent when the home screen loads it.
        if (saved.userVerified) return true;
        if (saved.metadata == null || MetadataMatcher.hardConflict(identity, saved.metadata)) return false;
        String sourceTitle = MetadataMatcher.normalize(MetadataMatcher.queryTitle(identity.title()));
        String savedTitle = MetadataMatcher.normalize(saved.metadata.getTitle());
        return !sourceTitle.isEmpty() && sourceTitle.equals(savedTitle);
    }

    private static boolean isUserVerified(String key, String externalId) {
        try {
            String persisted = Prefers.getString(key, "");
            if (persisted.isEmpty()) return false;
            CachedMetadata saved = App.gson().fromJson(persisted, CachedMetadata.class);
            return saved != null && saved.userVerified && externalId.equals(saved.externalId);
        } catch (RuntimeException ignored) {
            return false;
        }
    }


    private static class CandidateEntry {
        private final List<MetadataCandidate> items;
        private final long updatedAt;

        private CandidateEntry(List<MetadataCandidate> items, long updatedAt) {
            this.items = items == null ? List.of() : List.copyOf(items);
            this.updatedAt = updatedAt;
        }

        private boolean expired(long ttl) {
            return System.currentTimeMillis() - updatedAt >= ttl;
        }
    }
    private static class CacheEntry {
        private final MovieMetadata metadata;
        private final long updatedAt;

        private CacheEntry(MovieMetadata metadata, long updatedAt) {
            this.metadata = metadata;
            this.updatedAt = updatedAt;
        }

        private boolean expired(long ttl) {
            return metadata == null || System.currentTimeMillis() - updatedAt >= ttl;
        }
    }

    private static class CachedMetadata {
        private boolean resumeEligible;
        private String externalId = "";
        private String fingerprint = "";
        private long updatedAt;
        private boolean userVerified;
        private int version = 1;
        private MovieMetadata metadata;
    }
}
