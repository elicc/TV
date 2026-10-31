package com.fongmi.android.tv.diagnostic;

import com.fongmi.android.tv.utils.Task;
import com.orhanobut.logger.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Small periodic samples before a failure, not a large thread/heap dump during OOM. */
public final class PlaybackResourceSampler {

    private ScheduledFuture<?> future;

    public void start(boolean proxy) {
        stop();
        future = Task.scheduler().scheduleWithFixedDelay(() -> sample(proxy), 0, 60, TimeUnit.SECONDS);
    }

    public void stop() {
        if (future != null) future.cancel(false);
        future = null;
    }

    private static void sample(boolean proxy) {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
            StringBuilder values = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Threads:") || line.startsWith("VmSize:") || line.startsWith("VmRSS:")) {
                    values.append(' ').append(line.trim().replace('\t', ' '));
                }
            }
            String[] descriptors = new File("/proc/self/fd").list();
            Logger.t("PlaybackResources").i("proxy=%s fd=%d%s", proxy, descriptors == null ? -1 : descriptors.length, values);
        } catch (IOException | SecurityException e) {
            Logger.t("PlaybackResources").w("Resource sample unavailable (%s)", e.getClass().getSimpleName());
        }
    }
}
