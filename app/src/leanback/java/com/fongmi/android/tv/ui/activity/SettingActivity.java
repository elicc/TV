package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
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
import com.fongmi.android.tv.db.ConfigVault;
import com.fongmi.android.tv.db.VaultPolicy;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.LiveListener;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.TvKeycapsBar;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
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

    private static final SimpleDateFormat TIME = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    /** Set while a file-access grant is in flight; a refusal only surfaces on resume. */
    private boolean mVaultPending;
    private Runnable mVaultOnGranted;

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
        setActiveSourceText();
        setKeycaps();
        setCacheText();
        setOtherText();
        setVaultText();
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

    /**
     * The only place a vault write failure becomes visible. A silent backup that never lands is
     * indistinguishable from a working one until the day it is needed, so the row states the
     * mechanism's real condition rather than just offering a button.
     */
    private void setVaultText() {
        if (PermissionUtil.allFilesAccess(this) == PermissionUtil.AllFilesAccess.UNSUPPORTED) {
            mBinding.vaultText.setText(R.string.tv_vault_status_unsupported);
        } else if (!ConfigVault.isWritable()) {
            mBinding.vaultText.setText(R.string.tv_vault_status_off);
        } else if (ConfigVault.state() == VaultPolicy.State.IO_ERROR) {
            mBinding.vaultText.setText(R.string.tv_vault_status_io);
        } else if (ConfigVault.lastOk() > 0) {
            mBinding.vaultText.setText(getString(R.string.tv_vault_status_on, TIME.format(new Date(ConfigVault.lastOk()))));
        } else {
            mBinding.vaultText.setText(R.string.tv_vault_status_idle);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from the file-access settings screen is the only completion signal a
        // refusal produces, so the pending action is settled here as well as in its callback.
        finishVaultRequest();
    }

    private void requestVault(Runnable onGranted) {
        if (ConfigVault.isWritable()) {
            onGranted.run();
            return;
        }
        if (PermissionUtil.allFilesAccess(this) == PermissionUtil.AllFilesAccess.UNSUPPORTED) {
            setVaultText();
            Notify.show(R.string.tv_vault_unsupported);
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
        setVaultText();
        if (!ConfigVault.isWritable()) {
            Notify.show(R.string.tv_vault_denied);
            return;
        }
        // onResume settles this too, and the base class can recreate the activity from there,
        // so the continuation must not open a dialog on a window that is already going away.
        if (action != null && !isFinishing() && !isDestroyed()) action.run();
    }

    private void onVault(View view) {
        requestVault(() -> {
            if (isFinishing() || isDestroyed()) return;
            ConfigVault.save();
            ConfigVault.backup();
            setVaultText();
            Notify.show(R.string.tv_vault_enabled);
        });
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                mBinding.cacheText.setText(result);
            }
        });
    }

    private void setActiveSourceText() {
        String source = VodConfig.getDesc();
        boolean configured = !TextUtils.isEmpty(source);
        mBinding.activeSourceText.setCompoundDrawablesRelativeWithIntrinsicBounds(configured ? R.drawable.tv_setting_status_dot : R.drawable.tv_setting_status_dot_idle, 0, 0, 0);
        mBinding.activeSourceText.setText(getString(R.string.tv_setting_active_source, configured ? source : getString(R.string.tv_setting_unconfigured), BuildConfig.VERSION_NAME));
    }

    private void setKeycaps() {
        TvKeycapsBar.Cap[] caps = {
                TvKeycapsBar.Cap.of(getString(R.string.tv_key_dpad), KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT),
                TvKeycapsBar.Cap.of(getString(R.string.tv_key_ok), KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER),
                TvKeycapsBar.Cap.of(getString(R.string.tv_key_back), KeyEvent.KEYCODE_BACK),
        };
        String[] hints = {getString(R.string.tv_setting_hint_navigate), getString(R.string.tv_setting_hint_select), getString(R.string.tv_setting_hint_back)};
        mBinding.keycaps.setCaps(caps, hints);
    }

    @Override
    protected TvKeycapsBar keycaps() {
        return mBinding.keycaps;
    }

    @Override
    protected void initEvent() {
        mBinding.settingBack.setOnClickListener(view -> finish());
        bindVerticalNavigation(mBinding.settingBack, mBinding.settingBack, mBinding.navContent);
        bindVerticalNavigation(mBinding.navContent, mBinding.settingBack, mBinding.navAppearance);
        bindVerticalNavigation(mBinding.navAppearance, mBinding.navContent, mBinding.navPlayback);
        bindVerticalNavigation(mBinding.navPlayback, mBinding.navAppearance, mBinding.navData);
        bindVerticalNavigation(mBinding.navData, mBinding.navPlayback, mBinding.navAbout);
        bindVerticalNavigation(mBinding.navAbout, mBinding.navData, mBinding.navAbout);
        bindSectionNavigation(mBinding.navContent, mBinding.settingsScroll, mBinding.sectionContent, mBinding.vod);
        bindSectionNavigation(mBinding.navAppearance, mBinding.settingsScroll, mBinding.sectionAppearance, mBinding.skin);
        bindSectionNavigation(mBinding.navPlayback, mBinding.settingsScroll, mBinding.sectionPlayback, mBinding.player);
        bindSectionNavigation(mBinding.navData, mBinding.utilityScroll, mBinding.sectionData, mBinding.incognito);
        bindSectionNavigation(mBinding.navAbout, mBinding.utilityScroll, mBinding.sectionAbout, mBinding.version);
        bindSectionFocus(mBinding.navContent, mBinding.vod, mBinding.vodHome, mBinding.vodHistory, mBinding.live, mBinding.liveHome, mBinding.liveHistory, mBinding.doh);
        bindSectionFocus(mBinding.navAppearance, mBinding.skin, mBinding.atmosphere, mBinding.wall, mBinding.wallDefault, mBinding.wallRefresh, mBinding.size);
        bindSectionFocus(mBinding.navPlayback, mBinding.player, mBinding.danmaku);
        bindSectionFocus(mBinding.navData, mBinding.incognito, mBinding.vault, mBinding.backup, mBinding.restore, mBinding.cache);
        bindSectionFocus(mBinding.navAbout, mBinding.version);
        mBinding.skin.setOnClickListener(this::setSkin);
        mBinding.atmosphere.setOnClickListener(this::setAtmosphere);
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.live.setOnClickListener(this::onLive);
        mBinding.wall.setOnClickListener(this::onWall);
        mBinding.size.setOnClickListener(this::setSize);
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.vault.setOnClickListener(this::onVault);
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

    private void bindVerticalNavigation(View current, View up, View down) {
        current.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                up.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                down.requestFocus();
                return true;
            }
            return false;
        });
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
            PermissionUtil.requestFile(this, allGranted -> {
                if (allGranted) load(config);
            });
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
        requestVault(() -> BackupManager.backup(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.backup_success);
                setVaultText();
            }

            @Override
            public void error() {
                Notify.show(R.string.backup_fail);
                setVaultText();
            }
        }));
    }

    private void onRestore(View view) {
        requestVault(() -> RestoreDialog.create().callback(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.restore_success);
                setOtherText();
                setVaultText();
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
        setActiveSourceText();
    }

}
