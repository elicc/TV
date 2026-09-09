package com.fongmi.android.tv.ui.adapter;

import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.AdapterVodBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

public class KeepAdapter extends BaseDiffAdapter<Keep, KeepAdapter.ViewHolder> {

    private final OnClickListener listener;
    private int width, height;
    private boolean delete;

    public KeepAdapter(OnClickListener listener) {
        this.listener = listener;
        setLayoutSize();
    }

    public interface OnClickListener {

        void onItemClick(Keep item);

        void onItemDelete(Keep item);

        boolean onLongClick();
    }

    private void setLayoutSize() {
        int space = ResUtil.dp2px(48) + ResUtil.dp2px(16 * (Product.getColumn() - 1));
        int base = ResUtil.getScreenWidth() - space;
        width = base / Product.getColumn();
        height = (int) (width / 0.75f);
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        if (this.delete == delete) return;
        this.delete = delete;
        notifyItemRangeChanged(0, getItemCount());
    }

    private void setClickListener(View root, Keep item) {
        root.setOnLongClickListener(view -> listener.onLongClick());
        root.setOnClickListener(view -> {
            if (isDelete()) listener.onItemDelete(item);
            else listener.onItemClick(item);
        });
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(AdapterVodBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().getLayoutParams().width = width;
        holder.binding.image.getLayoutParams().height = height;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Keep item = getItem(position);
        setClickListener(holder.itemView, item);
        holder.binding.name.setText(item.getVodName());
        holder.binding.remark.setVisibility(View.GONE);
        holder.binding.site.setVisibility(View.VISIBLE);
        holder.binding.site.setText(item.getSiteName());
        holder.binding.delete.setVisibility(!delete ? View.GONE : View.VISIBLE);
        ImgUtil.load(item.getVodName(), item.getVodPic(), holder.binding.image);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        holder.itemView.animate().cancel();
        holder.itemView.setScaleX(1f);
        holder.itemView.setScaleY(1f);
        holder.itemView.setTranslationZ(0f);
        super.onViewRecycled(holder);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterVodBinding binding;

        public ViewHolder(@NonNull AdapterVodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            setFocusListener();
        }

        private void setFocusListener() {
            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                float scale = hasFocus ? 1.04f : 1f;
                v.animate().cancel();
                if (Settings.Global.getFloat(v.getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) {
                    v.setScaleX(scale);
                    v.setScaleY(scale);
                } else {
                    // ViewPropertyAnimator already applies the system duration scale.
                    v.animate().scaleX(scale).scaleY(scale).setDuration(150).start();
                }
                v.setTranslationZ(hasFocus ? ResUtil.dp2px(4) : 0f);
            });
        }
    }
}
