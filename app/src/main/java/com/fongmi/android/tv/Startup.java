package com.fongmi.android.tv;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;

import com.fongmi.android.tv.event.EventIndex;
import com.fongmi.android.tv.diagnostic.CrashRecorder;
import com.fongmi.android.tv.diagnostic.DiagnosticStore;
import com.fongmi.android.tv.diagnostic.ExitInfoCollector;
import com.fongmi.android.tv.diagnostic.PersistentLogAdapter;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.CrashActivity;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;

import org.greenrobot.eventbus.EventBus;

import java.util.Collections;
import java.util.List;

import cat.ereza.customactivityoncrash.config.CaocConfig;

public class Startup implements Initializer<Void> {

    @NonNull
    @Override
    public Void create(@NonNull Context context) {
        DiagnosticStore.init(context);
        CaocConfig.Builder.create().trackActivities(true).backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT).errorActivity(CrashActivity.class).apply();
        CrashRecorder.install();
        Logger.addLogAdapter(new AndroidLogAdapter(PrettyFormatStrategy.newBuilder().methodCount(0).showThreadInfo(false).tag("TV").build()));
        Logger.addLogAdapter(new PersistentLogAdapter());
        Task.execute(() -> ExitInfoCollector.collect(context));
        EventBus.builder().addIndex(new EventIndex()).installDefaultEventBus();
        OkHttp.dns().setDoh(() -> Doh.objectFrom(Setting.getDoh()));
        return null;
    }

    @NonNull
    @Override
    public List<Class<? extends Initializer<?>>> dependencies() {
        return Collections.emptyList();
    }
}
