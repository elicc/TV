package com.fongmi.android.tv.setting;

import android.text.TextUtils;

import com.github.catvod.utils.Prefers;

public final class MetadataAgentSetting {

    private static final String URL = "metadata_agent_url";
    private static final String API_KEY = "metadata_agent_api_key";

    private MetadataAgentSetting() {
    }

    public static String getUrl() {
        return normalize(Prefers.getString(URL, ""));
    }

    public static void putUrl(String value) {
        Prefers.put(URL, normalize(value));
    }

    public static String getApiKey() {
        return Prefers.getString(API_KEY, "").trim();
    }

    public static void putApiKey(String value) {
        Prefers.put(API_KEY, value == null ? "" : value.trim());
    }

    public static boolean isConfigured() {
        String value = getUrl();
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static String normalize(String value) {
        String result = value == null ? "" : value.trim();
        while (!TextUtils.isEmpty(result) && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
