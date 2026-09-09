package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
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
import com.fongmi.android.tv.db.BackupManager;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.DLNARendererService;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.adapter.BaseDiffCallback;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.custom.CustomTitleView;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.presenter.FuncPresenter;
import com.fongmi.android.tv.ui.presenter.HeaderPresenter;
import com.fongmi.android.tv.ui.presenter.HeroPresenter;
import com.fongmi.android.tv.ui.presenter.HistoryPresenter;
import com.fongmi.android.tv.ui.presenter.ProgressPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
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

public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, VodPresenter.OnClickListener, FuncPresenter.OnClickListener, HistoryPresenter.OnClickListener, HeroPresenter.Listener {

    private ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mHistoryAdapter;
    private ArrayObjectAdapter mAdapter;
    private HistoryPresenter mPresenter;
    private SiteViewModel mViewModel;
    private Result mResult;
    private Clock mClock;
    private boolean mLoading;
    private boolean mConfigLoading;
    private boolean mConfigFailed;
    private boolean mOwnConfigEvent;
    private String mConfigError = "";
    private boolean mActionHandled;
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
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mActionHandled = savedInstanceState != null && savedInstanceState.getBoolean("home.actionHandled");
        mResult = Result.empty();
        mClock = Clock.create(mBinding.clock);
        mBinding.progressLayout.showProgress();
        PermissionUtil.requestNotify(this);
        DLNARendererService.start(this);
        Updater.create().start(this);
        setRecyclerView();
        setViewModel();
        setAdapter();
        initConfig();
        setTitle();
        setLogo();
        adaptToolbar();
        if (savedInstanceState == null) mBinding.navHome.requestFocus();
    }

    private void adaptToolbar() {
        boolean largeText = getResources().getConfiguration().fontScale > 1.15f;
        mBinding.toolbar.setOrientation(largeText ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        mBinding.navSpacer.setVisibility(largeText ? View.GONE : View.VISIBLE);
        mBinding.clock.setVisibility(largeText ? View.GONE : View.VISIBLE);
        if (largeText) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mBinding.utilities.getLayoutParams();
            params.gravity = android.view.Gravity.END;
            mBinding.utilities.setLayoutParams(params);
        }
    }

    @Override
    protected void initEvent() {
        mBinding.title.setOnClickListener(v -> showDialog());
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
        selector.addPresenter(HeroPresenter.Item.class, new HeroPresenter(this));
        selector.addPresenter(String.class, new ProgressPresenter());
        selector.addPresenter(Vod.class, new VodPresenter(this, Style.list()));
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), FuncPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16, FocusHighlight.ZOOM_FACTOR_SMALL, HorizontalGridView.FOCUS_SCROLL_ALIGNED), HistoryPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
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
        mAdapter.add(heroItem());
        mAdapter.add(R.string.home_recommend);
    }

    private void setTitle() {
        List<String> items = Arrays.asList(getHome().getName(), getConfig().getName(), getString(R.string.app_name));
        Optional<String> optional = items.stream().filter(s -> !TextUtils.isEmpty(s)).findFirst();
        optional.ifPresent(s -> mBinding.title.setText(s));
        mBinding.title.setContentDescription(getString(R.string.tv_source) + "：" + mBinding.title.getText());
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init();
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

    private void showContent() {
        mBinding.progressLayout.showContent();
        if (!mActionHandled) {
            mActionHandled = true;
            checkAction(getIntent());
        }
        setFocus();
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
        mBinding.title.setSelected(false);
        App.post(() -> mBinding.title.setFocusable(true), 500);
        if (getCurrentFocus() == null) mBinding.navHome.requestFocus();
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
        Vod vod = mConfigFailed || mConfigLoading ? null : mResult.getList().stream().filter(v -> !v.isAction() && !TextUtils.isEmpty(v.getId())).findFirst().orElse(null);
        String error = !mConfigError.isEmpty() ? mConfigError : mResult.getMsg();
        return new HeroPresenter.Item(vod, getHome().getKey(), getHome().getName(), !TextUtils.isEmpty(getConfig().getUrl()), mLoading || mConfigLoading, mConfigFailed, error, mHistoryAdapter != null && mHistoryAdapter.size() > 0);
    }

    private void updateHero() {
        if (mAdapter != null && mAdapter.size() > 0) mAdapter.replace(0, heroItem());
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
        onItemClick(vod);
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

    @Override
    public void onItemClick(History item) {
        VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(History item) {
        mHistoryAdapter.remove(item.delete());
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
        if (KeyUtil.isActionDown(event) && KeyUtil.isMenuKey(event)) {
            showDialog();
            return true;
        }
        if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event) && mBinding.toolbar.hasFocus()) {
            if (getResources().getConfiguration().fontScale > 1.15f && !mBinding.utilities.hasFocus()) {
                mBinding.title.requestFocus();
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
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
        if (!isChangingConfigurations()) {
            DLNARendererService.stop(this);
            LiveConfig.get().clear();
            VodConfig.get().clear();
            BackupManager.backup();
            OkHttp.get().clear();
            Source.get().exit();
            Server.get().stop();
        }
        super.onDestroy();
    }
}
