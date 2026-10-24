package com.fongmi.android.tv.player.engine;

import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.effect.PlayerEffect;
import com.fongmi.android.tv.player.media.PlaySpec;

public interface PlayerEngine {

    int SOFT = C.DECODE_SOFTWARE;
    int HARD = C.DECODE_HARDWARE;

    Type getType();

    Player getPlayer();

    int getAudioChannelCount();

    void release();

    void setDecode(int decode);

    default PlayerEffect getEffect() {
        return PlayerEffect.NONE;
    }

    void start(PlaySpec spec, long startPositionMs);

    default void preload(PlaySpec spec, long startPositionMs) {
    }

    default void clearPreload() {
    }

    void stop();

    /** Stops, keeping the decoders for an item that starts right after, such as the next live channel. */
    default void stopForNext() {
        stop();
    }

    /** Releases the decoders kept by {@link #stopForNext()}, once the next item plays or never came. */
    default void releaseKeptDecoders() {
    }

    default void setSubtitleStyle() {
    }

    default boolean addSubtitle(Sub sub) {
        return false;
    }

    String getErrorMessage(PlaybackException e);

    ErrorAction handleError(PlaybackException e);

    enum ErrorAction {
        RECOVERED,
        DECODE,
        FATAL
    }

    enum Type {
        EXO,
        MPV
    }
}
