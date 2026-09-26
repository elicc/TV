package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterPartBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class PartAdapter extends RecyclerView.Adapter<PartAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<String> mItems;
    private final int maxWidth;
    private int nextFocusUp;

    public PartAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
        maxWidth = ResUtil.dp2px(220);
    }

    public void addAll(List<String> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void setNextFocusUp(int nextFocusUp) {
        this.nextFocusUp = nextFocusUp;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterPartBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String text = mItems.get(position);
        holder.binding.text.setText(text);
        holder.binding.text.setMaxWidth(maxWidth);
        holder.binding.text.setSingleLine(true);
        holder.binding.text.setHorizontallyScrolling(false);
        holder.binding.text.setEllipsize(TextUtils.TruncateAt.END);
        holder.binding.text.setMarqueeRepeatLimit(-1);
        holder.binding.text.setOnFocusChangeListener((view, focused) -> {
            if (focused) {
                holder.binding.text.setHorizontallyScrolling(true);
                holder.binding.text.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            } else {
                holder.binding.text.setHorizontallyScrolling(false);
                holder.binding.text.setEllipsize(TextUtils.TruncateAt.END);
            }
        });
        holder.binding.text.setNextFocusUpId(nextFocusUp);
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(text));
    }

    public interface OnClickListener {

        void onItemClick(String item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterPartBinding binding;

        ViewHolder(@NonNull AdapterPartBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
