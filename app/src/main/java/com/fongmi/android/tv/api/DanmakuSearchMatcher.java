package com.fongmi.android.tv.api;

import com.fongmi.android.tv.bean.Danmaku;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ranks mixed-title search results without changing the server response contract. */
public final class DanmakuSearchMatcher {

    private static final Pattern EPISODE = Pattern.compile("(?i)(?:s\\d{1,2}\\s*e\\s*|第\\s*|ep(?:isode)?\\s*|\\be\\s*)(\\d{1,4})");
    private static final Pattern NUMBER = Pattern.compile("\\d{1,5}");

    private DanmakuSearchMatcher() {
    }

    public static List<Danmaku> rank(List<Danmaku> items, String title, String episode) {
        List<Danmaku> result = items == null ? new ArrayList<>() : new ArrayList<>(items);
        result.sort(Comparator.comparingInt((Danmaku item) -> score(item, title, episode)).reversed());
        return result;
    }

    public static int number(String text) {
        if (text == null || text.isEmpty()) return 0;
        Matcher matcher = EPISODE.matcher(text);
        int value = 0;
        while (matcher.find()) value = Integer.parseInt(matcher.group(1));
        if (value > 0) return value;
        matcher = NUMBER.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group()) : 0;
    }

    /** Changes the episode token, preferring the E02 part of S01E02 over the season. */
    public static String step(String episode, int current, int delta) {
        int base = current > 0 ? current : number(episode);
        int target = Math.max(1, base + delta);
        if (episode == null || episode.isEmpty()) return "第" + target + "集";
        Matcher matcher = EPISODE.matcher(episode);
        int start = -1;
        int end = -1;
        String group = null;
        while (matcher.find()) {
            start = matcher.start(1);
            end = matcher.end(1);
            group = matcher.group(1);
        }
        if (start < 0) {
            matcher = NUMBER.matcher(episode);
            if (matcher.find()) return replace(episode, matcher.start(), matcher.end(), matcher.group(), target);
            return "第" + target + "集";
        }
        return replace(episode, start, end, group, target);
    }

    private static String replace(String text, int start, int end, String group, int value) {
        String digits = String.format(Locale.ROOT, "%0" + (group.startsWith("0") ? group.length() : 1) + "d", value);
        return text.substring(0, start) + digits + text.substring(end);
    }

    private static int score(Danmaku item, String title, String episode) {
        String name = item == null ? "" : item.getName();
        String lowerName = name.toLowerCase(Locale.ROOT);
        String lowerTitle = title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
        int result = 0;
        if (!lowerTitle.isEmpty()) {
            if (lowerName.startsWith(lowerTitle + "(") || lowerName.startsWith(lowerTitle + "（") || lowerName.startsWith(lowerTitle + "【")) result += 60;
            else if (lowerName.startsWith(lowerTitle)) result += 40;
            else if (lowerName.contains(lowerTitle)) result += 15;
        }
        int wanted = number(episode);
        if (wanted > 0) {
            int found = number(name);
            if (found == wanted) result += 80;
            else if (found > 0) result -= 20;
        }
        return result;
    }
}
