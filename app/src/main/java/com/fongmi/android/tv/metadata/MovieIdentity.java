package com.fongmi.android.tv.metadata;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Vod;

import java.util.Locale;

public record MovieIdentity(String sourceInstanceId, String sourceVodId, String title, String year,
                            String area, String type, String director, String actor) {

    public static MovieIdentity from(String sourceInstanceId, String sourceVodId, Vod vod) {
        return new MovieIdentity(sourceInstanceId, sourceVodId, vod.getName(), vod.getYear(), vod.getArea(),
                vod.getTypeName(), vod.getDirector(), vod.getActor());
    }

    public boolean isValid() {
        return !TextUtils.isEmpty(sourceInstanceId) && !TextUtils.isEmpty(sourceVodId) && !TextUtils.isEmpty(title);
    }

    public String mappingKey(MetadataProvider provider) {
        return "metadata_link_" + provider.name().toLowerCase(Locale.ROOT) + "_" + sourceInstanceId + "_" + sourceVodId;
    }
}
