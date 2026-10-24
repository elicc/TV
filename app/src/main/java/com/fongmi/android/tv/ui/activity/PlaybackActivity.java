package com.fongmi.android.tv.ui.activity;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerSeekView;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TimeBar;
import androidx.media3.ui.danmaku.DanmakuConfig;
import androidx.media3.ui.danmaku.DanmakuController;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuCache;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.playback.PlaybackIntent;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.media.PlaySpec;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.SubtitleSetting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public abstract class PlaybackActivity extends BaseActivity implements MediaController.Listener, Player.Listener, ServiceConnection {

    private static final String TRACE_TAG = "PlaybackTrace";
    private static final String DANMAKU_TAG = "DanmakuLoad";

    private final List<ServiceReadyObserver<?>> serviceReadyObservers = new ArrayList<>();
    private final List<Runnable> foreverObserverRemovers = new ArrayList<>();
    private ListenableFuture<MediaController> mControllerFuture;
    private MediaController mController;
    private PlaybackService mService;
    private boolean initialized;
    private boolean audioOnly;
    private boolean scrubbing;
    private boolean redirect;
    private boolean bound;
    private boolean stop;
    private boolean lock;
    /** True when the hidden activity's player still intended to continue playing. */
    private boolean resumePlaybackAfterHidden;
    private boolean resumed;

    protected MediaController controller() {
        return mController;
    }

    protected PlaybackService service() {
        return mService;
    }

    protected PlayerManager player() {
        return mService.player();
    }

    protected boolean isRedirect() {
        return redirect;
    }

    protected void setRedirect(boolean redirect) {
        this.redirect = redirect;
        if (mService != null) mService.setNavigationCallback(redirect ? null : getNavigationCallback(), getPlaybackKey());
    }

    protected void updateNavigationKey() {
        updateNavigationKey(getPlaybackKey());
    }

    protected void updateNavigationKey(String key) {
        if (mService != null) mService.setNavigationCallback(getNavigationCallback(), key);
    }

    protected boolean isAudioOnly() {
        return audioOnly;
    }

    protected void setAudioOnly(boolean audioOnly) {
        this.audioOnly = audioOnly;
    }

    protected boolean isStop() {
        return stop;
    }

    protected void setStop(boolean stop) {
        this.stop = stop;
    }

    protected boolean isLock() {
        return lock;
    }

    protected void setLock(boolean lock) {
        this.lock = lock;
    }

    protected abstract PlaybackService.NavigationCallback getNavigationCallback();

    protected abstract PlayerSeekView getSeekView();

    protected abstract PlayerView getPlayerView();

    protected abstract String getPlaybackKey();

    /** Whether this screen plays live streams, which are not read ahead. */
    protected boolean isLivePlayback() {
        return false;
    }

    protected boolean isOwner() {
        String key = getPlaybackKey();
        return key == null || (mService != null && key.equals(player().getKey()));
    }

    protected boolean isBindingOwner() {
        return mService != null && mService.ownsBinding(getNavigationCallback());
    }

    /** The only Activity allowed to react to events from the shared playback service. */
    protected boolean isActivePlaybackOwner() {
        return isBindingOwner() && isOwner();
    }

    protected boolean hasPlaybackSource() {
        return mService != null && isOwner() && !player().isEmpty();
    }

    protected <T> void observeForever(LiveData<T> liveData, Observer<T> observer) {
        liveData.observeForever(observer);
        foreverObserverRemovers.add(() -> liveData.removeObserver(observer));
    }

    protected <T> void observeWhenServiceReady(LiveData<T> liveData, Observer<T> observer) {
        ServiceReadyObserver<T> serviceObserver = new ServiceReadyObserver<>(observer);
        serviceReadyObservers.add(serviceObserver);
        observeForever(liveData, serviceObserver);
    }

    public void toggleDebugView() {
        getPlayerView().toggleDebugView();
        PlayerSetting.putDebug(getPlayerView().isDebugViewVisible());
    }

    public void onChoose() {
        if (!hasPlaybackSource()) return;
        PlayerManager player = player();
        PlaybackIntent.choose(this, player.getUrl(), player.getHeaders(), player.isVod(), player.getPosition(), player.getMediaTitle());
        setRedirect(true);
    }

    public void onShare(CharSequence title, String url, Map<String, String> headers) {
        PlaybackIntent.share(this, url, headers, title);
        setRedirect(true);
    }

    protected void setSeekNextFocusDown(int id) {
        View timeBar = getSeekView().findViewById(androidx.media3.ui.R.id.exo_progress);
        if (timeBar != null) timeBar.setNextFocusDownId(id);
    }

    protected void setActionFocusBoundary(View view) {
        if (view == null) return;
        if (view.isFocusable() && view.getId() != View.NO_ID) view.setNextFocusDownId(view.getId());
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) setActionFocusBoundary(group.getChildAt(i));
    }

    protected boolean isIdle() {
        return isPlaybackState(Player.STATE_IDLE);
    }

    protected boolean isEnded() {
        return isPlaybackState(Player.STATE_ENDED);
    }

    protected boolean isBuffering() {
        return isPlaybackState(Player.STATE_BUFFERING);
    }

    protected boolean isPaused() {
        return mController != null && !mController.isPlaying() && !isBuffering() && !isIdle();
    }

    private boolean isPlaybackState(int state) {
        return mController != null && mController.getPlaybackState() == state;
    }

    protected void onServiceConnected() {
    }

    protected void onPrepare() {
    }

    protected void onTracksChanged() {
    }

    protected void onDecodeChanged() {
    }

    protected void onMediaOptionsChanged() {
    }

    protected void onError(String msg) {
    }

    protected void onPlayingChanged(boolean isPlaying) {
    }

    protected void onStateChanged(int state) {
    }

    protected void onSizeChanged(VideoSize size) {
    }

    protected void onReclaim() {
    }

    protected long startPositionMs() {
        return C.TIME_UNSET;
    }

    protected boolean seekTo(long deltaMs) {
        PlayerManager player = player();
        long targetMs = Math.max(0, player.getPosition() + deltaMs);
        long durationMs = player.getDuration();
        boolean seekToEnd = durationMs > 0 && targetMs >= durationMs;
        mController.seekTo(seekToEnd ? durationMs : targetMs);
        if (!seekToEnd) mController.play();
        return seekToEnd;
    }

    protected void startPlayer(String key, Result result, boolean useParse, long timeout, MediaMetadata metadata) {
        startPlayer(key, result, useParse, timeout, startPositionMs(), metadata);
    }

    protected void startPlayer(String key, Result result, boolean useParse, long timeout, long startPositionMs, MediaMetadata metadata) {
        String error = getPlaybackError(result);
        if (error != null) onError(error);
        else startPlayerInternal(key, result, useParse, timeout, startPositionMs, metadata);
    }

    @Nullable
    private String getPlaybackError(Result result) {
        if (result.hasMsg()) return result.getMsg();
        if (result.getRealUrl().isEmpty()) return ResUtil.getString(R.string.error_play_url);
        if (result.getDrm() != null && !FrameworkMediaDrm.isCryptoSchemeSupported(result.getDrm().getUUID())) return ResUtil.getString(R.string.error_play_drm);
        return null;
    }

    private void startPlayerInternal(String key, Result result, boolean useParse, long timeout, long startPositionMs, MediaMetadata metadata) {
        trace("PLAYER_START_BEGIN key=" + key + " parse=" + (result.needParse() || useParse));
        attachPlayerView();
        updateNavigationKey(key);
        if (result.needParse() || useParse) player().parse(key, result, useParse, metadata, startPositionMs);
        else player().start(PlaySpec.from(result, key, metadata).setLive(isLivePlayback()), timeout, startPositionMs);
        trace("PLAYER_START_END");
    }

    private void bindPlaybackService() {
        trace("SERVICE_BIND_BEGIN");
        startService(new Intent(this, PlaybackService.class));
        bindService(new Intent(this, PlaybackService.class).setAction(PlaybackService.LOCAL_BIND_ACTION), this, BIND_AUTO_CREATE);
        buildControllerAsync();
        bound = true;
    }

    private void buildControllerAsync() {
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        mControllerFuture = new MediaController.Builder(this, token).setListener(this).buildAsync();
        mControllerFuture.addListener(this::onControllerConnected, ContextCompat.getMainExecutor(this));
    }

    private void onControllerConnected() {
        try {
            mController = mControllerFuture.get();
            trace("CONTROLLER_CONNECTED");
            getSeekView().setPlayer(mController);
            mController.addListener(this);
            updateKeyIncrement();
            resumePlaybackIfNeeded();
        } catch (Exception ignored) {
        }
    }

    private void addSeekListener() {
        getSeekView().getTimeBar().addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(@NonNull TimeBar timeBar, long position) {
                PlaybackActivity.this.setScrubbing(true);
            }

            @Override
            public void onScrubMove(@NonNull TimeBar timeBar, long position) {
                PlaybackActivity.this.setScrubbing(true);
            }

            @Override
            public void onScrubStop(@NonNull TimeBar timeBar, long position, boolean canceled) {
                PlaybackActivity.this.onScrubStop(canceled);
            }
        });
    }

    protected boolean isScrubbing() {
        return scrubbing;
    }

    protected void onScrubStop(boolean canceled) {
        if (!canceled && mController != null && mController.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) mController.play();
        setScrubbing(false);
    }

    private void setScrubbing(boolean scrubbing) {
        if (this.scrubbing == scrubbing) return;
        this.scrubbing = scrubbing;
        onScrubbingChanged(scrubbing);
    }

    protected void onScrubbingChanged(boolean scrubbing) {
    }

    private void updateKeyIncrement() {
        long durationMs = mController == null ? C.TIME_UNSET : mController.getDuration();
        long incrementMs = getKeyTimeIncrementMs(durationMs);
        TimeBar timeBar = getSeekView().getTimeBar();
        timeBar.setKeyTimeIncrement(incrementMs);
    }

    private long getKeyTimeIncrementMs(long durationMs) {
        if (durationMs > TimeUnit.HOURS.toMillis(3)) {
            return TimeUnit.MINUTES.toMillis(5);
        } else if (durationMs > TimeUnit.MINUTES.toMillis(30)) {
            return TimeUnit.MINUTES.toMillis(1);
        } else if (durationMs > TimeUnit.MINUTES.toMillis(15)) {
            return TimeUnit.SECONDS.toMillis(30);
        } else if (durationMs > TimeUnit.MINUTES.toMillis(10)) {
            return TimeUnit.SECONDS.toMillis(15);
        } else {
            return TimeUnit.SECONDS.toMillis(10);
        }
    }

    private PendingIntent buildSessionIntent() {
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private boolean shouldReclaim() {
        return mService != null && !isOwner();
    }

    private void resumePlayback() {
        if (shouldReclaim()) reclaimPlayback();
        else attachPlayerView();
    }

    private void captureResumePlaybackIntent() {
        if (isFinishing() || isInPictureInPictureMode() || mService == null || !isActivePlaybackOwner()) {
            resumePlaybackAfterHidden = false;
            return;
        }
        // ExoPlayer reports isPlaying() as false while buffering. Keep the
        // playWhenReady intent so a HOME transition does not strand a stream in
        // BUFFERING after the PlayerView is reattached.
        resumePlaybackAfterHidden = mController != null && mController.getPlayWhenReady();
        trace("PLAYBACK_RESUME_INTENT=" + resumePlaybackAfterHidden);
    }

    private void resumePlaybackIfNeeded() {
        if (!resumePlaybackAfterHidden || mController == null || !isOwner()) return;
        if (!resumed) return;
        if (!mController.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) return;
        resumePlaybackAfterHidden = false;
        trace("PLAYBACK_AUTO_RESUME");
        mController.play();
    }

    private void reclaimPlayback() {
        detachPlayerView();
        onReclaim();
    }

    private void claimBinding() {
        if (mService == null) return;
        mService.claimBinding(getNavigationCallback(), this::closePiP);
        mService.setSessionActivity(buildSessionIntent());
    }

    private boolean canActivate() {
        return mService != null && !isFinishing() && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }

    private boolean canDispatch() {
        return !isFinishing() && isBindingOwner();
    }

    private void activateService() {
        if (!canActivate()) return;
        claimBinding();
        if (!isRedirect()) updateNavigationKey();
        dispatchPendingObservers();
        initService();
    }

    private void initService() {
        if (initialized) return;
        initialized = true;
        onServiceConnected();
    }

    private void closePiP() {
        if (!isInPictureInPictureMode()) return;
        detach();
        finish();
    }

    private void attachPlayerView() {
        if (mService != null) syncPlayerView(player().getPlayer());
    }

    private void detachPlayerView() {
        getPlayerView().setPlayer(null);
    }

    private void syncPlayerView(Player player) {
        getPlayerView().setPlayer(player);
        syncDanmakuSource();
    }

    private void configurePlayerView() {
        PlayerView playerView = getPlayerView();
        playerView.setRender(PlayerSetting.getRender());
        playerView.setDanmakuOkHttpClient(DanmakuCache.client());
        playerView.getDanmakuController().setListener(danmakuLoadListener);
        playerView.setDanmakuEnabled(DanmakuSetting.isShow());
        playerView.setDanmakuConfig(DanmakuSetting.getConfig());
        SubtitleSetting.applyStyle(this, playerView.getSubtitleView());
    }

    private void syncDanmakuSource() {
        if (!isActivePlaybackOwner()) return;
        Uri uri = player().getLoadableDanmakuUri();
        danmakuLoadListener.onSourceChanged(uri);
        getPlayerView().setDanmakuSource(uri);
    }

    private void releasePlaybackService() {
        trace("RELEASE_SERVICE_BEGIN");
        PlaybackService shutdownService = null;
        if (mService != null) {
            PlaybackService service = mService;
            boolean playbackOwner = isOwner();
            service.removePlayerCallback(mPlayerCallback);
            if (service.releaseBinding(getNavigationCallback())) {
                if (shouldKeepServiceAlive()) keepServiceAlive(playbackOwner);
                else shutdownService = service;
            }
        }
        detach();
        // Release the MediaController/local binding first. This lets
        // MediaLibraryService observe its final unbind before stopSelf(), avoiding
        // a stale start intent recreating the player during Activity teardown.
        if (shutdownService != null) shutdownService.shutdown();
        trace("RELEASE_SERVICE_END");
    }

    private boolean shouldKeepServiceAlive() {
        return mService.hasMediaClient() || mService.hasPlayerCallback();
    }

    private void keepServiceAlive(boolean playbackOwner) {
        if (playbackOwner) mService.suspend();
        mService.resetSessionActivity();
    }

    private void detach() {
        releaseController();
        releaseBinding();
    }

    private void pausePlayback() {
        if (mController != null) mController.pause();
        else if (mService != null) player().pause();
    }

    private void releaseController() {
        if (mControllerFuture != null) MediaController.releaseFuture(mControllerFuture);
        if (mController != null) mController.removeListener(this);
        if (mController != null) getSeekView().setPlayer(null);
        mControllerFuture = null;
        mController = null;
    }

    private void releaseBinding() {
        if (!bound) return;
        bound = false;
        if (mService != null) mService.removePlayerCallback(mPlayerCallback);
        unbindService(this);
        mService = null;
    }

    private void clearObservers() {
        foreverObserverRemovers.forEach(Runnable::run);
        foreverObserverRemovers.clear();
        serviceReadyObservers.clear();
    }

    private void dispatchPendingObservers() {
        if (!canDispatch()) return;
        serviceReadyObservers.forEach(ServiceReadyObserver::dispatch);
    }

    /**
     * Retries danmaku sources that failed to load and keeps searched sources on disk. A failed
     * load is otherwise silent: danmaku stays blank until something else resets the source.
     */
    private final class DanmakuLoadListener implements DanmakuController.Listener {

        private final long[] retryDelaysMs = {2_000, 5_000, 15_000};
        private final Runnable retry = this::retry;
        private Uri uri;
        private int attempts;

        void onSourceChanged(@Nullable Uri uri) {
            if (Objects.equals(this.uri, uri)) return;
            App.removeCallbacks(retry);
            this.uri = uri;
            attempts = 0;
        }

        @Override
        public void onLoadCompleted(@NonNull Uri uri, int count) {
            if (uri.equals(this.uri)) App.removeCallbacks(retry);
            DanmakuCache.onLoaded(uri, count);
        }

        @Override
        public void onLoadError(@NonNull Uri uri, @NonNull IOException error) {
            Log.w(DANMAKU_TAG, "load failed: " + uri.getScheme() + " " + uri.getHost() + " " + error.getClass().getSimpleName());
            DanmakuCache.onFailed(uri);
            if (!uri.equals(this.uri) || !isNetworkUri(uri) || attempts >= retryDelaysMs.length) return;
            App.post(retry, retryDelaysMs[attempts++]);
        }

        private boolean isNetworkUri(Uri uri) {
            return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        }

        private void retry() {
            if (uri != null && isActivePlaybackOwner() && uri.equals(player().getLoadableDanmakuUri())) getPlayerView().setDanmakuSource(uri);
        }
    }

    private final DanmakuLoadListener danmakuLoadListener = new DanmakuLoadListener();

    private final PlaybackService.PlayerCallback mPlayerCallback = new PlaybackService.PlayerCallback() {

        @Override
        public void onPrepare() {
            if (isActivePlaybackOwner()) PlaybackActivity.this.onPrepare();
        }

        @Override
        public void onTracksChanged() {
            if (!isActivePlaybackOwner()) return;
            PlaybackActivity.this.onTracksChanged();
            if (PlayerSetting.isDebug() && !getPlayerView().isDebugViewVisible()) getPlayerView().toggleDebugView();
        }

        @Override
        public void onDecodeChanged() {
            if (isActivePlaybackOwner()) PlaybackActivity.this.onDecodeChanged();
        }

        @Override
        public void onMediaOptionsChanged() {
            if (isActivePlaybackOwner()) PlaybackActivity.this.onMediaOptionsChanged();
        }

        @Override
        public void onError(String msg) {
            if (isActivePlaybackOwner()) PlaybackActivity.this.onError(msg);
        }

        @Override
        public void onPlayerRebuild(Player player) {
            if (isActivePlaybackOwner()) syncPlayerView(player);
        }

        @Override
        public void onDanmakuSourceChanged(Uri uri) {
            if (!isActivePlaybackOwner()) return;
            danmakuLoadListener.onSourceChanged(uri);
            getPlayerView().setDanmakuSource(uri);
        }

        @Override
        public void onDanmakuConfigChanged(DanmakuConfig config) {
            if (isActivePlaybackOwner()) getPlayerView().setDanmakuConfig(config);
        }

        @Override
        public void onDanmakuEnabledChanged(boolean enabled) {
            if (isActivePlaybackOwner()) getPlayerView().setDanmakuEnabled(enabled);
        }

        @Override
        public void onDanmakuSent(String text) {
            if (isActivePlaybackOwner()) getPlayerView().sendDanmaku(text);
        }
    };

    @Override
    protected void initView(Bundle savedInstanceState) {
        trace("PLAYBACK_INIT_BEGIN");
        super.initView(savedInstanceState);
        configurePlayerView();
        bindPlaybackService();
        addSeekListener();
        trace("PLAYBACK_INIT_END");
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        if (isActivePlaybackOwner() && events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_AVAILABLE_COMMANDS_CHANGED)) updateKeyIncrement();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (!isActivePlaybackOwner()) return;
        if (isPlaying) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else if (!isBuffering()) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        onPlayingChanged(isPlaying);
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        if (isActivePlaybackOwner()) trace("PLAYER_PLAY_WHEN_READY value=" + playWhenReady + " reason=" + reason);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        trace("PLAYER_STATE state=" + state);
        if (isActivePlaybackOwner()) onStateChanged(state);
    }

    @Override
    public void onRenderedFirstFrame() {
        if (isActivePlaybackOwner()) trace("PLAYER_FIRST_FRAME");
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize size) {
        if (isActivePlaybackOwner()) onSizeChanged(size);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        mService = ((PlaybackService.LocalBinder) binder).getService();
        trace("SERVICE_CONNECTED");
        mService.addPlayerCallback(mPlayerCallback);
        activateService();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        initialized = false;
        mService = null;
    }

    @Override
    protected void onStart() {
        super.onStart();
        activateService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        claimBinding();
        setRedirect(false);
        dispatchPendingObservers();
        resumePlayback();
        resumePlaybackIfNeeded();
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
        if (isRedirect() && isActivePlaybackOwner()) pausePlayback();
    }

    @Override
    protected void onStop() {
        trace("PLAYBACK_ON_STOP_BEGIN");
        captureResumePlaybackIntent();
        super.onStop();
        if (isActivePlaybackOwner() && (isFinishing() || PlayerSetting.isBackgroundOff())) pausePlayback();
        if (!isInPictureInPictureMode()) detachPlayerView();
        trace("PLAYBACK_ON_STOP_END");
    }

    @Override
    protected void onDestroy() {
        trace("PLAYBACK_ON_DESTROY_BEGIN");
        clearObservers();
        detachPlayerView();
        danmakuLoadListener.onSourceChanged(null);
        getPlayerView().getDanmakuController().setListener(null);
        super.onDestroy();
        releasePlaybackService();
        trace("PLAYBACK_ON_DESTROY_END");
    }

    private void trace(String event) {
        if (BuildConfig.DEBUG) Log.d(TRACE_TAG, event + " t=" + SystemClock.uptimeMillis());
    }

    private final class ServiceReadyObserver<T> implements Observer<T> {

        private final Observer<T> observer;
        private T pendingValue;
        private boolean pending;

        private ServiceReadyObserver(Observer<T> observer) {
            this.observer = observer;
        }

        @Override
        public void onChanged(T value) {
            if (!canDispatch()) {
                pendingValue = value;
                pending = true;
            } else {
                deliver(value);
            }
        }

        private void deliver(T value) {
            pendingValue = null;
            pending = false;
            observer.onChanged(value);
        }

        private void dispatch() {
            if (pending) deliver(pendingValue);
        }
    }
}
