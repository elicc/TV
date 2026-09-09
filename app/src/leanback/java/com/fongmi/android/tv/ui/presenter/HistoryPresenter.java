package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import java.util.Locale;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.AdapterHistoryBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

public class HistoryPresenter extends Presenter {

    private final OnClickListener listener;
    private int width, height;
    private boolean delete;

    public HistoryPresenter(OnClickListener listener) {
        this.listener = listener;
        setLayoutSize();
    }

    public interface OnClickListener {

        void onItemClick(History item);

        void onItemDelete(History item);

        boolean onLongClick();
    }

    private void setLayoutSize() {
        int columns = Math.max(3, Product.getColumn() - 1);
        int space = ResUtil.dp2px(48) + ResUtil.dp2px(16 * (columns - 1));
        int base = ResUtil.getScreenWidth() - space;
        width = base / columns;
        height = width * 9 / 16;
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    private void setClickListener(View root, History item) {
        root.setOnLongClickListener(view -> listener.onLongClick());
        root.setOnClickListener(view -> {
            if (isDelete()) listener.onItemDelete(item);
            else listener.onItemClick(item);
        });
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        ViewHolder holder = new ViewHolder(AdapterHistoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().getLayoutParams().width = width;
        holder.binding.image.getLayoutParams().height = height;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        History item = (History) object;
        ViewHolder holder = (ViewHolder) viewHolder;
        setClickListener(holder.view, item);
        holder.binding.name.setText(item.getVodName());
        holder.binding.site.setText(item.getSiteName());
        long seconds = Math.max(0, item.getPosition()) / 1000;
        String time = String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
        holder.binding.remark.setText(holder.view.getContext().getString(R.string.tv_continue, time) + (item.getVodRemarks().isEmpty() ? "" : " · " + item.getVodRemarks()));
        holder.binding.position.setVisibility(item.getDuration() > 0 ? View.VISIBLE : View.INVISIBLE);
        holder.binding.position.setProgress(item.getDuration() > 0 ? (int) Math.max(0, Math.min(1000, item.getPosition() * 1000.0 / item.getDuration())) : 0);
        holder.binding.site.setVisibility(item.getSiteVisible());
        holder.binding.delete.setVisibility(!delete ? View.GONE : View.VISIBLE);
        holder.binding.remark.setVisibility(delete ? View.INVISIBLE : View.VISIBLE);
        ImgUtil.load(item.getVodName(), item.getVodPic(), holder.binding.image);
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
        ViewHolder holder = (ViewHolder) viewHolder;
        Glide.with(holder.binding.image).clear(holder.binding.image);
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterHistoryBinding binding;

        public ViewHolder(@NonNull AdapterHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}