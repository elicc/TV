package com.fongmi.android.tv.playback.vod;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.metadata.MetadataMatcher;
import com.fongmi.android.tv.metadata.MetadataRepository;
import com.fongmi.android.tv.metadata.MovieIdentity;
import com.fongmi.android.tv.metadata.MovieMetadata;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative work/episode matching: uncertainty must not silently reset a checkpoint. */
public final class VodResumeMatcher {
    private static final Pattern EPISODE = Pattern.compile("(?i)^(?:第\\s*)?0*(\\d+)\\s*(?:集|话|期)?$|^(?:ep?|episode)\\s*0*(\\d+)$");

    private VodResumeMatcher() {}

    public record Match(History source, Flag flag, Episode episode, boolean confirmed) {}

    public static Match latest(String key, Vod item, List<History> candidates) {
        String[] source = key.split(AppDatabase.SYMBOL, 3);
        MovieIdentity current = MovieIdentity.from(source[0], source.length > 1 ? source[1] : key, item);
        MetadataRepository.PlaybackIdentity currentIdentity = MetadataRepository.get().getPlaybackIdentity(current);
        for (History history : candidates.stream().filter(History::canSave)
                .sorted(Comparator.comparingLong(History::getProgressTime).reversed()).toList()) {
            boolean sameSource = key.equals(history.getKey());
            MovieMetadata previous = new MovieMetadata();
            previous.setTitle(MetadataMatcher.queryTitle(history.getVodName()));
            previous.setYear(history.getResumeYear());
            previous.setType(history.getResumeType());
            if (MetadataMatcher.resumeConflict(current, previous)) continue;
            String[] oldSource = history.getKey().split(AppDatabase.SYMBOL, 3);
            MovieIdentity old = new MovieIdentity(oldSource[0], oldSource.length > 1 ? oldSource[1] : history.getKey(),
                    history.getVodName(), history.getResumeYear(), "", history.getResumeType(), "", "");
            MetadataRepository.PlaybackIdentity oldIdentity = MetadataRepository.get().getPlaybackIdentity(old);
            boolean sameWork = currentIdentity != null && currentIdentity.sameWork(oldIdentity);
            if (!sameSource && currentIdentity != null && oldIdentity != null && !sameWork) continue;
            // Trusted provider IDs allow aliases; text fallback only proposes candidates.
            double score = MetadataMatcher.score(current, previous);
            if (!sameSource && !sameWork && !MetadataMatcher.isCandidate(score)) continue;
            boolean exactTitle = MetadataMatcher.normalize(MetadataMatcher.queryTitle(current.title())).equals(MetadataMatcher.normalize(previous.getTitle()));
            boolean confirmed = sameSource || sameWork || (exactTitle && MetadataMatcher.isResumeSafe(current, previous, false));
            // Try the old line first, but map into the target source's own URL.
            List<Flag> flags = item.getFlags().stream()
                    .sorted(Comparator.comparingInt(flag -> flag.getFlag().equals(history.getVodFlag()) ? 0 : 1)).toList();
            for (Flag flag : flags) {
                Episode episode = findEpisode(flag, history.getEpisodeIndex());
                // Only the original source/line may recover a legacy ordinal from its own label.
                // Never compare source A's episode name with source B's episode names.
                if (history.getEpisodeIndex() < 0 && sameSource && flag.getFlag().equals(history.getVodFlag()))
                    episode = findEpisode(flag, history.getEpisode());
                if (episode != null) return new Match(history, flag, episode, confirmed);
            }
            // Do not quietly fall back to an older episode when the latest one is missing.
            return new Match(history, null, null, false);
        }
        return null;
    }

    public static Episode findEpisode(Flag flag, int index) {
        if (index < 0) return null;
        return flag.getEpisodes().stream().filter(item -> item.getIndex() == index).findFirst().orElse(null);
    }

    public static Episode findEpisode(Flag flag, History history) {
        if (history.getEpisodeIndex() >= 0) return findEpisode(flag, history.getEpisodeIndex());
        return flag.getFlag().equals(history.getVodFlag()) ? findEpisode(flag, history.getEpisode()) : null;
    }

    public static boolean sameEpisode(History history, Episode episode) {
        return history.getEpisodeIndex() >= 0 && episode.getIndex() >= 0
                && history.getEpisodeIndex() == episode.getIndex();
    }

    public static boolean sameEpisode(History left, History right) {
        if (left.getEpisodeIndex() >= 0 && right.getEpisodeIndex() >= 0)
            return left.getEpisodeIndex() == right.getEpisodeIndex();
        return left.getVodFlag().equals(right.getVodFlag()) && sameEpisode(left.getEpisode(), right.getEpisode());
    }

    /** Compatibility lookup within an old checkpoint's original source/line only. */
    public static Episode findEpisode(Flag flag, Episode wanted) {
        if (wanted == null) return null;
        List<Episode> exact = flag.getEpisodes().stream().filter(item -> item.matchesName(wanted)).toList();
        if (exact.size() == 1) return exact.get(0);
        String number = episodeNumber(wanted.getName());
        if (number.isEmpty()) return null;
        List<Episode> numbered = flag.getEpisodes().stream().filter(item -> number.equals(episodeNumber(item.getName()))).toList();
        return numbered.size() == 1 ? numbered.get(0) : null;
    }

    public static boolean sameEpisode(Episode left, Episode right) {
        if (left == null || right == null) return false;
        if (left.matchesName(right)) return true;
        String number = episodeNumber(left.getName());
        return !number.isEmpty() && number.equals(episodeNumber(right.getName()));
    }

    private static String episodeNumber(String name) {
        Matcher match = EPISODE.matcher(Normalizer.normalize(name, Normalizer.Form.NFKC).trim());
        if (!match.matches()) return "";
        String number = match.group(1) == null ? match.group(2) : match.group(1);
        return number.replaceFirst("^0+(?!$)", "");
    }

}
