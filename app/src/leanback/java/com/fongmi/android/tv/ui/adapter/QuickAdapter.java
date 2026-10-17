package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;

import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterQuickBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class QuickAdapter extends RecyclerView.Adapter<QuickAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Vod> mItems;
    private final int width;

    public QuickAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
        int space = ResUtil.dp2px(24) + ResUtil.dp2px(32);
        // Keep source cards compact enough for the five-card rhythm in the
        // detail design while retaining horizontal scrolling for long lists.
        width = (ResUtil.getScreenWidth() - space) / 5;
    }

    public void setItems(List<Vod> items) {
        List<Vod> next = new ArrayList<>(items);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return mItems.size(); }

            @Override
            public int getNewListSize() { return next.size(); }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                Vod old = mItems.get(oldPosition), item = next.get(newPosition);
                return old.getSiteKey().equals(item.getSiteKey()) && old.getId().equals(item.getId());
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return mItems.get(oldPosition).isSameContent(next.get(newPosition));
            }
        });
        mItems.clear();
        mItems.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    public void remove(int position) {
        mItems.remove(position);
        notifyItemRemoved(position);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public Vod get(int position) {
        return mItems.get(position);
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(AdapterQuickBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().getLayoutParams().width = width;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Vod item = mItems.get(position);
        holder.binding.name.setText(item.getName());
        holder.binding.site.setText(item.getSiteName());
        // Some sites send "0" as an empty remark. Keep the line's height so
        // every card in the rail stays the same size.
        String remark = item.getRemarks();
        boolean hasRemark = !TextUtils.isEmpty(remark) && !"0".equals(remark.trim());
        holder.binding.remark.setText(hasRemark ? remark : "");
        holder.binding.remark.setVisibility(hasRemark ? View.VISIBLE : View.INVISIBLE);
        holder.setMarquee(holder.itemView.hasFocus());
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
    }

    @Override
    public void onViewAttachedToWindow(@NonNull ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        holder.setMarquee(holder.itemView.hasFocus());
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ViewHolder holder) {
        holder.setMarquee(false);
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        holder.setMarquee(false);
        super.onViewRecycled(holder);
    }

    public interface OnClickListener {

        void onItemClick(Vod item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterQuickBinding binding;

        ViewHolder(@NonNull AdapterQuickBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnFocusChangeListener((view, focused) -> setMarquee(focused));
        }

        private void setMarquee(boolean focused) {
            for (TextView text : new TextView[]{binding.name, binding.site, binding.remark}) {
                text.setSelected(false);
                text.setSingleLine(true);
                text.setHorizontallyScrolling(focused);
                text.setEllipsize(focused ? TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.END);
                text.setMarqueeRepeatLimit(-1);
                text.setSelected(focused);
            }
        }
    }
}
