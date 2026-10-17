package com.fongmi.android.tv.playback.vod;

import com.fongmi.android.tv.bean.Site;

import java.util.List;

public interface VodDataSource {

    void detailContent(String key, String id);

    void playerContent(VodPlayRequest request);

    void preloadContent(VodPlayRequest request);

    void searchSources(long generation, List<Site> sites, String keyword);

    void searchSources(long generation, List<Site> sites, String keyword, boolean incremental);

    void loadAllSources(long generation);

    void retryFailedSources(long generation);

    void cancelSourceSearch();
}
