package com.fongmi.android.tv.metadata;

import android.text.TextUtils;

import java.text.Normalizer;
import java.util.Locale;

/** Conservative, deterministic matcher used before an external ID is persisted. */
public final class MetadataMatcher {
    public static final double AUTO_THRESHOLD = 0.90d;
    public static final double CANDIDATE_THRESHOLD = 0.75d;
    private static final double MIN_MARGIN = 0.10d;

    private MetadataMatcher() {
    }

    /**
     * Removes only well-known trailing distribution labels before an external
     * metadata lookup. The original title remains untouched for display and
     * source identity; this is deliberately narrower than general punctuation
     * cleanup so legitimate movie titles are not rewritten.
     */
    public static String queryTitle(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String title = Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
        String cleaned = title.replaceFirst("(?iu)\\s*[\\[(]\\s*(?:臻彩|4k|8k|1080p|高清|超清|蓝光|蓝光版|抢先版|tc|ts|cam)\\s*[\\])]\\s*$", "").trim();
        return cleaned.isEmpty() ? title : cleaned;
    }

    public static double score(MovieIdentity source, MovieMetadata candidate) {
        if (source == null || candidate == null || hardConflict(source, candidate)) return 0d;
        double points = similarity(queryTitle(source.title()), candidate.getTitle()) * 0.45d;
        double weight = 0.45d;
        if (hasBothYears(source.year(), candidate.getYear())) {
            points += yearScore(source.year(), candidate.getYear()) * 0.20d;
            weight += 0.20d;
        }
        if (hasBoth(source.director(), candidate.getDirectors())) {
            points += overlap(source.director(), candidate.getDirectors()) * 0.15d;
            weight += 0.15d;
        }
        if (hasBoth(source.actor(), candidate.getActors())) {
            points += overlap(source.actor(), candidate.getActors()) * 0.10d;
            weight += 0.10d;
        }
        if (hasBoth(source.area(), candidate.getArea())) {
            points += overlap(source.area(), candidate.getArea()) * 0.05d;
            weight += 0.05d;
        }
        if (hasBoth(source.type(), candidate.getType())) {
            points += overlap(source.type(), candidate.getType()) * 0.05d;
            weight += 0.05d;
        }
        return clamp(points / weight);
    }

    public static boolean isAutomatic(double score, double secondBest) {
        return score >= AUTO_THRESHOLD && score - secondBest >= MIN_MARGIN;
    }

    public static boolean isCandidate(double score) {
        return score >= CANDIDATE_THRESHOLD;
    }

    /** Stricter than artwork matching: a title-only score of 1 is not resume evidence. */
    public static boolean isResumeSafe(MovieIdentity source, MovieMetadata candidate, boolean userVerified) {
        if (resumeConflict(source, candidate)) return false;
        if (userVerified) return true;
        boolean year = hasBothYears(source.year(), candidate.getYear());
        boolean people = hasBoth(source.director(), candidate.getDirectors()) && hasBoth(source.actor(), candidate.getActors())
                && overlap(source.director(), candidate.getDirectors()) >= 0.8d && overlap(source.actor(), candidate.getActors()) >= 0.8d;
        return (year || people) && score(source, candidate) >= AUTO_THRESHOLD;
    }

    public static boolean resumeConflict(MovieIdentity source, MovieMetadata candidate) {
        if (source == null || candidate == null) return true;
        if (hasBothYears(source.year(), candidate.getYear()) && !firstYear(source.year()).equals(firstYear(candidate.getYear()))) return true;
        int sourceKind = mediaKind(source.type());
        int candidateKind = mediaKind(candidate.getExternalType().isEmpty() ? candidate.getType() : candidate.getExternalType());
        return (sourceKind != 0 && candidateKind != 0 && sourceKind != candidateKind)
                || !sameResumeEdition(source.title(), candidate.getTitle());
    }

    public static boolean sameResumeEdition(String left, String right) {
        return edition(left).equals(edition(right));
    }

    private static String edition(String title) {
        String value = normalize(title);
        java.util.regex.Matcher season = java.util.regex.Pattern.compile("第([零一二三四五六七八九十百0-9]+)[季部]|season([0-9]+)|s([0-9]+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value);
        String identity = season.find() ? season.group() : "";
        java.util.regex.Matcher cut = java.util.regex.Pattern.compile("未删减|删减|加长|导演剪辑|重剪|合辑|合集|特别版|特別版|director.?scut|extended").matcher(value);
        while (cut.find()) identity += ":" + cut.group();
        return identity;
    }

    public static int mediaKind(String value) {
        if (value == null) return 0;
        if (value.contains("电影") || value.equalsIgnoreCase("movie")) return 1;
        if (value.contains("电视剧") || value.contains("连续剧") || value.matches(".*(?:国产|欧美|韩|日|港|台|美|英)剧.*") || value.equalsIgnoreCase("tv")) return 2;
        return 0;
    }

    public static boolean hardConflict(MovieIdentity source, MovieMetadata candidate) {
        String sourceYear = firstYear(source.year());
        String candidateYear = firstYear(candidate.getYear());
        if (!sourceYear.isEmpty() && !candidateYear.isEmpty()) {
            try {
                if (Math.abs(Integer.parseInt(sourceYear) - Integer.parseInt(candidateYear)) > 1) return true;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    public static String normalize(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String text = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        text = text.replaceAll("[^\\p{L}\\p{Nd}]", "");
        return text;
    }

    private static double similarity(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isEmpty() || b.isEmpty()) return 0d;
        if (a.equals(b)) return 1d;
        int distance = levenshtein(a, b);
        int max = Math.max(a.length(), b.length());
        double edit = 1d - (double) distance / max;
        int intersection = 0;
        for (int i = 0; i < a.length(); i++) if (b.indexOf(a.charAt(i)) >= 0) intersection++;
        double jaccard = (double) intersection / Math.max(a.length(), b.length());
        return clamp(edit * 0.65d + jaccard * 0.35d);
    }

    private static double yearScore(String left, String right) {
        return firstYear(left).equals(firstYear(right)) ? 1d : 0d;
    }

    private static boolean hasBothYears(String left, String right) {
        return !firstYear(left).isEmpty() && !firstYear(right).isEmpty();
    }

    private static boolean hasBoth(String left, String right) {
        return !normalize(left).isEmpty() && !normalize(right).isEmpty();
    }

    private static double overlap(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.equals(b)) return 1d;
        return b.contains(a) || a.contains(b) ? 0.8d : 0d;
    }

    private static String firstYear(String value) {
        if (TextUtils.isEmpty(value)) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(19|20)\\d{2}").matcher(value);
        return matcher.find() ? matcher.group() : "";
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static double clamp(double value) {
        return Math.max(0d, Math.min(1d, value));
    }
}
