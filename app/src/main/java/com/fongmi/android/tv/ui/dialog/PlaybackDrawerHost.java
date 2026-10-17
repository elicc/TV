package com.fongmi.android.tv.ui.dialog;

import android.widget.FrameLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;

/** A fullscreen player that docks bottom sheets above its console as full-width drawers. */
public interface PlaybackDrawerHost {

    /** Whether sheets opened now should dock as drawers instead of floating cards. */
    boolean usePlaybackDrawer();

    /** Restyles the sheet window and surface; called on every start of a docked sheet. */
    void onPlaybackDrawerStart(BottomSheetDialog dialog, FrameLayout sheet);

    void onPlaybackDrawerStop();
}
