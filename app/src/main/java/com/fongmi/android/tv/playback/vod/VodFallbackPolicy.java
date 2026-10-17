package com.fongmi.android.tv.playback.vod;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.github.catvod.utils.Trans;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

class VodFallbackPolicy {

    private final VodPlaybackController controller;
    private final VodPlaybackState state;
    private final VodPlaybackHost host;
    private final VodDataSource dataSource;

    VodFallbackPolicy(VodPlaybackController controller, VodPlaybackState state, VodPlaybackHost host, VodDataSource dataSource) {
        this.controller = controller;
        this.state = state;
        this.host = host;
        this.dataSource = dataSource;
    }

    void playbackError() {
        // A source can expose several lines, and the user may have selected that
        // source manually (which clears autoFallback).  A fatal playback error
        // must still walk the remaining sources after its last line fails.
        fallbackToNextLineOrSource(true);
    }

    void emptyFlag() {
        fallbackToNextLineOrSource(true);
    }

    void emptyDetail() {
        fallbackToNextSource(false);
    }

    void manualSwitchSource() {
        fallbackToNextSource(true);
    }

    void search(String keyword, boolean autoFallback) {
        state.setAutoFallback(autoFallback);
        state.setSelectFirstSource(autoFallback);
        startSearch(keyword);
    }

    void ensureSourcesLoaded() {
        if (!host.discoverSourcesOnDetail() || host.getVodName().trim().isEmpty()) return;
        if (state.getSourceResult().status() == VodSourceResult.Status.IDLE) startSearch(host.getVodName());
        else renderSources();
    }

    void retrySources() {
        VodSourceResult result = state.getSourceResult();
        if (result.isLoading()) return;
        // A retry discovers alternatives; it must not silently switch playback.
        if (result.failed() > 0) {
            // Only the sites that failed are searched again; sources already
            // found stay in the rail instead of restarting the whole discovery.
            state.setAutoFallback(false);
            state.setSelectFirstSource(false);
            dataSource.retryFailedSources(result.generation());
            return;
        }
        search(state.getSearchKeyword().isEmpty() ? host.getVodName() : state.getSearchKeyword(), false);
    }

    /** Detail discovery starts with one batch; this searches every remaining site. */
    void loadAllSources() {
        VodSourceResult result = state.getSourceResult();
        if (result.hasMore()) dataSource.loadAllSources(result.generation());
    }

    private void startSearch(String keyword) {
        keyword = keyword.trim();
        if (keyword.isEmpty()) return;
        List<Site> sites = getSearchableSites();
        long generation = state.beginSourceSearch(keyword, sites.size());
        host.onSearchStarted(keyword);
        renderSources();
        dataSource.searchSources(generation, sites, keyword, host.discoverSourcesOnDetail());
    }

    void onSearchResult(VodSourceResult result) {
        if (!state.accepts(result) || host.isHostFinishing()) return;
        List<Vod> items = new ArrayList<>(result.items());
        String keyword = normalize(result.keyword());
        items.removeIf(item -> item.getId().isEmpty() || !normalize(item.getName()).contains(keyword));
        state.setSourceResult(result.withItems(items));
        boolean hasAlternatives = items.stream().anyMatch(item -> !isCurrent(item));
        if (!state.isSelectFirstSource() || !nextSource()) renderSources();
        if (hasAlternatives) host.onSearchResult();
        // A pending manual/error switch must not stall at the first lazy batch.
        // Plain detail discovery stops here until the viewer reaches the rail.
        if (state.isSelectFirstSource()) loadAllSources();
    }

    void renderSources() {
        List<Vod> items = new ArrayList<>(state.getSources());
        items.removeIf(this::isCurrent);
        // Substring matches also return commentaries/related titles; keep the
        // exact title first. List.sort is stable, so arrival order is kept within each group.
        String name = normalize(host.getVodName());
        items.sort(Comparator.comparing(item -> !normalize(item.getName()).equals(name)));
        host.renderSourceSearch(state.getSourceResult().withItems(items));
    }

    private void fallbackToNextLineOrSource(boolean allowSourceFallback) {
        // The changeable flag controls cross-source fallback, not trying the
        // remaining lines that already belong to the active source.
        if (fallbackToNextLine()) return;
        if (!host.isSiteChangeable()) return;
        fallbackToNextSource(allowSourceFallback);
    }

    private boolean fallbackToNextLine() {
        int position = state.getFlagPosition() + 1;
        while (position < state.getFlags().size()) {
            Flag flag = state.getFlags().get(position++);
            // Some providers return an empty line label alongside playable
            // lines.  Do not stop the fallback chain on that placeholder.
            if (flag.getEpisodes().isEmpty()) continue;
            host.showSwitchLine(flag);
            controller.selectFlag(flag);
            return true;
        }
        return false;
    }

    private void fallbackToNextSource(boolean force) {
        if (state.hasSources() && !state.isAutoFallback() && !force) return;
        state.setAutoFallback(true);
        state.setSelectFirstSource(true);
        if (nextSource()) return;
        // Upgrade an in-flight discovery to a pending switch without cancelling
        // its requests or discarding results that have already arrived.
        if (state.getSourceResult().hasMore()) {
            loadAllSources();
            return;
        }
        if (state.getSourceResult().isLoading()) return;
        startSearch(host.getVodName());
    }

    private boolean nextSource() {
        for (Vod item : state.getSources()) {
            if (isCurrent(item) || state.hasFailedId(item)) continue;
            if (!VodConfig.get().getSite(item.getSiteKey()).isChangeable()) continue;
            // Display can include related matches, but automatic playback must
            // not jump to a different season/remake sharing a title substring.
            if (!normalize(item.getName()).equals(normalize(host.getVodName()))) continue;
            host.showSwitchSource(item);
            state.addFailedId(host.getVodKey(), host.getVodId());
            state.setSelectFirstSource(false);
            controller.fallbackSource(item);
            renderSources();
            return true;
        }
        return false;
    }

    private List<Site> getSearchableSites() {
        List<Site> sites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) {
            if (site.isSearchable() && site.isQuickSearch()) sites.add(site);
        }
        return sites;
    }

    private boolean isCurrent(Vod item) {
        return host.getVodKey().equals(item.getSiteKey()) && host.getVodId().equals(item.getId());
    }

    private static String normalize(String name) {
        return Trans.t2s(name).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
