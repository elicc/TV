package com.fongmi.android.tv.voice;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative UI-result bridge, not an ASR or voice-origin detector. */
public final class TclSearchBridgePolicy {
    public static final String PACKAGE = "com.tcl.wholenetsearch";
    public static final String TITLE_ID = PACKAGE + ":id/ttvl_result_title";
    // The resource ID and title envelope identify a search, not a category/keyword allowlist.
    private static final Pattern TITLE = Pattern.compile("^“([^”]+)”的[^“”\\p{Cntrl}]{1,32}结果（[0-9]{1,7}条）$");
    private final LinkedHashSet<String> consumed = new LinkedHashSet<>();
    private String candidate;
    private int window;
    private long since;
    private long first;
    private boolean cleared;

    public static String parse(String title) {
        if (title == null || title.length() > 256) return null;
        Matcher matcher = TITLE.matcher(title);
        if (!matcher.matches()) return null;
        String raw = matcher.group(1);
        if (raw.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT)) return null;
        String word = raw.trim();
        if (word.isEmpty() || word.codePointCount(0, word.length()) > 100) return null;
        if (word.contains("没有找到") || word.contains("未找到") || word.contains("为你推荐")) return null;
        return word;
    }

    public void prime(String title) {
        cancel();
        String word = parse(title);
        if (word != null) remember(word);
    }

    /** Missing nodes are NOT proof of a fresh search. Only an explicitly blank title is. */
    public long observe(int windowId, String title, long now) {
        if ("".equals(title)) {
            cancel();
            cleared = true;
            return -1;
        }
        String word = parse(title);
        if (word == null || (!cleared && consumed.contains(word))) {
            cancel();
            return -1;
        }
        if (candidate == null) first = now;
        if (!Objects.equals(candidate, word) || window != windowId) {
            candidate = word;
            window = windowId;
            since = now;
        }
        if (now - first > 2000) {
            cancel();
            return -1;
        }
        return Math.max(0, 400 - (now - since));
    }

    public String take(int windowId, String title, long now) {
        if (candidate == null || window != windowId || !candidate.equals(parse(title))) return null;
        if (now - first > 2000) { cancel(); return null; }
        if (now - since < 400) return null;
        String word = candidate;
        remember(word);
        cancel();
        return word;
    }

    private void remember(String word) {
        consumed.add(word);
        if (consumed.size() > 32) consumed.remove(consumed.iterator().next());
    }

    public void cancel() {
        candidate = null;
        cleared = false;
    }
}
