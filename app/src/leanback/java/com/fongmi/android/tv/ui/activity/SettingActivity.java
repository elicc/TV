package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivitySettingBinding;
import com.fongmi.android.tv.db.BackupManager;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.LiveListener;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.dialog.DohDialog;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.RestoreDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.TvTheme;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class SettingActivity extends BaseActivity implements ConfigListener, SiteListener, LiveListener, DohDialog.Listener {

    private ActivitySettingBinding mBinding;
    private String[] size;
    private AlertDialog themeDialog;
    private int restoreFocusId = View.NO_ID;
    private static final String STATE_FOCUS = "setting_focus";

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingActivity.class));
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingBinding.inflate(getLayoutInflater());
    }

    @Override
    protected boolean customWall() {
        return false;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        restoreFocusId = savedInstanceState == null ? R.id.navContent : savedInstanceState.getInt(STATE_FOCUS, R.id.navContent);
        mBinding.getRoot().post(() -> {
            View target = findViewById(restoreFocusId);
            if (target == null || !target.requestFocus()) mBinding.navContent.requestFocus();
            restoreFocusId = View.NO_ID;
        });
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        mBinding.wallUrl.setText(WallConfig.getDesc());
        mBinding.versionText.setText(BuildConfig.VERSION_NAME);
        setCacheText();
        setOtherText();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        View focused = getCurrentFocus();
        outState.putInt(STATE_FOCUS, restoreFocusId == R.id.skin ? R.id.skin : focused == null ? R.id.navContent : focused.getId());
        super.onSaveInstanceState(outState);
    }

    private void setOtherText() {
        mBinding.skinText.setText(TvTheme.getSkinLabels(this)[TvTheme.getSkin()]);
        mBinding.atmosphereText.setText(Setting.getSwitch(TvTheme.isAtmosphereEnabled()));
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.incognitoText.setText(Setting.getSwitch(Setting.isIncognito()));
        mBinding.sizeText.setText((size = ResUtil.getStringArray(R.array.select_size))[PlayerSetting.getSize()]);
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                mBinding.cacheText.setText(result);
            }
        });
    }

    @Override
    protected void initEvent() {
        mBinding.settingBack.setOnClickListener(view -> finish());
        bindSectionNavigation(mBinding.navContent, mBinding.settingsScroll, mBinding.sectionContent, mBinding.vod);
        bindSectionNavigation(mBinding.navAppearance, mBinding.settingsScroll, mBinding.sectionAppearance, mBinding.skin);
        bindSectionNavigation(mBinding.navPlayback, mBinding.settingsScroll, mBinding.sectionPlayback, mBinding.player);
        bindSectionNavigation(mBinding.navData, mBinding.utilityScroll, mBinding.sectionData, mBinding.incognito);
        bindSectionNavigation(mBinding.navAbout, mBinding.utilityScroll, mBinding.sectionAbout, mBinding.version);
        bindSectionFocus(mBinding.navContent, mBinding.vod, mBinding.vodHome, mBinding.vodHistory, mBinding.live, mBinding.liveHome, mBinding.liveHistory, mBinding.doh);
        bindSectionFocus(mBinding.navAppearance, mBinding.skin, mBinding.atmosphere, mBinding.wall, mBinding.wallDefault, mBinding.wallRefresh, mBinding.size);
        bindSectionFocus(mBinding.navPlayback, mBinding.player, mBinding.danmaku);
        bindSectionFocus(mBinding.navData, mBinding.incognito, mBinding.backup, mBinding.restore, mBinding.cache);
        bindSectionFocus(mBinding.navAbout, mBinding.version);
        mBinding.skin.setOnClickListener(this::setSkin);
        mBinding.atmosphere.setOnClickListener(this::setAtmosphere);
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.live.setOnClickListener(this::onLive);
        mBinding.wall.setOnClickListener(this::onWall);
        mBinding.size.setOnClickListener(this::setSize);
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.player.setOnClickListener(this::onPlayer);
        mBinding.danmaku.setOnClickListener(this::onDanmaku);
        mBinding.restore.setOnClickListener(this::onRestore);
        mBinding.version.setOnClickListener(this::onVersion);
        mBinding.vod.setOnLongClickListener(this::onVodEdit);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.live.setOnLongClickListener(this::onLiveEdit);
        mBinding.liveHome.setOnClickListener(this::onLiveHome);
        mBinding.wall.setOnLongClickListener(this::onWallEdit);
        mBinding.incognito.setOnClickListener(this::setIncognito);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.liveHistory.setOnClickListener(this::onLiveHistory);
        mBinding.wallDefault.setOnClickListener(this::setWallDefault);
        mBinding.wallRefresh.setOnClickListener(this::setWallRefresh);
        mBinding.wallRefresh.setOnLongClickListener(this::onWallHistory);
    }

    private void bindSectionNavigation(View navigation, NestedScrollView scroller, View section, View target) {
        navigation.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) return;
            selectNavigation(view);
            scrollToSection(scroller, section);
        });
        navigation.setOnClickListener(view -> {
            selectNavigation(view);
            scrollToSection(scroller, section);
            target.requestFocus();
        });
    }

    private void bindSectionFocus(View navigation, View... items) {
        for (View item : items) {
            item.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) selectNavigation(navigation);
            });
        }
    }

    private void selectNavigation(View selected) {
        mBinding.navContent.setSelected(mBinding.navContent == selected);
        mBinding.navAppearance.setSelected(mBinding.navAppearance == selected);
        mBinding.navPlayback.setSelected(mBinding.navPlayback == selected);
        mBinding.navData.setSelected(mBinding.navData == selected);
        mBinding.navAbout.setSelected(mBinding.navAbout == selected);
    }

    private void scrollToSection(NestedScrollView scroller, View section) {
        scroller.post(() -> scroller.smoothScrollTo(0, section.getTop()));
    }

    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file")) {
            PermissionUtil.requestFile(this, allGranted -> load(config));
        } else {
            load(config);
        }
    }

    private void load(Config config) {
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

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(getActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                setCacheText();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    private void onVod(View view) {
        ConfigDialog.create().vod().show(this);
    }

    private void onLive(View view) {
        ConfigDialog.create().live().show(this);
    }

    private void onWall(View view) {
        ConfigDialog.create().wall().show(this);
    }

    private boolean onVodEdit(View view) {
        ConfigDialog.create().vod().edit().show(this);
        return true;
    }

    private boolean onLiveEdit(View view) {
        ConfigDialog.create().live().edit().show(this);
        return true;
    }

    private boolean onWallEdit(View view) {
        ConfigDialog.create().wall().edit().show(this);
        return true;
    }

    private void onVodHome(View view) {
        SiteDialog.create().action().show(this);
    }

    private void onLiveHome(View view) {
        LiveDialog.create().action().show(this);
    }

    private void onVodHistory(View view) {
        HistoryDialog.create().vod().show(this);
    }

    private void onLiveHistory(View view) {
        HistoryDialog.create().live().show(this);
    }

    private void onPlayer(View view) {
        SettingPlayerActivity.start(this);
    }

    private void onDanmaku(View view) {
        SettingDanmakuActivity.start(this);
    }

    private void onVersion(View view) {
        Updater.create().force().start(this);
    }

    private void setWallDefault(View view) {
        Setting.putWall(Setting.getWall() == 4 ? 1 : Setting.getWall() + 1);
        Setting.putWallType(0);
        ConfigEvent.wall();
    }

    private void setWallRefresh(View view) {
        Setting.putWall(0);
        WallConfig.get().load(getCallback());
    }

    private boolean onWallHistory(View view) {
        HistoryDialog.create().wall().show(this);
        return true;
    }

    private void setSkin(View view) {
        themeDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tv_setting_skin)
                .setSingleChoiceItems(TvTheme.getSkinLabels(this), TvTheme.getSkin(), (dialog, which) -> {
                    dialog.dismiss();
                    if (which == TvTheme.getSkin()) return;
                    restoreFocusId = R.id.skin;
                    TvTheme.setSkin(which);
                    recreate();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .setOnDismissListener(dialog -> mBinding.skin.requestFocus())
                .show();
    }

    private void setAtmosphere(View view) {
        TvTheme.setAtmosphereEnabled(!TvTheme.isAtmosphereEnabled());
        mBinding.atmosphereText.setText(Setting.getSwitch(TvTheme.isAtmosphereEnabled()));
    }

    @Override
    protected void onDestroy() {
        if (themeDialog != null) themeDialog.dismiss();
        super.onDestroy();
    }

    private void setIncognito(View view) {
        Setting.putIncognito(!Setting.isIncognito());
        mBinding.incognitoText.setText(Setting.getSwitch(Setting.isIncognito()));
    }

    private void setSize(View view) {
        int index = (PlayerSetting.getSize() + 1) % size.length;
        mBinding.sizeText.setText(size[index]);
        PlayerSetting.putSize(index);
        RefreshEvent.size();
    }

    private void setDoh(View view) {
        DohDialog.create().index(getDohIndex()).show(this);
    }

    @Override
    public void setDoh(Doh doh) {
        OkHttp.dns().setDoh(doh);
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
    }

    private void onCache(View view) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                setCacheText();
            }
        });
    }

    private void onBackup(View view) {
        PermissionUtil.requestFile(this, allGranted -> BackupManager.backup(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.backup_success);
            }

            @Override
            public void error() {
                Notify.show(R.string.backup_fail);
            }
        }));
    }

    private void onRestore(View view) {
        PermissionUtil.requestFile(this, allGranted -> RestoreDialog.create().callback(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.restore_success);
                setOtherText();
                initConfig();
            }

            @Override
            public void error() {
                Notify.show(R.string.restore_fail);
            }
        }).show(this));
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init().load();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() != ConfigEvent.Type.COMMON) return;
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        mBinding.wallUrl.setText(WallConfig.getDesc());
    }

}
