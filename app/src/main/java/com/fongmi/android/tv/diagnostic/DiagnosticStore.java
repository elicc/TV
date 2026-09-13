package com.fongmi.android.tv.diagnostic;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.utils.FileUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class DiagnosticStore {

    private static final String TAG = DiagnosticStore.class.getSimpleName();
    private static final long MAX_LOG_BYTES = 2L * 1024 * 1024;
    private static final int MAX_LOG_FILES = 8;
    private static final int MAX_REPORT_FILES = 20;
    private static final int MAX_MESSAGE_CHARS = 16 * 1024;
    private static final int MAX_REPORT_CHARS = 512 * 1024;
    private static final int MAX_RECENT_LINES = 120;
    private static final Object FILE_LOCK = new Object();
    private static volatile DiagnosticStore instance;

    private final ArrayDeque<String> recent;
    private final ExecutorService writer;
    private final File fallbackDirectory;
    private final File directory;

    private DiagnosticStore(Context context) {
        recent = new ArrayDeque<>();
        writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "diagnostic-writer");
            thread.setDaemon(true);
            return thread;
        });
        fallbackDirectory = new File(context.getNoBackupFilesDir(), "diagnostics");
        File external = context.getExternalFilesDir("diagnostics");
        directory = prepare(external) ? external : fallbackDirectory;
        prepare(directory);
    }

    public static synchronized void init(Context context) {
        if (instance != null) return;
        instance = new DiagnosticStore(context.getApplicationContext());
        instance.installStderrMirror();
        instance.log(Log.INFO, TAG, "session_start version=" + BuildConfig.VERSION_NAME + " sdk=" + Build.VERSION.SDK_INT + " model=" + Build.MODEL + " path=" + instance.directory.getAbsolutePath());
    }

    public static DiagnosticStore get() {
        DiagnosticStore store = instance;
        if (store == null) throw new IllegalStateException("DiagnosticStore is not initialized");
        return store;
    }

    public File getDirectory() {
        return directory;
    }

    public void log(int priority, String tag, String message) {
        String record = format(priority, tag, message);
        remember(record);
        try {
            writer.execute(() -> append(record));
        } catch (RejectedExecutionException ignored) {
        }
    }

    public void recordCrash(Thread thread, Throwable error) {
        StringWriter stack = new StringWriter();
        error.printStackTrace(new PrintWriter(stack));
        StringBuilder report = reportHeader("java_crash");
        report.append("thread=").append(thread.getName()).append(" id=").append(thread.getId()).append('\n');
        report.append("exception=").append(error.getClass().getName()).append('\n');
        report.append("message=").append(LogRedactor.redact(String.valueOf(error.getMessage()))).append("\n\n");
        report.append("stack_trace:\n").append(LogRedactor.redact(stack.toString()));
        appendRecent(report);
        writeReport("crash", report.toString());
    }

    public boolean recordExit(String name, String report, byte[] trace) {
        boolean saved = writeReport(name, report);
        if (saved && trace != null && trace.length > 0) writeBytes(name + "-trace", ".bin", trace);
        return saved;
    }

    private StringBuilder reportHeader(String kind) {
        return new StringBuilder()
                .append("kind=").append(kind).append('\n')
                .append("time=").append(now()).append('\n')
                .append("version=").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
                .append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                .append("android=").append(Build.VERSION.RELEASE).append(" sdk=").append(Build.VERSION.SDK_INT).append('\n')
                .append("process=").append(currentProcessName()).append(" pid=").append(Process.myPid()).append("\n\n");
    }

    private void appendRecent(StringBuilder report) {
        String[] snapshot;
        synchronized (recent) {
            snapshot = recent.toArray(new String[0]);
        }
        report.append("\nrecent_app_log:\n");
        for (String line : snapshot) report.append(line);
    }

    private void remember(String record) {
        synchronized (recent) {
            recent.addLast(record);
            while (recent.size() > MAX_RECENT_LINES) recent.removeFirst();
        }
    }

    private void append(String record) {
        byte[] data = record.getBytes(StandardCharsets.UTF_8);
        synchronized (FILE_LOCK) {
            if (append(directory, data)) return;
            if (!directory.equals(fallbackDirectory)) append(fallbackDirectory, data);
        }
    }

    private boolean append(File parent, byte[] data) {
        try {
            if (!prepare(parent)) return false;
            File target = new File(parent, "app.log");
            if (target.length() + data.length > MAX_LOG_BYTES) rotateLogs(parent);
            try (FileOutputStream output = new FileOutputStream(target, true)) {
                output.write(data);
                output.flush();
            }
            return true;
        } catch (IOException | SecurityException e) {
            Log.w(TAG, "Unable to persist diagnostic log", e);
            return false;
        }
    }

    private void rotateLogs(File parent) {
        File oldest = new File(parent, "app." + MAX_LOG_FILES + ".log");
        if (oldest.exists() && !oldest.delete()) Log.w(TAG, "Unable to delete old diagnostic log");
        for (int index = MAX_LOG_FILES - 1; index >= 1; index--) {
            File source = new File(parent, "app." + index + ".log");
            if (!source.exists()) continue;
            File target = new File(parent, "app." + (index + 1) + ".log");
            if (!source.renameTo(target)) Log.w(TAG, "Unable to rotate " + source.getName());
        }
        File current = new File(parent, "app.log");
        if (current.exists() && !current.renameTo(new File(parent, "app.1.log"))) Log.w(TAG, "Unable to rotate current diagnostic log");
    }

    private boolean writeReport(String prefix, String report) {
        String safe = LogRedactor.redact(limit(report, MAX_REPORT_CHARS));
        return writeBytes(prefix, ".txt", safe.getBytes(StandardCharsets.UTF_8));
    }

    private boolean writeBytes(String prefix, String extension, byte[] data) {
        String fileName = prefix + '-' + fileTimestamp() + '-' + Process.myPid() + extension;
        synchronized (FILE_LOCK) {
            if (writeBytes(directory, fileName, data)) {
                trimReports(directory);
                return true;
            }
            if (!directory.equals(fallbackDirectory) && writeBytes(fallbackDirectory, fileName, data)) {
                trimReports(fallbackDirectory);
                return true;
            }
            return false;
        }
    }

    private boolean writeBytes(File parent, String fileName, byte[] data) {
        try {
            if (!prepare(parent)) return false;
            FileUtil.writeAtomically(data, new File(parent, fileName));
            return true;
        } catch (IOException | SecurityException e) {
            Log.w(TAG, "Unable to persist diagnostic report", e);
            return false;
        }
    }

    private void trimReports(File parent) {
        File[] reports = parent.listFiles(file -> file.isFile() && !file.getName().startsWith("app"));
        if (reports == null || reports.length <= MAX_REPORT_FILES) return;
        Arrays.sort(reports, Comparator.comparingLong(File::lastModified).reversed());
        for (int index = MAX_REPORT_FILES; index < reports.length; index++) {
            if (!reports[index].delete()) Log.w(TAG, "Unable to delete old diagnostic report");
        }
    }

    private String format(int priority, String tag, String message) {
        String safe = LogRedactor.redact(limit(String.valueOf(message), MAX_MESSAGE_CHARS)).replace("\r", "").replace("\n", "\n    ");
        return now() + ' ' + level(priority) + " pid=" + Process.myPid() + " tid=" + Thread.currentThread().getId() + " [" + String.valueOf(tag) + "] " + safe + '\n';
    }

    private void installStderrMirror() {
        PrintStream original = System.err;
        if (original == null) return;
        try {
            System.setErr(new PrintStream(new StderrOutputStream(original, this), true, StandardCharsets.UTF_8.name()));
        } catch (Exception e) {
            Log.w(TAG, "Unable to mirror stderr", e);
        }
    }

    private static boolean prepare(File directory) {
        return directory != null && (directory.isDirectory() || directory.mkdirs() || directory.isDirectory());
    }

    private static String limit(String value, int maxChars) {
        if (value.length() <= maxChars) return value;
        return value.substring(0, maxChars) + "\n[diagnostic content truncated]";
    }

    private static String level(int priority) {
        return switch (priority) {
            case Log.VERBOSE -> "V";
            case Log.DEBUG -> "D";
            case Log.INFO -> "I";
            case Log.WARN -> "W";
            case Log.ERROR -> "E";
            case Log.ASSERT -> "A";
            default -> "?";
        };
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date());
    }

    private static String fileTimestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
    }

    private static String currentProcessName() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return android.app.Application.getProcessName();
        return "unknown";
    }

    private static final class StderrOutputStream extends OutputStream {

        private final ByteArrayOutputStream buffer;
        private final PrintStream original;
        private final DiagnosticStore store;

        private StderrOutputStream(PrintStream original, DiagnosticStore store) {
            this.buffer = new ByteArrayOutputStream();
            this.original = original;
            this.store = store;
        }

        @Override
        public synchronized void write(int value) {
            original.write(value);
            if (value == '\n') commit();
            else if (value != '\r') buffer.write(value);
        }

        @Override
        public synchronized void write(byte[] data, int offset, int length) {
            original.write(data, offset, length);
            int end = offset + length;
            for (int index = offset; index < end; index++) {
                int value = data[index] & 0xff;
                if (value == '\n') commit();
                else if (value != '\r') buffer.write(value);
            }
        }

        @Override
        public synchronized void flush() {
            original.flush();
            commit();
        }

        private void commit() {
            if (buffer.size() == 0) return;
            String line = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            buffer.reset();
            store.log(Log.ERROR, "stderr", line);
        }
    }
}
