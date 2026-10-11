package com.fongmi.android.tv.ui.dialog;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Explicit consent for uncertain cross-source identity; cancelling never starts playback. */
public final class VodResumeDialog {
    private VodResumeDialog() {}

    public static AlertDialog show(Context context, History source, boolean available, Runnable resume, Runnable startOver) {
        return show(context, source, available ? R.string.resume_confirm_message : R.string.resume_missing_message, available, resume, startOver);
    }

    public static AlertDialog show(Context context, History source, int message, boolean available, Runnable resume, Runnable startOver) {
        String checkpoint = source.getVodName() + " · " + source.getVodRemarks() + " · " + Util.timeMs(source.getPosition());
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.resume_confirm_title)
                .setMessage(context.getString(message, checkpoint))
                .setNegativeButton(R.string.resume_start_over, (dialog, which) -> startOver.run())
                .setNeutralButton(android.R.string.cancel, null);
        if (available) builder.setPositiveButton(R.string.resume_confirm_action, (dialog, which) -> resume.run());
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).requestFocus());
        dialog.show();
        return dialog;
    }
}
