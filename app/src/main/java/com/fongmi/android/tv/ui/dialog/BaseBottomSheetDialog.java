package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.activity.ComponentDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.utils.Util;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public abstract class BaseBottomSheetDialog extends BottomSheetDialogFragment {

    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            onBackInvoked();
        }
    };

    private PlaybackDrawerHost drawerHost;

    protected abstract ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container);

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        // Decided once: a sheet opened from the fullscreen console stays docked to it.
        drawerHost = getActivity() instanceof PlaybackDrawerHost host && host.usePlaybackDrawer() ? host : null;
        dialog.setOnShowListener(d -> setSheet(dialog));
        Window window = dialog.getWindow();
        if (window == null) return dialog;
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Util.isFullscreen(getActivity())) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return getBinding(inflater, container).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupBackDispatcher();
        initView();
        initEvent();
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    protected void onBackInvoked() {
    }

    protected final void setBackCallbackEnabled(boolean enabled) {
        backCallback.setEnabled(enabled);
    }

    protected int getMaxHeight() {
        return 0;
    }

    protected final boolean isPlaybackDrawer() {
        return drawerHost != null;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (drawerHost == null || !(getDialog() instanceof BottomSheetDialog dialog)) return;
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) drawerHost.onPlaybackDrawerStart(dialog, sheet);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (drawerHost != null) drawerHost.onPlaybackDrawerStop();
    }

    private void setSheet(BottomSheetDialog dialog) {
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        if (!isPlaybackDrawer()) setHeight(sheet, getMaxHeight());
        else sheet.getViewTreeObserver().addOnGlobalLayoutListener(() -> onDrawerLayout(sheet));
        setBehavior(sheet);
    }

    // The behavior keeps an expanded sheet's top when its content later grows or
    // shrinks; a docked drawer must instead keep its bottom on the console. Runs
    // after the whole pass, since the behavior restores the top after child layout.
    private void onDrawerLayout(FrameLayout sheet) {
        if (!(sheet.getParent() instanceof View parent)) return;
        int offset = parent.getHeight() - sheet.getBottom();
        if (offset != 0 && BottomSheetBehavior.from(sheet).getState() == BottomSheetBehavior.STATE_EXPANDED) sheet.offsetTopAndBottom(offset);
    }

    private void setHeight(FrameLayout sheet, int maxHeight) {
        if (maxHeight <= 0) return;
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        params.height = maxHeight;
        sheet.setLayoutParams(params);
    }

    private void setBehavior(FrameLayout sheet) {
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
    }

    private void setupBackDispatcher() {
        if (requireDialog() instanceof ComponentDialog dialog) dialog.getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);
    }

    @Override
    public void onDestroyView() {
        backCallback.setEnabled(false);
        super.onDestroyView();
    }
}
