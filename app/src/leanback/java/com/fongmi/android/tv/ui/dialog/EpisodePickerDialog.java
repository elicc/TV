package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.DialogEpisodePickerBinding;
import com.fongmi.android.tv.databinding.ViewEpisodePickerTabBinding;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.motion.TvMotion;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.util.List;

/** TV-only modal: display ordinals, but pass the original episode to the playback owner. */
public class EpisodePickerDialog extends BaseAlertDialog {
    public static final String TAG = "tv_episode_picker";
    private static final int PAGE_SIZE = 16;
    private DialogEpisodePickerBinding binding;
    private EpisodeAdapter adapter;
    private List<Episode> episodes = List.of();
    private boolean picked;

    public interface Host {
        int episodePickerBottomOffset();
        List<Episode> episodePickerItems(int generation);
        void pickEpisode(int generation, Episode episode);
        void closeEpisodePicker(int generation, boolean picked);
    }

    public static EpisodePickerDialog create(int generation) {
        EpisodePickerDialog dialog = new EpisodePickerDialog();
        Bundle args = new Bundle();
        args.putInt("generation", generation);
        dialog.setArguments(args);
        return dialog;
    }

    private int generation() { return requireArguments().getInt("generation"); }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogEpisodePickerBinding.inflate(getLayoutInflater());
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
        if (!(getActivity() instanceof Host host)) return;
        episodes = host.episodePickerItems(generation());
        adapter = new EpisodeAdapter(episode -> {
            if (!host.episodePickerItems(generation()).contains(episode)) {
                dismissAllowingStateLoss();
                return;
            }
            picked = true;
            host.pickEpisode(generation(), episode);
            dismiss();
        });
        binding.grid.setNumRows(1);
        binding.grid.setHorizontalSpacing(ResUtil.dp2px(8));
        binding.grid.setItemAnimator(null);
        binding.grid.setAdapter(adapter);
        binding.title.setText(getString(R.string.tv_episode_picker_title, episodes.size()));
        binding.tabs.setVisibility(episodes.size() > PAGE_SIZE ? View.VISIBLE : View.GONE);
        for (int start = 0; start < episodes.size(); start += PAGE_SIZE) {
            var label = ViewEpisodePickerTabBinding.inflate(getLayoutInflater(), binding.tabs, false).getRoot();
            label.setText(getString(R.string.tv_episode_picker_range, number(start), number(Math.min(start + PAGE_SIZE, episodes.size()) - 1)));
            TabLayout.Tab tab = binding.tabs.newTab().setCustomView(label);
            binding.tabs.addTab(tab, false);
            View tabView = (View) label.getParent();
            tabView.setId(View.generateViewId());
            tabView.setFocusable(true);
            tabView.setNextFocusDownId(R.id.grid);
            tabView.setOnFocusChangeListener((view, focused) -> { if (focused && !tab.isSelected()) tab.select(); });
        }
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { showPage(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) { focusEpisode(); }
        });
        if (!episodes.isEmpty()) binding.tabs.selectTab(binding.tabs.getTabAt(selectedPosition() / PAGE_SIZE));
        refreshSelection();
    }

    private int number(int position) {
        Episode episode = episodes.get(position);
        return (episode.getIndex() >= 0 ? episode.getIndex() : position) + 1;
    }

    private int selectedPosition() {
        for (int i = 0; i < episodes.size(); i++) if (episodes.get(i).isSelected()) return i;
        return 0;
    }

    private void showPage(int page) {
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, episodes.size());
        adapter.numbersOnly(start).addAll(episodes.subList(start, end));
        TabLayout.Tab tab = binding.tabs.getTabAt(page);
        View target = tab == null ? binding.grid : (View) tab.getCustomView().getParent();
        adapter.setNextFocusUp(episodes.size() > PAGE_SIZE ? target.getId() : R.id.grid);
        binding.grid.setSelectedPosition(adapter.getPosition());
    }

    private void focusEpisode() {
        // A dialog opened after pointer input may still be in touch mode; wait for
        // its child to attach instead of asking an empty grid to take focus.
        binding.grid.post(() -> {
            if (!isAdded() || getDialog() == null || !getDialog().isShowing()) return;
            binding.grid.setSelectedPosition(adapter.getPosition(), holder -> holder.itemView.requestFocus());
        });
    }

    public void refreshSelection() {
        if (binding == null || adapter == null || episodes.isEmpty()) return;
        binding.current.setText(getString(R.string.tv_episode_picker_current, number(selectedPosition())));
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (episodes.size() <= 1) {
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
            if (getActivity() instanceof Host host) params.y = host.episodePickerBottomOffset();
            window.setAttributes(params);
        }
        animateEntrance();
        focusEpisode();
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
            // The fixed wrapper clips the panel below the toolbar's top edge.
            // Slide, rather than scale, so text and focus rings keep their shape.
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
        if (getActivity() instanceof Host host) host.closeEpisodePicker(generation(), picked);
    }
}
