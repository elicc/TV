package com.fongmi.android.tv.ui.activity;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.splashscreen.SplashScreen;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Cache;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Func;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.db.ConfigVault;
import com.fongmi.android.tv.db.VaultPolicy;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.model.VideoViewModel;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.DLNARendererService;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.BaseDiffCallback;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.custom.CustomTitleView;
import com.fongmi.android.tv.ui.custom.TvKeycapsBar;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.dialog.VaultDialog;
import com.fongmi.android.tv.ui.presenter.FuncPresenter;
import com.fongmi.android.tv.ui.presenter.EmptySourcePresenter;
import com.fongmi.android.tv.ui.presenter.HeaderPresenter;
import com.fongmi.android.tv.ui.presenter.HeroPresenter;
import com.fongmi.android.tv.ui.presenter.HistoryPresenter;
import com.fongmi.android.tv.ui.presenter.ProgressPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.ui.motion.TvMotion;
import com.fongmi.android.tv.ui.motion.TvStagger;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.TvTheme;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.net.OkHttp;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Lists;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, VodPresenter.OnClickListener, FuncPresenter.OnClickListener, HistoryPresenter.OnClickListener, HeroPresenter.Listener, EmptySourcePresenter.Listener, ConfigListener, VaultDialog.Listener {

    private ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mHistoryAdapter;
    private ArrayObjectAdapter mAdapter;
    private HistoryPresenter mPresenter;
    private SiteViewModel mViewModel;
    private Result mResult;
    /** History card currently under the D-pad; null means the source hero is active. */
    private History mFocusedHistory;
    /** History card whose detail prefetch is pending after focus settles. */
    private History mPrefetchTarget;
    private Clock mClock;
    private ObjectAnimator mPulse;
    private boolean mLoading;
    private boolean mConfigLoading;
    private boolean mConfigFailed;
    private boolean mOwnConfigEvent;
    private String mConfigError = "";
    private boolean mActionHandled;
    /** True once the cold-start brand overlay has been handed off (or skipped). */
    private boolean mSplashDone;
    private long mSplashShownAt;
    /** Guards against re-entrant adapter mutations while a previous updateHero is queued. */
    private boolean mHeroUpdatePending;
    /**
     * Set while a file-access grant is in flight. PermissionX never calls back on a refusal,
     * so returning from the system screen is the only signal a denial produces; onResume
     * re-reads the grant and finishes the flow either way.
     */
    private boolean mVaultPending;
    /** What to do once the grant resolves; null when the request was only to enable backup. */
    private Runnable mVaultOnGranted;
    /** Pending coalesced updateHero runnable; retained so onDestroy can cancel it. */
    private final Runnable mHeroUpdate = this::applyHeroUpdate;
    private final ActivityResultLauncher<Intent> mFileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> FileChooser.getUri(result, uri -> VideoActivity.file(this, uri)));

    @Override
    protected boolean customWall() {
        return !isFilmAtmosphereEnabled();
    }

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private Config getConfig() {
        return VodConfig.get().getConfig();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        mActionHandled = false;
        checkAction(intent);
        mActionHandled = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Always keep the splash screen condition false so the splash exits
        // the moment the first frame is drawn; on some Android TV emulators the
        // default "keep until pre-draw" path leaves the starting_reveal
        // animation leash hanging and prevents the activity surface from
        // invalidating, which would render the hero/empty-source row as black.
        // Once the splash exit animation completes we also force a redraw so
        // the activity surface is guaranteed to commit at least one frame.
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        splash.setKeepOnScreenCondition(() -> false);
        splash.setOnExitAnimationListener(provider -> {
            provider.remove();
            if (!isFinishing() && !isDestroyed()) mBinding.getRoot().invalidate();
        });
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        // Brand first: raise the overlay before any home setup runs so the viewer never
        // catches a half-built grid behind the fade.
        if (savedInstanceState == null) playSplash();
        mActionHandled = savedInstanceState != null && savedInstanceState.getBoolean("home.actionHandled");
        mResult = Result.empty();
        mClock = Clock.create(mBinding.clock).format("HH:mm");
        // The focused-poster backdrop replaces the hero-scoped atmosphere; hide it
        // entirely when the user opted for their own wallpaper (customWall).
        mBinding.atmosphere.setVisibility(isFilmAtmosphereEnabled() ? View.VISIBLE : View.GONE);
        mBinding.progressLayout.showProgress();
        PermissionUtil.requestNotify(this);
        DLNARendererService.start(this);
        Updater.create().start(this);
        setRecyclerView();
        setViewModel();
        setAdapter();
        setKeycaps();
        startSourcePulse();
        initConfig();
        setTitle();
        setLogo();
        adaptToolbar();
        if (savedInstanceState == null) mBinding.navHome.requestFocus();
    }

    private void setKeycaps() {
        TvKeycapsBar.Cap[] caps = {
                TvKeycapsBar.Cap.of(getString(R.string.tv_key_dpad), KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT),
                TvKeycapsBar.Cap.of(getString(R.string.tv_key_ok), KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER),
                TvKeycapsBar.Cap.of(getString(R.string.tv_key_menu), KeyEvent.KEYCODE_MENU),
                TvKeycapsBar.Cap.of(getString(R.string.tv_key_back), KeyEvent.KEYCODE_BACK),
        };
        String[] hints = {getString(R.string.tv_hint_dpad), getString(R.string.tv_hint_ok), getString(R.string.tv_hint_menu), getString(R.string.tv_hint_back)};
        mBinding.keycaps.setCaps(caps, hints);
    }

    private void startSourcePulse() {
        if (!TvMotion.motionEnabled(this)) return;
        mPulse = ObjectAnimator.ofFloat(mBinding.sourceStatus, "alpha", 1f, 0.45f);
        mPulse.setDuration(1200);
        mPulse.setRepeatCount(ObjectAnimator.INFINITE);
        mPulse.setRepeatMode(ObjectAnimator.REVERSE);
        mPulse.start();
    }

    @Override
    protected TvKeycapsBar keycaps() {
        return mBinding.keycaps;
    }

    private void adaptToolbar() {
        boolean largeText = getResources().getConfiguration().fontScale > 1.15f;
        mBinding.toolbar.setOrientation(largeText ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        mBinding.navSpacer.setVisibility(largeText ? View.GONE : View.VISIBLE);
        mBinding.clock.setVisibility(View.VISIBLE);
        if (largeText) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mBinding.utilities.getLayoutParams();
            params.gravity = android.view.Gravity.END;
            mBinding.utilities.setLayoutParams(params);
        }
    }

    @Override
    protected void initEvent() {
        mBinding.sourceRow.setOnClickListener(v -> showDialog());
        mBinding.navHome.setSelected(true);
        mBinding.navHome.setOnClickListener(v -> {
            mBinding.recycler.setSelectedPosition(0);
            mBinding.recycler.requestFocus();
        });
        mBinding.navVod.setOnClickListener(v -> onItemClick(Func.create(R.string.home_vod)));
        mBinding.navLive.setOnClickListener(v -> {
            if (LiveConfig.hasUrl()) onItemClick(Func.create(R.string.home_live));
            else new MaterialAlertDialogBuilder(this).setMessage(R.string.tv_live_unavailable).setPositiveButton(R.string.home_setting, (d, w) -> SettingActivity.start(this)).setNegativeButton(android.R.string.cancel, null).show();
        });
        mBinding.navKeep.setOnClickListener(v -> onItemClick(Func.create(R.string.home_keep)));
        mBinding.navSearch.setOnClickListener(v -> onItemClick(Func.create(R.string.home_search)));
        mBinding.settings.setOnClickListener(v -> SettingActivity.start(this));
        mBinding.more.setOnClickListener(v -> showMore());
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mPresenter.isDelete() && position != getHistoryIndex()) setHistoryDelete(false);
                if (position != getHistoryIndex() && mFocusedHistory != null) {
                    mFocusedHistory = null;
                    updateHero();
                }
                syncBackdropFromSelection(child);
            }
        });
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            PermissionUtil.requestFile(this, allGranted -> checkType(intent));
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) SearchActivity.start(this, keyword);
        }
    }

    private void checkType(Intent intent) {
        if ("text/plain".equals(intent.getType()) || UrlUtil.path(intent.getData()).endsWith(".m3u")) {
            FileChooser.getUri(intent, uri -> loadLive(UrlUtil.toLocalUrl(uri)));
        } else {
            FileChooser.getUri(intent, uri -> VideoActivity.file(this, uri));
        }
    }

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(Integer.class, new HeaderPresenter());
        selector.addPresenter(EmptySourcePresenter.Item.class, new EmptySourcePresenter(this));
        selector.addPresenter(HeroPresenter.Item.class, new HeroPresenter(this));
        selector.addPresenter(String.class, new ProgressPresenter());
        selector.addPresenter(Vod.class, new VodPresenter(this, Style.list()));
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), FuncPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16, FocusHighlight.ZOOM_FACTOR_SMALL, HorizontalGridView.FOCUS_SCROLL_ALIGNED), HistoryPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(12));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, result -> {
            mLoading = false;
            mAdapter.remove("progress");
            addVideo(mResult = result);
            updateHero();
            Cache.clear().put(result);
        });
    }

    private void setAdapter() {
        mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        boolean empty = showEmptySource();
        mAdapter.add(empty ? emptySourceItem() : heroItem());
        if (!empty) mAdapter.add(R.string.home_recommend);
        // Load persisted playback entries on the first render. Previously the
        // history row was only refreshed after an EventBus notification, so a
        // cold start (or a history created before this Activity was resumed)
        // silently omitted the recent-watch module.
        getHistory();
    }

    private boolean showEmptySource() {
        return !hasConfiguredSource();
    }

    private boolean hasConfiguredSource() {
        return !TextUtils.isEmpty(getConfig().getUrl());
    }

    private EmptySourcePresenter.Item emptySourceItem() {
        // Offer the recovery link exactly when a silent restore was impossible. When the vault
        // is reachable the restore has already run, and when it is unreachable we cannot tell
        // whether a snapshot exists — checking would itself need the grant — so offer it and
        // find out after the grant. Cheaper to over-offer than to hide the one path back.
        boolean restorable = !ConfigVault.isWritable();
        if (mConfigFailed) return EmptySourcePresenter.Item.asFailed(restorable);
        if (mConfigLoading) return EmptySourcePresenter.Item.asLoading();
        return EmptySourcePresenter.Item.fresh(restorable);
    }

    private void setTitle() {
        boolean configured = hasConfiguredSource();
        List<String> items = Arrays.asList(getHome().getName(), getConfig().getName());
        Optional<String> optional = items.stream().filter(s -> !TextUtils.isEmpty(s)).findFirst();
        mBinding.title.setText(configured ? optional.orElse(getString(R.string.tv_source)) : getString(R.string.tv_source_unconfigured));
        int statusColor = configured && !mConfigFailed
                ? getColor(R.color.tv_success)
                : mConfigFailed ? getColor(R.color.tv_danger) : TvTheme.color(this, R.attr.tvColorAccent);
        mBinding.sourceStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(statusColor));
        mBinding.title.setContentDescription(getString(R.string.tv_source) + "：" + mBinding.title.getText());
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init();
        maybeAutoRestore();
    }

    /**
     * Recover the previous install's configuration on cold start. Only runs when shared storage
     * is reachable, no source is configured, and this install has not already tried — an empty
     * source list is the proof that a restore cannot overwrite anything, since every history
     * and keep row belongs to a config. A no-op on a normal launch.
     */
    private void maybeAutoRestore() {
        ConfigVault.restoreIfFresh(source -> {
            if (isFinishing() || isDestroyed()) return;
            if (source == VaultPolicy.Source.NONE) {
                // Either there was nothing to restore or the gate refused. Re-render so the
                // recovery link reflects the vault's real state.
                updateHero();
                return;
            }
            Notify.show(R.string.tv_vault_restored);
            VodConfig.get().init().load(getCallback());
            LiveConfig.get().init().load();
            updateHero();
        });
    }

    /** Explicit restore, after the viewer asked for it and the vault became reachable. */
    private void restoreFromVault() {
        ConfigVault.restore(source -> {
            if (isFinishing() || isDestroyed()) return;
            if (source == VaultPolicy.Source.NONE) {
                Notify.show(R.string.tv_vault_restore_missing);
                updateHero();
                return;
            }
            Notify.show(R.string.tv_vault_restored);
            VodConfig.get().init().load(getCallback());
            LiveConfig.get().init().load();
            WallConfig.get().init();
        });
    }

    /**
     * Ask for file access, then run {@code onGranted}. Safe to call when access is already
     * held. The permission library stays silent when the viewer declines, so the continuation
     * is driven from {@link #onResume} as well as from the callback; the pending flag makes the
     * two paths idempotent.
     */
    private void requestVault(Runnable onGranted) {
        if (ConfigVault.isWritable()) {
            onGranted.run();
            return;
        }
        mVaultOnGranted = onGranted;
        mVaultPending = true;
        PermissionUtil.requestAllFiles(this, granted -> finishVaultRequest());
    }

    private void finishVaultRequest() {
        if (!mVaultPending) return;
        mVaultPending = false;
        Runnable action = mVaultOnGranted;
        mVaultOnGranted = null;
        if (!ConfigVault.isWritable()) {
            Notify.show(R.string.tv_vault_denied);
            updateHero();
            return;
        }
        if (action != null) action.run();
    }

    /**
     * Say it once when a write failed while storage was reachable — a full disk, a dead card.
     * A missing grant is the expected fresh-install state and is handled by the offer instead,
     * so this stays quiet on a healthy or merely un-granted install.
     */
    private void reportVaultFailure() {
        if (!ConfigVault.needsReport()) return;
        ConfigVault.markReported();
        Notify.show(R.string.tv_vault_write_failed);
    }

    /** The one unprompted offer, raised once, and only now that a source is actually loading. */
    private void maybeOfferVault() {
        if (!ConfigVault.shouldPrompt()) return;
        if (getSupportFragmentManager().findFragmentByTag(VaultDialog.TAG) != null) return;
        VaultDialog.create().show(this);
    }

    @Override
    public void onVaultEnable() {
        requestVault(() -> {
            if (isFinishing() || isDestroyed()) return;
            ConfigVault.save();
            Notify.show(R.string.tv_vault_enabled);
        });
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                mOwnConfigEvent = true;
                mConfigLoading = true;
                mConfigFailed = false;
                mConfigError = "";
                updateHero();
                mBinding.progressLayout.showContent();
            }

            @Override
            public void success() {
                if (isFinishing() || isDestroyed()) return;
                mConfigLoading = false;
                mConfigFailed = false;
                mConfigError = "";
                updateHero();
                showContent();
                reportVaultFailure();
                maybeOfferVault();
            }

            @Override
            public void error(String msg) {
                if (isFinishing() || isDestroyed()) return;
                mConfigLoading = false;
                mConfigFailed = !TextUtils.isEmpty(getConfig().getUrl());
                mLoading = false;
                mConfigError = msg;
                updateHero();
                Notify.show(msg);
                showContent();
            }
        };
    }

    /**
     * Cold-start brand moment. The platform splash is unreliable on TV: it is torn down the
     * moment the first frame lands, so a slow cold start leaves the viewer on a bare
     * background for seconds with nothing to look at. The mark is therefore choreographed
     * inside the activity and handed off to the home entrance once the config settles.
     */
    private void playSplash() {
        View splash = mBinding.splash;
        if (!TvMotion.motionEnabled(splash)) {
            mSplashDone = true;
            return;
        }
        mSplashShownAt = SystemClock.uptimeMillis();
        splash.setVisibility(View.VISIBLE);
        View logo = mBinding.splashLogo;
        logo.setAlpha(0f);
        logo.setScaleX(0.90f);
        logo.setScaleY(0.90f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(TvMotion.SPLASH_MARK)
                .setInterpolator(AnimationUtils.loadInterpolator(this, R.interpolator.tv_interp_enter)).start();
        View wordmark = mBinding.splashWordmark;
        wordmark.setAlpha(0f);
        wordmark.setTranslationY(ResUtil.dp2px(10));
        wordmark.animate().alpha(1f).translationY(0f).setStartDelay(TvMotion.SPLASH_MARK / 3)
                .setDuration(TvMotion.SPLASH_MARK)
                .setInterpolator(AnimationUtils.loadInterpolator(this, R.interpolator.tv_interp_enter)).start();
        splash.postDelayed(this::dismissSplash, TvMotion.SPLASH_MAX);
    }

    /** Cross-fade the brand out, then walk the home screen in underneath it. */
    private void dismissSplash() {
        if (mSplashDone) return;
        mSplashDone = true;
        View splash = mBinding.splash;
        long delay = Math.max(0L, TvMotion.SPLASH_HOLD - (SystemClock.uptimeMillis() - mSplashShownAt));
        splash.animate().alpha(0f).setStartDelay(delay).setDuration(TvMotion.SPLASH_OUT)
                .withEndAction(() -> {
                    splash.setVisibility(View.GONE);
                    splash.setAlpha(1f);
                }).start();
        splash.postDelayed(this::runEntrance, delay);
    }

    private void runEntrance() {
        if (isFinishing() || isDestroyed()) return;
        TvStagger.activity(mBinding.getRoot(), R.id.toolbar, R.id.progressLayout, R.id.keycaps);
    }

    private void showContent() {
        mBinding.progressLayout.showContent();
        dismissSplash();
        if (!mActionHandled) {
            mActionHandled = true;
            checkAction(getIntent());
        }
        setFocus();
        TvStagger.firstScreen(mBinding.recycler);
    }

    private void loadLive(String url) {
        if (isFinishing() || isDestroyed()) return;
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                LiveActivity.start(getActivity());
            }
        });
    }

    private void setFocus() {
        if (showEmptySource()) {
            mBinding.recycler.setSelectedPosition(0);
            mBinding.recycler.post(() -> {
                View card = mBinding.recycler.findViewById(R.id.cardVod);
                if (card != null) card.requestFocus();
                else if (getCurrentFocus() == null) mBinding.navHome.requestFocus();
            });
        } else if (getCurrentFocus() == null) {
            mBinding.navHome.requestFocus();
        }
    }

    private void getVideo() {
        if (mConfigLoading || mConfigFailed || TextUtils.isEmpty(getConfig().getUrl())) {
            updateHero();
            return;
        }
        mResult = Result.empty();
        mLoading = true;
        mConfigError = "";
        updateHero();
        int index = getRecommendIndex();
        boolean gone = mAdapter.indexOf("progress") == -1;
        boolean hasItem = gone && mAdapter.size() > index;
        if (hasItem) mAdapter.removeItems(index, mAdapter.size() - index);
        if (gone) mAdapter.add("progress");
        mViewModel.homeContent();
    }

    private void addVideo(Result result) {
        Style style = result.getStyle(getHome().getStyle());
        if (style.isList()) mAdapter.addAll(mAdapter.size(), result.getList());
        else addGrid(result.getList(), style);
    }

    private void addGrid(List<Vod> items, Style style) {
        List<ListRow> rows = new ArrayList<>();
        VodPresenter presenter = new VodPresenter(this, style);
        for (List<Vod> part : Lists.partition(items, Product.getColumn(style))) {
            ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenter);
            adapter.addAll(0, part);
            rows.add(new ListRow(adapter));
        }
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private HeroPresenter.Item heroItem() {
        Vod vod;
        boolean history = mFocusedHistory != null;
        if (history) {
            vod = new Vod();
            vod.setId(mFocusedHistory.getVodId());
            vod.setName(mFocusedHistory.getVodName());
            vod.setPic(mFocusedHistory.getVodPic());
            vod.setSite(VodConfig.get().getSite(mFocusedHistory.getSiteKey()));
        } else {
            vod = mConfigFailed || mConfigLoading ? null : mResult.getList().stream().filter(v -> !v.isAction() && !TextUtils.isEmpty(v.getId())).findFirst().orElse(null);
        }
        String error = !mConfigError.isEmpty() ? mConfigError : mResult.getMsg();
        return new HeroPresenter.Item(vod, history ? mFocusedHistory.getSiteKey() : getHome().getKey(), history ? mFocusedHistory.getSiteName() : getHome().getName(), !TextUtils.isEmpty(getConfig().getUrl()), history ? false : mLoading || mConfigLoading, history ? false : mConfigFailed, history ? "" : error, history);
    }

    private void updateHero() {
        // Coalesce same-frame invocations: config start/success/error, the
        // SiteViewModel observer and getHistory() all fire during startup and
        // each would issue mAdapter.replace(0, item) plus optional add/remove
        // notifications back-to-back. ItemBridgeAdapter forwards those as
        // pending RecyclerView updates; the layout pass then tries to re-attach
        // a ViewHolder that the previous replace left half-detached, raising
        // IllegalArgumentException ("Called attach on a child which is not
        // detached") in GridLayoutManager.createItem. Posting the actual
        // mutation collapses every queued call into a single, atomic swap.
        if (mAdapter == null || mHeroUpdatePending) return;
        mHeroUpdatePending = true;
        App.post(mHeroUpdate);
    }

    private void applyHeroUpdate() {
        mHeroUpdatePending = false;
        if (mAdapter == null) return;
        boolean empty = showEmptySource();
        Object item = empty ? emptySourceItem() : heroItem();
        if (mAdapter.size() == 0) mAdapter.add(item);
        else mAdapter.replace(0, item);
        if (empty && mAdapter.size() > 1) mAdapter.removeItems(1, mAdapter.size() - 1);
        else if (!empty && mAdapter.indexOf(R.string.home_recommend) < 0) mAdapter.add(R.string.home_recommend);
        syncBackdrop(item);
    }

    /**
     * Pushes the hero's poster into the activity-level backdrop. Called from
     * {@link #applyHeroUpdate()} so every config / history / result change
     * re-syncs the atmosphere without needing the inner hero presenter.
     */
    private void syncBackdrop(Object heroOrEmpty) {
        if (mBinding == null) return;
        if (heroOrEmpty instanceof HeroPresenter.Item) {
            HeroPresenter.Item h = (HeroPresenter.Item) heroOrEmpty;
            updateBackdrop(h.vod(), h.sourceKey());
        } else {
            clearBackdrop();
        }
    }

    /**
     * Extracts the focused item from the outer recycler selection and pushes
     * its poster to the backdrop. Header / function / empty-source rows are
     * ignored so the previous backdrop sticks until a real movie gains focus.
     */
    private void syncBackdropFromSelection(@Nullable RecyclerView.ViewHolder child) {
        if (mBinding == null || child == null) return;
        if (!(child instanceof ItemBridgeAdapter.ViewHolder)) return;
        Object item = ((ItemBridgeAdapter.ViewHolder) child).getItem();
        if (item instanceof Vod) {
            Vod v = (Vod) item;
            updateBackdrop(v, v.getSiteKey());
        } else if (item instanceof HeroPresenter.Item) {
            HeroPresenter.Item h = (HeroPresenter.Item) item;
            updateBackdrop(h.vod(), h.sourceKey());
        }
        // History, Header, Func, EmptySource, Progress → keep current backdrop.
    }

    /** Pushes a poster to the full-screen atmosphere view, or clears it. */
    private void updateBackdrop(Vod vod, String sourceKey) {
        if (mBinding == null) return;
        if (vod == null || TextUtils.isEmpty(vod.getPic())) {
            clearBackdrop();
            return;
        }
        mBinding.atmosphere.setImage(sourceKey, vod.getPic());
    }

    private void clearBackdrop() {
        if (mBinding == null) return;
        mBinding.atmosphere.clear();
    }

    private void showMore() {
        String[] labels = {getString(R.string.home_push), getString(R.string.tv_local), getString(R.string.tv_manage_history), getString(R.string.tv_clear_history), getString(R.string.tv_retry)};
        new MaterialAlertDialogBuilder(this).setTitle(R.string.tv_more).setItems(labels, (dialog, which) -> {
            if (which == 0) PushActivity.start(this);
            else if (which == 1) PermissionUtil.requestFile(this, granted -> {
                if (granted) FileChooser.from(mFileLauncher).show(new String[]{"video/*", "audio/*"});
            });
            else if (which == 2 && mHistoryAdapter.size() > 0) {
                setHistoryDelete(true);
                mBinding.recycler.setSelectedPosition(getHistoryIndex());
                mBinding.recycler.requestFocus();
            } else if (which == 3 && mHistoryAdapter.size() > 0) {
                new MaterialAlertDialogBuilder(this).setMessage(R.string.tv_clear_history_confirm).setPositiveButton(android.R.string.ok, (d, w) -> clearHistory()).setNegativeButton(android.R.string.cancel, null).show();
            } else if (which == 4) onHeroRetry();
        }).show();
    }

    @Override
    public void onHeroClick(Vod vod) {
        // A focused history hero is a lightweight Vod projection; keep the
        // original site key when resuming playback instead of the home source.
        if (mFocusedHistory != null) onItemClick(mFocusedHistory);
        else onItemClick(vod);
    }

    @Override
    public void onHeroBrowse() {
        VodActivity.start(this, mResult);
    }

    @Override
    public void onHeroConfigure() {
        SettingActivity.start(this);
    }

    @Override
    public void onEmptyAction(EmptySourcePresenter.Action action) {
        // Empty-source entry cards route to the most relevant setup flow.
        switch (action) {
            case VOD:
                // Mirror the Settings → 配置接口 (API) flow: a single dialog
                // exposes the QR for the H5 panel and a local URL input, and
                // an H5 submission auto-applies so users on the couch do not
                // have to walk back to the TV for a second confirm click.
                ConfigDialog.create().vod().show(this);
                break;
            case LIVE:
            case DRIVE:
                SettingActivity.start(this);
                break;
            case RESTORE:
                // Grant first, then recover. A refusal lands in finishVaultRequest and is
                // reported there rather than leaving the tap with no visible effect.
                requestVault(this::restoreFromVault);
                break;
        }
    }

    @Override
    public void setConfig(Config config) {
        if (isFinishing() || isDestroyed()) return;
        // Empty-source setup only opens the VOD dialog, but ConfigDialog is
        // type-aware; route every source type through its own loader so the
        // behaviour stays consistent if a future entry card reuses this path.
        switch (config.getType()) {
            case 0:
                VodConfig.load(config, getCallback());
                break;
            case 1:
                LiveConfig.load(config, getCallback());
                break;
            case 2:
                Setting.putWall(0);
                WallConfig.load(config, getCallback());
                break;
        }
    }

    @Override
    public void onHeroRetry() {
        if (mConfigLoading || mLoading) return;
        if (TextUtils.isEmpty(getConfig().getUrl())) onHeroConfigure();
        else if (mConfigFailed) VodConfig.get().load(getCallback());
        else getVideo();
    }

    private void getHistory() {
        getHistory(false);
    }

    private void getHistory(boolean renew) {
        List<History> items = History.get();
        if (mFocusedHistory != null && !items.contains(mFocusedHistory)) mFocusedHistory = null;
        int header = mAdapter.indexOf(R.string.home_history);
        if (header >= 0 && (items.isEmpty() || renew)) mAdapter.removeItems(header, 2);
        if (renew) mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        mHistoryAdapter.setItems(items, new BaseDiffCallback<History>());
        if (!items.isEmpty() && (header < 0 || renew)) {
            mAdapter.add(1, R.string.home_history);
            mAdapter.add(2, new ListRow(mHistoryAdapter));
        }
        updateHero();
    }

    private void setHistoryDelete(boolean delete) {
        mPresenter.setDelete(delete);
        mHistoryAdapter.notifyArrayItemRangeChanged(0, mHistoryAdapter.size());
    }

    private void clearHistory() {
        int header = mAdapter.indexOf(R.string.home_history);
        if (header >= 0) mAdapter.removeItems(header, 2);
        History.clear(VodConfig.getCid());
        mPresenter.setDelete(false);
        mHistoryAdapter.clear();
        mFocusedHistory = null;
        updateHero();
        mBinding.more.requestFocus();
    }

    private int getHistoryIndex() {
        return mAdapter.indexOf(R.string.home_history) + 1;
    }

    private int getRecommendIndex() {
        return mAdapter.indexOf(R.string.home_recommend) + 1;
    }

    private void setLogo() {
        mBinding.logo.setImageResource(R.drawable.ic_logo);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                // BaseConfig emits VOD even on failure. Do not override our
                // callback result using a previously loaded site's stale state.
                // Settings may load a replacement with its own callback.
                if (!mOwnConfigEvent && !mConfigLoading && !getHome().isEmpty()) {
                    mConfigFailed = false;
                    mConfigError = "";
                }
                mOwnConfigEvent = false;
                RefreshEvent.history();
                RefreshEvent.home();
                setLogo();
                break;
            case COMMON:
                updateHero();
                break;
            case BOOT:
                LiveActivity.start(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                getVideo();
                setTitle();
                break;
            case HISTORY:
                getHistory();
                break;
            case SIZE:
                getVideo();
                getHistory(true);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        switch (event.type()) {
            case SEARCH:
                SearchActivity.start(this, event.text());
                break;
            case PUSH:
                VideoActivity.push(this, event.text());
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (VodConfig.get().getConfig().equals(event.config())) {
            VideoActivity.cast(this, event.history());
        } else {
            VodConfig.load(event.config(), getCallback(event));
        }
    }

    private Callback getCallback(CastEvent event) {
        return new Callback() {
            @Override
            public void success() {
                onCastEvent(event);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    @Override
    public void onItemClick(Func item) {
        if (item.getResId() == R.string.home_vod) VodActivity.start(this, mResult);
        else if (item.getResId() == R.string.home_live) LiveActivity.start(this);
        else if (item.getResId() == R.string.home_keep) KeepActivity.start(this);
        else if (item.getResId() == R.string.home_push) PushActivity.start(this);
        else if (item.getResId() == R.string.home_search) SearchActivity.start(this);
        else if (item.getResId() == R.string.home_setting) SettingActivity.start(this);
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) mViewModel.action(getHome().getKey(), item.getAction());
        else if (getHome().isIndex()) CollectActivity.start(this, item.getName());
        else VideoActivity.start(this, getHome().getKey(), item.getId(), item.getName(), item.getPic());
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction()) return false;
        CollectActivity.start(this, item.getName());
        return true;
    }

    /**
     * Receives focus events from any {@link VodPresenter} card (history rows,
     * recommend grid, etc.). The holder publishes this when the card gains
     * D-pad focus; here we mirror the poster into the activity-level
     * atmosphere so the blurred background tracks navigation within a row.
     */
    @Override
    public void onItemFocus(Vod item) {
        if (item == null) return;
        updateBackdrop(item, item.getSiteKey());
    }

    @Override
    public void onItemClick(History item) {
        VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemFocus(History item) {
        if (mPresenter.isDelete() || item.equals(mFocusedHistory)) return;
        mFocusedHistory = item;
        updateHero();
        // History cards reuse the activity-level atmosphere: convert the
        // history record into a Vod projection so the backdrop tracks the
        // focused history card the same way it tracks any other Vod.
        Vod projected = new Vod();
        projected.setPic(item.getVodPic());
        updateBackdrop(projected, item.getSiteKey());
        scheduleDetailPrefetch(item);
    }

    /** Warms the detail cache after focus settles so opening the card skips the network round-trip. */
    private void scheduleDetailPrefetch(History item) {
        App.removeCallbacks(mPrefetch);
        mPrefetchTarget = item;
        App.post(mPrefetch, 250);
    }

    private final Runnable mPrefetch = () -> {
        History item = mPrefetchTarget;
        if (item != null) VideoViewModel.prefetchDetail(item.getSiteKey(), item.getVodId());
    };

    @Override
    public void onItemDelete(History item) {
        mHistoryAdapter.remove(item.delete());
        if (item.equals(mFocusedHistory)) mFocusedHistory = null;
        if (mHistoryAdapter.size() > 0) return;
        int header = mAdapter.indexOf(R.string.home_history);
        if (header >= 0) mAdapter.removeItems(header, 2);
        mPresenter.setDelete(false);
        updateHero();
        mBinding.more.requestFocus();
    }

    @Override
    public boolean onLongClick() {
        if (mPresenter.isDelete()) new MaterialAlertDialogBuilder(this).setMessage(R.string.tv_clear_history_confirm).setPositiveButton(android.R.string.ok, (d, w) -> clearHistory()).setNegativeButton(android.R.string.cancel, null).show();
        else setHistoryDelete(true);
        return true;
    }

    @Override
    public void showDialog() {
        SiteDialog.create().show(this);
    }

    @Override
    public void onRefresh() {
        getVideo();
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        notifyKeycaps(event);
        if (KeyUtil.isActionDown(event) && KeyUtil.isMenuKey(event)) {
            showDialog();
            return true;
        }
        if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event) && mBinding.toolbar.hasFocus()) {
            if (getResources().getConfiguration().fontScale > 1.15f && !mBinding.utilities.hasFocus()) {
                mBinding.sourceRow.requestFocus();
                return true;
            }
            mBinding.recycler.setSelectedPosition(0);
            mBinding.recycler.requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
        if (mPulse != null) mPulse.start();
        // Returning from the file-access settings screen is the only completion signal a
        // refusal produces, so the vault flow is settled here as well as in its callback.
        finishVaultRequest();
        // Defensive: on some Android TV emulators the activity surface is
        // left with invalidate=0 after the splash exit animation finishes,
        // so the empty-source / hero row would stay black even though the
        // adapter has items. Forcing a redraw on resume guarantees the
        // content area paints once focus returns to the activity.
        mBinding.getRoot().post(mBinding.getRoot()::invalidate);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
        if (mPulse != null) mPulse.pause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) mBinding.recycler.post(() -> {
            // Rebinding the Hero while another page is open can leave focus on
            // the invisible grid container. Preserve every real control focus.
            if (hasWindowFocus() && (getCurrentFocus() == null || getCurrentFocus() == mBinding.recycler)) {
                mBinding.navHome.requestFocus();
            }
        });
    }

    @Override
    protected void onBackInvoked() {
        if (mBinding.progressLayout.isProgress()) {
            showContent();
        } else if (mPresenter.isDelete()) {
            setHistoryDelete(false);
        } else if (mBinding.recycler.getSelectedPosition() != 0) {
            mBinding.recycler.scrollToPosition(0);
        } else if (!mBinding.toolbar.hasFocus()) {
            mBinding.navHome.requestFocus();
        } else {
            if (PlaybackService.isRunning()) Util.moveToBackground(this);
            else super.onBackInvoked();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean("home.actionHandled", mActionHandled);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (mPulse != null) mPulse.cancel();
        App.removeCallbacks(mPrefetch);
        App.removeCallbacks(mHeroUpdate);
        if (!isChangingConfigurations()) {
            DLNARendererService.stop(this);
            LiveConfig.get().clear();
            VodConfig.get().clear();
            ConfigVault.backup();
            OkHttp.get().clear();
            Source.get().exit();
            Server.get().stop();
        }
        super.onDestroy();
    }
}
