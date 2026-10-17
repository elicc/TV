package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AnimationUtils;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.databinding.DialogDanmakuPickerBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.ui.adapter.DanmakuChoiceAdapter;
import com.fongmi.android.tv.ui.motion.TvMotion;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** TV-only playback drawer for danmaku sources; mirrors the episode picker above the console. */
public class DanmakuPickerDialog extends BaseAlertDialog {

    public static final String TAG = "tv_danmaku_picker";
    private static final int MAX_ROWS = 3;
    private DialogDanmakuPickerBinding binding;
    private DanmakuChoiceAdapter adapter;
    private PlayerManager player;
    private boolean handled;
    private boolean choosing;
    private boolean resume;

    public interface Host {
        int danmakuPickerBottomOffset();
        void closeDanmakuPicker(boolean handled);
    }

    public static DanmakuPickerDialog create() {
        return new DanmakuPickerDialog();
    }

    public DanmakuPickerDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogDanmakuPickerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setBackground(new ColorDrawable(Color.TRANSPARENT))
                .setBackgroundInsetStart(0).setBackgroundInsetEnd(0)
                .setBackgroundInsetTop(0).setBackgroundInsetBottom(0)
                .setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        if (player == null) return;
        adapter = new DanmakuChoiceAdapter(this::onItemClick);
        adapter.addAll(player.getDanmakus());
        int count = adapter.getItemCount();
        int columns = count > 1 ? 2 : 1;
        int rows = Math.min(MAX_ROWS, Math.max(1, (count + columns - 1) / columns));
        int spacing = ResUtil.dp2px(8);
        binding.grid.setNumColumns(columns);
        binding.grid.setHorizontalSpacing(spacing);
        binding.grid.setVerticalSpacing(spacing);
        binding.grid.getLayoutParams().height = rows * getResources().getDimensionPixelSize(R.dimen.tv_danmaku_choice_height) + (rows - 1) * spacing + binding.grid.getPaddingTop() + binding.grid.getPaddingBottom();
        binding.grid.setItemAnimator(null);
        binding.grid.setAdapter(adapter);
        boolean empty = count == 0;
        binding.grid.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        // Search stays available without an interface; the drawer explains how to set one up.
        boolean canSearch = player.getMetadata() != null;
        binding.search.setVisibility(canSearch ? View.VISIBLE : View.GONE);
        // The grid lets UP leave its first row (focusOutFront); land on the leading action.
        adapter.setNextFocusUpId((canSearch ? binding.search : binding.choose).getId());
        Danmaku selected = adapter.getSelected();
        binding.current.setText(selected == null ? getString(R.string.tv_danmaku_off) : getString(R.string.tv_danmaku_current, selected.getName()));
    }

    @Override
    protected void initEvent() {
        binding.search.setOnClickListener(this::onSearch);
        binding.choose.setOnClickListener(this::onChoose);
        binding.setting.setOnClickListener(this::onSetting);
    }

    private void onItemClick(Danmaku item) {
        player.toggleDanmaku(item);
        close();
    }

    private void onSearch(View view) {
        DanmakuFinderDialog.create().player(player).show(requireActivity());
        // Not handled: the console stays up so the search drawer docks on it.
        dismiss();
    }

    private void onChoose(View view) {
        // The file list stops this Activity, so park playback and restore the
        // play intent on return; the picker must never outlive the round trip.
        resume = player.getPlayer().getPlayWhenReady();
        choosing = true;
        FileChooser.from(launcher).show(new String[]{"text/*"});
        player.pause();
    }

    private void onSetting(View view) {
        DanmakuSettingDialog.create().player(player).show(requireActivity());
        close();
    }

    private void close() {
        handled = true;
        dismiss();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onChosen);

    private void onChosen(ActivityResult result) {
        // Close for both pick and BACK: a lingering picker keeps the remote focus,
        // so OK would reach it instead of the paused player. The file resolves
        // asynchronously, after this dialog is gone.
        PlayerManager player = this.player;
        FileChooser.getUri(result, uri -> setDanmaku(player, uri));
        choosing = false;
        if (resume) player.play();
        close();
    }

    private static void setDanmaku(PlayerManager player, Uri uri) {
        player.setDanmaku(Danmaku.from(FileUtil.getDisplayName(uri), uri.toString()));
    }

    private void focusDefault() {
        // Land on the playing source first; otherwise the first available action.
        binding.grid.post(() -> {
            if (!isAdded() || getDialog() == null || !getDialog().isShowing()) return;
            if (adapter.getItemCount() > 0) binding.grid.setSelectedPosition(adapter.getPosition(), holder -> holder.itemView.requestFocus());
            else (binding.search.getVisibility() == View.VISIBLE ? binding.search : binding.choose).requestFocus();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (player == null) {
            dismissAllowingStateLoss();
            return;
        }
        setWidth(1f);
        Window window = requireDialog().getWindow();
        if (window != null) {
            window.setWindowAnimations(0);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            var params = window.getAttributes();
            if (getActivity() instanceof Host host) params.y = host.danmakuPickerBottomOffset();
            window.setAttributes(params);
        }
        if (choosing) {
            // Returning from the file list: stay hidden until the result closes us.
            binding.panel.setAlpha(0f);
            return;
        }
        animateEntrance();
        focusDefault();
    }

    private void animateEntrance() {
        View panel = binding.panel;
        panel.animate().cancel();
        panel.setTranslationY(0f);
        if (!TvMotion.motionEnabled(panel)) {
            panel.setAlpha(1f);
            return;
        }
        panel.setAlpha(0f);
        panel.post(() -> {
            if (!isAdded() || getDialog() == null || !getDialog().isShowing()) return;
            panel.setTranslationY(panel.getHeight());
            panel.animate().translationY(0f).alpha(1f).setDuration(TvMotion.DRAWER)
                    .setInterpolator(AnimationUtils.loadInterpolator(panel.getContext(), R.interpolator.tv_interp_decelerate))
                    .start();
        });
    }

    @Override
    public void onStop() {
        binding.panel.animate().cancel();
        binding.panel.setTranslationY(0f);
        binding.panel.setAlpha(1f);
        super.onStop();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (getActivity() instanceof Host host) host.closeDanmakuPicker(handled);
    }
}
