package com.fongmi.android.tv.ui.activity;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Property;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
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
import com.fongmi.android.tv.metadata.MetadataRepository;
import com.fongmi.android.tv.metadata.MovieIdentity;
import com.fongmi.android.tv.metadata.MovieArtwork;
import com.fongmi.android.tv.metadata.MovieMetadata;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.DLNARendererService;
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
import java.util.concurrent.Future;
import java.util.Optional;

public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, VodPresenter.OnClickListener, FuncPresenter.OnClickListener, HistoryPresenter.OnClickListener, HeroPresenter.Listener, EmptySourcePresenter.Listener, ConfigListener, VaultDialog.Listener {

    private ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mHistoryAdapter;
    private ArrayObjectAdapter mAdapter;
    private HistoryPresenter mPresenter;
    private SiteViewModel mViewModel;
    private Result mResult;
    /** History title represented by the hero; seeded from the newest persisted row. */
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
    /** Reassert the requested default after async content binding and the splash have settled. */
    private boolean mInitialFocusPending;
    /** True once the cold-start brand overlay has completed (or been skipped). */
    private boolean mSplashDone;
    /** A cold start wants the brand moment; the overlay is raised from initView. */
    private boolean mSplashPending;
    /** The first activity frame has drawn, so the brand clock can run. */
    private boolean mSplashRevealed;
    /** The home content settled before the brand clock started. */
    private boolean mContentReady;
    private long mSplashShownAt;
    /** Guards against re-entrant adapter mutations while a previous updateHero is queued. */
    private boolean mHeroUpdatePending;
    /** Keeps the first content rail flush with the usable screen bottom. */
    private boolean mFirstScreenAligned;
    private boolean mFirstScreenAligning;
    /** Generation-guarded, focus-debounced provider artwork lookup. */
    private long mBackdropGeneration;
    private Future<?> mBackdropTask;
    private Vod mBackdropVod;
    private String mBackdropSource = "";
    /** A detail screen confirmed new provider artwork while Home was stopped. */
    private boolean mBackdropRefreshPending;
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
    private final Runnable mBackdropLoad = this::loadBackdropArtwork;
    /** Coalesces startup callbacks so the final content bind cannot steal the default focus. */
    private final Runnable mInitialFocus = () -> {
        if (!mInitialFocusPending || isFinishing() || isDestroyed()) return;
        mInitialFocusPending = false;
        if (!mBinding.navVod.requestFocus()) mBinding.navVod.requestFocusFromTouch();
    };
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
    protected void initView(Bundle savedInstanceState) {
        // The manifest disables the platform preview, so the first app-owned frame must
        // already contain the complete brand composition. Raise it before the first layout
        // and start its clock only after that frame has been allowed to draw.
        if (savedInstanceState == null && TvMotion.motionEnabled(mBinding.splash)) {
            mSplashPending = true;
            raiseSplash();
            scheduleSplashReveal();
        } else mSplashDone = true;
        mActionHandled = savedInstanceState != null && savedInstanceState.getBoolean("home.actionHandled");
        mInitialFocusPending = savedInstanceState == null;
        mResult = Result.empty();
        mClock = Clock.create(mBinding.clock).format("HH:mm");
        // The focused-poster backdrop replaces the hero-scoped atmosphere; hide it
        // entirely when the user opted for their own wallpaper (customWall).
        mBinding.atmosphere.setVisibility(isFilmAtmosphereEnabled() ? View.VISIBLE : View.GONE);
        mBinding.backdrop.setVisibility(isFilmAtmosphereEnabled() ? View.VISIBLE : View.GONE);
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
        sizeNavigationIcons();
        adaptToolbar();
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

    /**
     * code.html gives every visible top-nav SVG the same w-5/h-5 box. Android compound
     * drawables otherwise use the vector's 20dp source size, which renders twice
     * as large as the 1920px reference on the 2x-density TV canvas. Keep the
     * generated Lucide resources untouched and size only these five instances.
     */
    private void sizeNavigationIcons() {
        int size = ResUtil.dp2px(12);
        sizeNavigationIcons(size, mBinding.navVod, mBinding.navLive, mBinding.navKeep, mBinding.navSearch, mBinding.more);
    }

    private void sizeNavigationIcons(int size, TextView... items) {
        for (TextView item : items) {
            Drawable[] drawables = item.getCompoundDrawablesRelative();
            Drawable icon = drawables[0];
            if (icon == null) continue;
            icon.setBounds(0, 0, size, size);
            item.setCompoundDrawablesRelative(icon, drawables[1], drawables[2], drawables[3]);
        }
    }

    @Override
    protected void initEvent() {
        mBinding.sourceRow.setOnClickListener(v -> showDialog());
        // Home remains the current route even though its redundant tab is hidden. Vod is only
        // the initial focus target, so it must not retain a false selected-route state after
        // focus moves into the page content.
        mBinding.navHome.setSelected(true);
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
                // Keep the selected recent title while the hero itself is focused. The
                // history-backed hero is the cold-start landing state; only entering a
                // different content rail should hand the hero back to recommendations.
                if (position != getHistoryIndex() && position != 0 && mFocusedHistory != null) {
                    mFocusedHistory = null;
                    updateHero();
                }
                if (position == 0 && mBinding.recycler.hasFocus()) {
                    mBinding.recycler.post(HomeActivity.this::focusHeroBrowse);
                }
                syncBackdropFromSelection(child);
            }
        });
    }

    private void focusHeroBrowse() {
        View browse = mBinding.recycler.findViewById(R.id.secondary);
        if (browse != null && browse.isFocusable()) browse.requestFocus();
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
        // The first rail is deliberately aligned to the viewport bottom. Keep
        // that alignment when focus enters its first card; the next down press
        // will still reveal the following rail because it is outside the
        // viewport. ALIGNED would recenter the first rail immediately because
        // the focused card's zoomed bounds extend below the fold.
        mBinding.recycler.setFocusScrollStrategy(VerticalGridView.FOCUS_SCROLL_ITEM);
        // Mirror the project's other RecyclerViews (KeepActivity, FileActivity, the dialogs):
        // the outer container's size does not depend on item content, and the first-screen
        // entrance already runs view-level alpha animations via TvStagger.firstScreen(), so
        // suppressing RecyclerView's own ItemAnimator stops it from leaving the
        // GridLayoutManager fastRelayout path with an attached child the next time the
        // RecyclerView is re-laid out while a fling is still in flight.
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.addOnLayoutChangeListener((view, left, top, right, bottom,
                                                      oldLeft, oldTop, oldRight, oldBottom) -> alignFirstScreenRow());
    }

    /**
     * Expands only the hero's quiet space so the first film rail ends at the
     * content viewport bottom. Card geometry remains untouched and subsequent
     * rails stay below the fold, independent of TV density or poster ratio.
     */
    private void alignFirstScreenRow() {
        if (mBinding == null || mAdapter == null || mFirstScreenAligned || mFirstScreenAligning
                || mAdapter.size() <= 2 || !(mAdapter.get(2) instanceof ListRow)) return;
        RecyclerView.ViewHolder heroHolder = mBinding.recycler.findViewHolderForAdapterPosition(0);
        RecyclerView.ViewHolder railHolder = mBinding.recycler.findViewHolderForAdapterPosition(2);
        if (heroHolder == null || railHolder == null || heroHolder.itemView.getTop() < 0) return;
        int viewportBottom = mBinding.recycler.getHeight() - mBinding.recycler.getPaddingBottom();
        int delta = viewportBottom - railHolder.itemView.getBottom();
        if (Math.abs(delta) <= ResUtil.dp2px(1)) {
            mFirstScreenAligned = true;
            return;
        }
        int minimum = ResUtil.dp2px(152);
        int target = Math.max(minimum, heroHolder.itemView.getHeight() + delta);
        if (target == heroHolder.itemView.getMinimumHeight()) {
            mFirstScreenAligned = true;
            return;
        }
        mFirstScreenAligning = true;
        heroHolder.itemView.setMinimumHeight(target);
        heroHolder.itemView.requestLayout();
        mBinding.recycler.post(() -> {
            mFirstScreenAligning = false;
            alignFirstScreenRow();
        });
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
        boolean restorable = !ConfigVault.isWritable()
                && PermissionUtil.allFilesAccess(this) != PermissionUtil.AllFilesAccess.UNSUPPORTED;
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
                maybePromptRestorePermission();
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
        if (PermissionUtil.allFilesAccess(this) == PermissionUtil.AllFilesAccess.UNSUPPORTED) {
            Notify.show(R.string.tv_vault_unsupported);
            updateHero();
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
        if (PermissionUtil.allFilesAccess(this) == PermissionUtil.AllFilesAccess.UNSUPPORTED) return;
        if (getSupportFragmentManager().findFragmentByTag(VaultDialog.TAG) != null) return;
        VaultDialog.create().show(this);
    }

    /** Explain why an empty fresh install needs access before asking the system for it. */
    private void maybePromptRestorePermission() {
        if (!ConfigVault.shouldPromptRestore(hasConfiguredSource())) return;
        if (PermissionUtil.allFilesAccess(this) != PermissionUtil.AllFilesAccess.REQUESTABLE) return;
        if (getSupportFragmentManager().findFragmentByTag(VaultDialog.TAG) != null) return;
        VaultDialog.create().restore().show(this);
    }

    @Override
    public void onVaultEnable() {
        requestVault(() -> {
            if (isFinishing() || isDestroyed()) return;
            ConfigVault.save();
            Notify.show(R.string.tv_vault_enabled);
        });
    }

    @Override
    public void onVaultRestore() {
        requestVault(this::restoreFromVault);
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
                scheduleInitialFocus();
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
     * Puts the brand overlay up. Called from {@code initView} so it is measured, laid out
     * and drawn during the very first traversal.
     * Nothing here animates: the composition is complete from the frame it first draws,
     * because a gradual build-up is read as the backdrop arriving before the mark.
     */
    private void raiseSplash() {
        // Set before layout so the artwork is already framed correctly on its first draw.
        View backdrop = mBinding.splashBackdrop;
        backdrop.setScaleX(TvMotion.SPLASH_BACKDROP_SCALE);
        backdrop.setScaleY(TvMotion.SPLASH_BACKDROP_SCALE);
        mBinding.splash.setVisibility(View.VISIBLE);
    }

    /** Starts on the first animation frame, after attachment makes the overlay drawable. */
    private void scheduleSplashReveal() {
        View splash = mBinding.splash;
        splash.postOnAnimation(HomeActivity.this::revealSplash);
    }

    /**
     * Starts the artwork drift and dismissal clock after the complete composition has drawn.
     */
    private void revealSplash() {
        if (!mSplashPending || isFinishing() || isDestroyed()) return;
        mSplashPending = false;
        mSplashRevealed = true;
        mSplashShownAt = SystemClock.uptimeMillis();
        Interpolator enter = AnimationUtils.loadInterpolator(this, R.interpolator.tv_interp_enter);
        View backdrop = mBinding.splashBackdrop;
        animate(backdrop, View.SCALE_X, TvMotion.SPLASH_BACKDROP_SCALE, 1f, TvMotion.SPLASH_BACKDROP, enter);
        animate(backdrop, View.SCALE_Y, TvMotion.SPLASH_BACKDROP_SCALE, 1f, TvMotion.SPLASH_BACKDROP, enter);
        mBinding.splash.postDelayed(this::dismissSplash, TvMotion.SPLASH_MAX);
        // The config may settle before the first brand frame is committed.
        if (mContentReady) dismissSplash();
    }

    /**
     * Dissolve the brand into the home. The frame fades while pushing slightly toward the
     * viewer, and the mark and wordmark leave ahead of the artwork so it empties from the
     * centre outward instead of dropping as one card. The home entrance starts on the same
     * frame, so the two cross-dissolve rather than cutting.
     */
    private void dismissSplash() {
        // Do not dismiss until the complete brand composition has reached the viewer.
        if (mSplashDone || !mSplashRevealed) return;
        mSplashDone = true;
        View splash = mBinding.splash;
        View content = mBinding.splashContent;
        long delay = Math.max(0L, TvMotion.SPLASH_HOLD - (SystemClock.uptimeMillis() - mSplashShownAt));
        splash.postDelayed(this::runEntrance, delay);
        splash.animate().alpha(0f).scaleX(TvMotion.SPLASH_OUT_SCALE).scaleY(TvMotion.SPLASH_OUT_SCALE)
                .setStartDelay(delay).setDuration(TvMotion.SPLASH_OUT)
                .setInterpolator(AnimationUtils.loadInterpolator(this, R.interpolator.tv_interp_decelerate))
                .withEndAction(() -> {
                    splash.setVisibility(View.GONE);
                    splash.setAlpha(1f);
                    splash.setScaleX(1f);
                    splash.setScaleY(1f);
                    mBinding.splashBackdrop.setAlpha(1f);
                    content.setAlpha(1f);
                    content.setScaleX(1f);
                    content.setScaleY(1f);
                }).start();
        content.animate().alpha(0f)
                .scaleX(TvMotion.SPLASH_CONTENT_OUT_SCALE).scaleY(TvMotion.SPLASH_CONTENT_OUT_SCALE)
                .setStartDelay(delay).setDuration(TvMotion.SPLASH_CONTENT_OUT)
                .setInterpolator(AnimationUtils.loadInterpolator(this, R.interpolator.tv_interp_decelerate)).start();
    }

    /**
     * Drives a single float property. Written out rather than chained because
     * {@link ValueAnimator#setInterpolator} returns void on current SDKs, unlike the
     * {@code ViewPropertyAnimator} setters, which do return themselves.
     */
    private void animate(View target, Property<View, Float> property, float from, float to, long duration, Interpolator interpolator) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(target, property, from, to);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);
        animator.start();
    }

    private void runEntrance() {
        if (isFinishing() || isDestroyed()) return;
        TvStagger.activity(mBinding.getRoot(), R.id.toolbar, R.id.progressLayout, R.id.keycaps);
    }

    private void showContent() {
        mBinding.progressLayout.showContent();
        mContentReady = true;
        dismissSplash();
        if (!mActionHandled) {
            mActionHandled = true;
            checkAction(getIntent());
        }
        if (mInitialFocusPending) scheduleInitialFocus();
        else {
            setFocus();
        }
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
        if (getCurrentFocus() != null) return;
        if (showEmptySource()) {
            mBinding.recycler.setSelectedPosition(0);
            mBinding.recycler.post(() -> {
                View card = mBinding.recycler.findViewById(R.id.cardVod);
                if (card != null) card.requestFocus();
                else mBinding.navVod.requestFocus();
            });
        } else {
            mBinding.navVod.requestFocus();
        }
    }

    private void scheduleInitialFocus() {
        App.post(mInitialFocus, 250);
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
        mFirstScreenAligned = false;
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

    /** Pushes a poster to the fallback atmosphere and schedules provider artwork. */
    private void updateBackdrop(Vod vod, String sourceKey) {
        if (mBinding == null) return;
        if (!isFilmAtmosphereEnabled()) {
            clearBackdrop();
            return;
        }
        if (vod == null || TextUtils.isEmpty(vod.getPic())) {
            clearBackdrop();
            return;
        }
        String nextSource = sourceKey == null ? "" : sourceKey;
        if (nextSource.equals(mBackdropSource) && mBackdropVod != null
                && vod.getId().equals(mBackdropVod.getId()) && vod.getPic().equals(mBackdropVod.getPic())) return;
        mBinding.atmosphere.setImage(sourceKey, vod.getPic());
        mBinding.backdrop.fadeOut();
        mBackdropGeneration++;
        cancelBackdropTask();
        mBackdropVod = vod;
        mBackdropSource = nextSource;
        App.post(mBackdropLoad, 450);
    }

    private void clearBackdrop() {
        if (mBinding == null) return;
        mBackdropGeneration++;
        cancelBackdropTask();
        App.removeCallbacks(mBackdropLoad);
        cancelBackdropTask();
        mBackdropVod = null;
        mBackdropSource = "";
        mBinding.atmosphere.clear();
        mBinding.backdrop.clear();
    }

    private void loadBackdropArtwork() {
        Vod vod = mBackdropVod;
        String sourceKey = mBackdropSource;
        long generation = mBackdropGeneration;
        if (vod == null || TextUtils.isEmpty(sourceKey) || TextUtils.isEmpty(vod.getId()) || TextUtils.isEmpty(vod.getName())) return;
        MovieIdentity identity = MovieIdentity.from(sourceKey, vod.getId(), vod);
        mBackdropTask = MetadataRepository.get().loadArtwork(identity, vod, metadata -> {
            if (mBinding == null || generation != mBackdropGeneration) return;
            mBackdropTask = null;
            List<String> artworks = artworkUrls(metadata);
            if (!artworks.isEmpty()) {
                mBinding.backdrop.setCarousel(sourceKey + ":" + vod.getId(), artworks, 0, null);
            } else {
                mBinding.backdrop.clear();
            }
        });
    }

    /** Re-resolves the current title even when its source/card identity did not change. */
    private void reloadBackdropArtwork() {
        if (mBinding == null || mBackdropVod == null || TextUtils.isEmpty(mBackdropSource)) return;
        mBackdropGeneration++;
        cancelBackdropTask();
        App.removeCallbacks(mBackdropLoad);
        mBinding.backdrop.fadeOut();
        App.post(mBackdropLoad);
    }

    private static List<String> artworkUrls(MovieMetadata metadata) {
        if (metadata == null) return List.of();
        List<String> urls = new ArrayList<>();
        if (!TextUtils.isEmpty(metadata.getBackdrop())) urls.add(metadata.getBackdrop());
        for (MovieArtwork artwork : metadata.getArtworks()) {
            if (!TextUtils.isEmpty(artwork.getUrl()) && !urls.contains(artwork.getUrl())) urls.add(artwork.getUrl());
        }
        return urls;
    }

    private void cancelBackdropTask() {
        if (mBackdropTask != null) {
            mBackdropTask.cancel(true);
            mBackdropTask = null;
        }
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
        List<History> items = History.getRecentAll();
        // The first recent item is the hero's initial subject. Do this before the
        // coalesced adapter update so cold start never flashes the recommendation
        // hero before history has been attached.
        if (items.isEmpty()) {
            mFocusedHistory = null;
        } else {
            int focused = mFocusedHistory == null ? -1 : items.indexOf(mFocusedHistory);
            // Preserve the focused key but replace the object with the fresh DB
            // row so a provider-confirmed poster/name is not held stale.
            if (focused < 0) mFocusedHistory = items.get(0);
            else mFocusedHistory = items.get(focused);
        }
        int header = mAdapter.indexOf(R.string.home_history);
        if (renew || (header >= 0) == items.isEmpty()) mFirstScreenAligned = false;
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
        mFirstScreenAligned = false;
        History.clearAll();
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
            case METADATA:
                mBackdropRefreshPending = true;
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
        // Home recommendation items usually omit siteKey because the whole
        // result belongs to the active home site. Falling back here keeps the
        // metadata identity stable and allows provider artwork resolution.
        String sourceKey = TextUtils.isEmpty(item.getSiteKey()) ? getHome().getKey() : item.getSiteKey();
        updateBackdrop(item, sourceKey);
    }

    @Override
    public void onItemClick(History item) {
        if (item.getCid() == VodConfig.getCid()) {
            openHistory(item);
            return;
        }
        Config config = Config.find(item.getCid());
        if (config == null) {
            Notify.show(R.string.tv_history_source_missing);
            CollectActivity.start(this, item.getVodName());
            return;
        }
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tv_history_switch_title)
                .setMessage(getString(R.string.tv_history_switch_message, config.getDesc(), getConfig().getDesc()))
                .setPositiveButton(R.string.tv_history_switch_confirm, (dialog, which) -> loadHistoryConfig(getConfig(), config, item))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        alertDialog.setOnShowListener(ignored -> alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus());
        alertDialog.show();
    }

    private void loadHistoryConfig(Config previous, Config target, History item) {
        VodConfig.load(target, new Callback() {
            @Override
            public void start() {
                Notify.progress(getActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                if (!isFinishing() && !isDestroyed()) openHistory(item);
            }

            @Override
            public void error(String msg) {
                restoreHistoryConfig(previous, msg);
            }
        });
    }

    private void restoreHistoryConfig(Config previous, String msg) {
        VodConfig.load(previous, new Callback() {
            @Override
            public void success() {
                Notify.dismiss();
                Notify.show(msg);
            }

            @Override
            public void error(String restoreError) {
                Notify.dismiss();
                Notify.show(TextUtils.isEmpty(restoreError) ? msg : restoreError);
            }
        });
    }

    private void openHistory(History item) {
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
        projected.setId(item.getVodId());
        projected.setName(item.getVodName());
        projected.setPic(item.getVodPic());
        updateBackdrop(projected, item.getSiteKey());
        if (item.getCid() == VodConfig.getCid()) scheduleDetailPrefetch(item);
        else {
            App.removeCallbacks(mPrefetch);
            mPrefetchTarget = null;
        }
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
        mFirstScreenAligned = false;
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
        // KeyUtil reports Menu on ACTION_UP so one physical press opens exactly one dialog.
        // Combining it with ACTION_DOWN would be impossible and made the shortcut inert.
        if (KeyUtil.isMenuKey(event)) {
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
            // The browse action is deliberately the first hero control. Request it
            // after RecyclerView has attached the selected holder so every nav-down
            // transition lands on the same predictable target.
            mBinding.recycler.post(this::focusHeroBrowse);
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
        if (mBackdropRefreshPending) {
            mBackdropRefreshPending = false;
            mBinding.recycler.post(this::reloadBackdropArtwork);
        }
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
                mBinding.navVod.requestFocus();
            }
        });
    }

    @Override
    protected void onBackInvoked() {
        if (mPresenter.isDelete()) {
            setHistoryDelete(false);
        } else {
            // Back is still the task-root exit gesture, but the first press also restores the
            // Home landing state. This mirrors the usual TV "back to top" affordance without
            // consuming the press: the shared callback still shows the exit hint, and a second
            // press within its window exits exactly as before.
            focusHomeRoot();
            super.onBackInvoked();
        }
    }

    private void focusHomeRoot() {
        if (mBinding.recycler.getAdapter() != null && mBinding.recycler.getAdapter().getItemCount() > 0) {
            mBinding.recycler.setSelectedPosition(0);
            mBinding.recycler.scrollToPosition(0);
        }
        mBinding.navVod.requestFocus();
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
        App.removeCallbacks(mBackdropLoad);
        App.removeCallbacks(mInitialFocus);
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
