package com.fongmi.android.tv.ui.holder;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterVodListBinding;
import com.fongmi.android.tv.ui.base.BaseVodHolder;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.ImgUtil;

public class VodListHolder extends BaseVodHolder {

    private final VodPresenter.OnClickListener listener;
    private final AdapterVodListBinding binding;
    /** Tracks the most recently bound Vod so the focus listener can publish it. */
    private Vod current;

    public VodListHolder(@NonNull AdapterVodListBinding binding, VodPresenter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
        // The list variant has no per-card focus animation but the activity
        // still needs focus events to drive the focused-poster backdrop.
        binding.getRoot().setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && current != null) listener.onItemFocus(current);
        });
    }

    @Override
    public void initView(Vod item) {
        current = item;
        binding.name.setText(item.getName());
        binding.remark.setText(item.getRemarks());
        binding.name.setVisibility(item.getNameVisible());
        binding.remark.setVisibility(item.getRemarkVisible());
        binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        binding.getRoot().setOnLongClickListener(v -> listener.onLongClick(item));
        ImgUtil.load(item.getName(), item.getPic(), binding.image, true);
        if (binding.getRoot().hasFocus()) listener.onItemFocus(item);
    }

    @Override
    public void unbind() {
        Glide.with(binding.image).clear(binding.image);
    }
}
