package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.AdapterHistoryBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.Locale;

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

        /** Called when a history card becomes the D-pad focused item. */
        default void onItemFocus(History item) {
        }
    }

    private void setLayoutSize() {
        int columns = Math.max(3, Product.getColumn() - 1);
        int space = 2 * ResUtil.getDimensionPixelSize(R.dimen.tv_safe_horizontal) + ResUtil.dp2px(16 * (columns - 1));
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
        root.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus && !delete) listener.onItemFocus(item);
        });
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
        Config config = Config.find(item.getCid());
        String source = item.getCid() == VodConfig.getCid() || config == null ? "" : holder.view.getContext().getString(R.string.tv_history_source_label, config.getDesc());
        holder.binding.site.setText(source);
        long seconds = Math.max(0, item.getPosition()) / 1000;
        String time = String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
        holder.binding.remark.setText(holder.view.getContext().getString(R.string.tv_continue, time) + (item.getVodRemarks().isEmpty() ? "" : " · " + item.getVodRemarks()));
        holder.binding.position.setVisibility(item.getDuration() > 0 ? View.VISIBLE : View.INVISIBLE);
        holder.binding.position.setProgress(item.getDuration() > 0 ? (int) Math.max(0, Math.min(1000, item.getPosition() * 1000.0 / item.getDuration())) : 0);
        holder.binding.site.setVisibility(source.isEmpty() ? View.GONE : View.VISIBLE);
        holder.binding.delete.setVisibility(!delete ? View.GONE : View.VISIBLE);
        holder.binding.remark.setVisibility(delete ? View.INVISIBLE : View.VISIBLE);
        ImgUtil.load(item.getVodName(), item.getVodPic(), holder.binding.image);
        // Binding can happen after the grid has already restored focus; in
        // that case Android will not emit a second focus-change callback.
        if (holder.view.hasFocus() && !delete) listener.onItemFocus(item);
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
