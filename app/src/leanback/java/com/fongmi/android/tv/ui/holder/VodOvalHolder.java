package com.fongmi.android.tv.ui.holder;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterVodOvalBinding;
import com.fongmi.android.tv.ui.base.BaseVodHolder;
import com.fongmi.android.tv.ui.motion.TvMotion;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.TvTheme;

public class VodOvalHolder extends BaseVodHolder {

    private final VodPresenter.OnClickListener listener;
    private final AdapterVodOvalBinding binding;

    public VodOvalHolder(@NonNull AdapterVodOvalBinding binding, VodPresenter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
        setFocusListener();
    }

    public VodOvalHolder size(int[] size) {
        binding.image.getLayoutParams().width = size[0];
        binding.image.getLayoutParams().height = size[1];
        return this;
    }

    /** Focused cards lift the title 2dp and warm it to the skin focus color. */
    private void setFocusListener() {
        binding.getRoot().setOnFocusChangeListener((v, hasFocus) -> {
            binding.name.animate().cancel();
            binding.name.animate().translationY(hasFocus ? -ResUtil.dp2px(2) : 0f).setDuration(TvMotion.FOCUS_ON).start();
            binding.name.setTextColor(hasFocus ? TvTheme.color(v.getContext(), R.attr.tvColorFocus) : ContextCompat.getColor(v.getContext(), R.color.tv_text_primary));
            // Surface focus to the activity-level backdrop so the focused-poster
            // atmosphere follows D-pad navigation between cards in a row.
            if (hasFocus && current != null) listener.onItemFocus(current);
        });
    }

    /** Tracks the most recently bound Vod so the focus listener can publish it. */
    private Vod current;

    @Override
    public void initView(Vod item) {
        current = item;
        binding.name.setText(item.getName());
        binding.name.setVisibility(item.getNameVisible());
        binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        binding.getRoot().setOnLongClickListener(v -> listener.onLongClick(item));
        ImgUtil.load(item.getName(), item.getPic(), binding.image);
        if (binding.getRoot().hasFocus()) listener.onItemFocus(item);
    }

    @Override
    public void unbind() {
        Glide.with(binding.image).clear(binding.image);
    }
}
