package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySettingDanmakuBinding;
import com.fongmi.android.tv.impl.DanmakuListener;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.DanmakuApiDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuSettingDialog;

import okhttp3.HttpUrl;

public class SettingDanmakuActivity extends BaseActivity implements DanmakuListener {

    private ActivitySettingDanmakuBinding mBinding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingDanmakuActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingDanmakuBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        refresh();
        mBinding.danmakuLoad.requestFocus();
        showHelp(R.string.danmaku_load, R.string.tv_dm_load_hint);
    }

    @Override
    protected void initEvent() {
        mBinding.back.setOnClickListener(v -> finish());
        bindHelp(mBinding.danmakuLoad, R.string.danmaku_load, R.string.tv_dm_load_hint);
        bindHelp(mBinding.danmakuApi, R.string.danmaku_api, R.string.tv_dm_api_hint);
        bindHelp(mBinding.danmakuAuto, R.string.danmaku_auto_load, R.string.tv_dm_auto_hint);
        bindHelp(mBinding.danmakuSpider, R.string.tv_dm_priority, R.string.tv_dm_priority_hint);
        bindHelp(mBinding.danmakuProbe, R.string.tv_dm_probe, R.string.tv_dm_probe_hint);
        bindHelp(mBinding.danmakuAppearance, R.string.tv_dm_appearance, R.string.tv_dm_appearance_hint);
        mBinding.danmakuLoad.setOnClickListener(v -> {
            DanmakuSetting.putLoad(!DanmakuSetting.isLoad());
            refresh();
        });
        mBinding.danmakuAuto.setOnClickListener(v -> {
            DanmakuSetting.putAuto(!DanmakuSetting.isAuto());
            refresh();
        });
        mBinding.danmakuSpider.setOnClickListener(v -> {
            DanmakuSetting.putSpiderFirst(!DanmakuSetting.isSpiderFirst());
            refresh();
        });
        mBinding.danmakuApi.setOnClickListener(v -> DanmakuApiDialog.show(this));
        mBinding.danmakuProbe.setOnClickListener(v -> DanmakuApiDialog.showProbe(this));
        mBinding.danmakuAppearance.setOnClickListener(v -> DanmakuSettingDialog.create().show(this));
    }

    private void bindHelp(View view, int title, int description) {
        view.setOnFocusChangeListener((v, focused) -> {
            if (focused) showHelp(title, description);
        });
    }

    private void showHelp(int title, int description) {
        mBinding.helpTitle.setText(title);
        mBinding.helpText.setText(description);
    }

    private String apiSummary() {
        String effective = DanmakuSetting.getEffectiveApiUrl();
        HttpUrl url = HttpUrl.parse(effective);
        if (url == null) return getString(R.string.tv_dm_unconfigured);
        // Never display a path, query or user-info: these may contain credentials.
        String host = url.host() + (url.port() == HttpUrl.defaultPort(url.scheme()) ? "" : ":" + url.port());
        return getString(TextUtils.isEmpty(DanmakuSetting.getApiUrl()) ? R.string.tv_dm_default : R.string.tv_dm_custom, host);
    }

    private void enabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setFocusable(enabled);
        view.setAlpha(enabled ? 1f : 0.5f);
    }

    private void refresh() {
        boolean load = DanmakuSetting.isLoad();
        boolean api = !TextUtils.isEmpty(DanmakuSetting.getEffectiveApiUrl());
        boolean auto = DanmakuSetting.isAuto();
        mBinding.danmakuLoadText.setText(Setting.getSwitch(load));
        mBinding.danmakuApiText.setText(apiSummary());
        mBinding.apiSummary.setText(apiSummary() + "\n\n" + getString(R.string.tv_dm_probe_hint));
        enabled(mBinding.danmakuAuto, load && api);
        enabled(mBinding.danmakuSpider, load && api && auto);
        mBinding.danmakuAutoText.setText(!load ? getString(R.string.tv_dm_requires_load) : !api ? getString(R.string.tv_dm_requires_api) : Setting.getSwitch(auto));
        mBinding.danmakuSpiderText.setText(!load ? getString(R.string.tv_dm_requires_load) : !api ? getString(R.string.tv_dm_requires_api) : !auto ? getString(R.string.tv_dm_requires_auto) : Setting.getSwitch(DanmakuSetting.isSpiderFirst()));
    }

    @Override
    public void setDanmakuApi(String url) {
        DanmakuSetting.putApiUrl(url);
        refresh();
    }
}
