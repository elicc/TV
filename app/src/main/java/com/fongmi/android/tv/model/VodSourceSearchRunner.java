package com.fongmi.android.tv.model;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.playback.vod.VodSourceResult;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.utils.Trans;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Main-thread-owned queue. Discovery never occupies all playback IO workers. */
final class VodSourceSearchRunner {
    private static final int MAX_IN_FLIGHT = 3;
    private final List<Future<?>> futures = new ArrayList<>();
    private final Queue<Site> pending = new ArrayDeque<>();
    private final Map<String, Vod> items = new LinkedHashMap<>();
    private long epoch;
    private long generation;
    private String keyword;
    private Consumer<VodSourceResult> output;
    private int total, completed, failed, running, requested;

    void start(long generation, List<Site> sites, String keyword, Consumer<VodSourceResult> output) {
        start(generation, sites, keyword, false, output);
    }

    void start(long generation, List<Site> sites, String keyword, boolean incremental,
               Consumer<VodSourceResult> output) {
        stop();
        this.generation = generation;
        this.keyword = keyword;
        this.output = output;
        long request = epoch;
        pending.addAll(sites);
        total = sites.size();
        requested = incremental ? Math.min(MAX_IN_FLIGHT, total) : total;
        publish(generation, keyword, output);
        pump(request, generation, keyword, output);
    }

    void loadMore(long generation) {
        if (this.generation != generation || output == null || completed < requested || requested >= total) return;
        long request = epoch;
        requested = Math.min(requested + MAX_IN_FLIGHT, total);
        publish(generation, keyword, output);
        pump(request, generation, keyword, output);
    }

    void stop() {
        epoch++;
        for (Future<?> future : futures) future.cancel(true);
        futures.clear();
        pending.clear();
        items.clear();
        output = null;
        total = completed = failed = running = requested = 0;
    }

    private void pump(long request, long generation, String keyword, Consumer<VodSourceResult> output) {
        while (request == epoch && running < MAX_IN_FLIGHT && completed + running < requested && !pending.isEmpty()) {
            Site site = pending.remove();
            running++;
            FluentFuture<Result> future = FluentFuture.from(Task.largeExecutor().submit(
                    () -> SiteApi.searchContent(site, Trans.t2s(keyword), true, "1")))
                    .withTimeout(Constant.TIMEOUT_SEARCH, TimeUnit.MILLISECONDS, Task.scheduler());
            futures.add(future);
            future.addCallback(Task.callback(
                    result -> App.post(() -> accept(request, generation, keyword, result, false, output)),
                    error -> App.post(() -> accept(request, generation, keyword, null, true, output))),
                    MoreExecutors.directExecutor());
        }
    }

    private void accept(long request, long generation, String keyword, Result result, boolean error,
                        Consumer<VodSourceResult> output) {
        // Check after posting to main, not just before it: old responses must not
        // enter a new film/search session while the main thread was busy.
        if (request != epoch) return;
        running--;
        completed++;
        if (error || result == null || !result.getMsg().isEmpty() && result.getList().isEmpty()) failed++;
        else for (Vod item : result.getList()) items.put(VodSourceResult.identity(item), item);
        publish(generation, keyword, output);
        pump(request, generation, keyword, output);
    }

    private void publish(long generation, String keyword, Consumer<VodSourceResult> output) {
        output.accept(new VodSourceResult(generation, keyword, new ArrayList<>(items.values()), total, completed, failed, requested));
    }
}
