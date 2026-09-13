package com.fongmi.android.tv.ui.holder;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterVodRectBinding;
import com.fongmi.android.tv.ui.base.BaseVodHolder;
import com.fongmi.android.tv.ui.motion.TvMotion;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.TvTheme;

public class VodRectHolder extends BaseVodHolder {

    private final VodPresenter.OnClickListener listener;
    private final AdapterVodRectBinding binding;

    public VodRectHolder(@NonNull AdapterVodRectBinding binding, VodPresenter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
        setFocusListener();
    }

    public VodRectHolder size(int[] size) {
        binding.image.getLayoutParams().height = size[1];
        binding.getRoot().getLayoutParams().width = size[0];
        return this;
    }

    /** Focused cards lift the title 2dp and warm it to the skin focus color. */
    private void setFocusListener() {
        binding.getRoot().setOnFocusChangeListener((v, hasFocus) -> {
            binding.name.animate().cancel();
            binding.name.animate().translationY(hasFocus ? -ResUtil.dp2px(2) : 0f).setDuration(TvMotion.FOCUS_ON).start();
            binding.name.setTextColor(hasFocus ? TvTheme.color(v.getContext(), R.attr.tvColorFocus) : ContextCompat.getColor(v.getContext(), R.color.tv_text_primary));
        });
    }

    @Override
    public void initView(Vod item) {
        binding.name.setText(item.getName());
        binding.year.setText(item.getYear());
        binding.site.setText(item.getSiteName());
        binding.remark.setText(item.getRemarks());
        binding.site.setVisibility(item.getSiteVisible());
        binding.year.setVisibility(item.getYearVisible());
        binding.name.setVisibility(item.getNameVisible());
        binding.remark.setVisibility(item.getRemarkVisible());
        binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        binding.getRoot().setOnLongClickListener(v -> listener.onLongClick(item));
        ImgUtil.load(item.getName(), item.getPic(), binding.image);
    }

    @Override
    public void unbind() {
        Glide.with(binding.image).clear(binding.image);
    }
}
