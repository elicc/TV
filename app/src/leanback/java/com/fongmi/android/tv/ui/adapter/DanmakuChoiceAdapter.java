package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.databinding.AdapterDanmakuChoiceBinding;

import java.util.ArrayList;
import java.util.List;

/** Danmaku sources for the TV playback picker grid; selection uses the shared choice underline. */
public class DanmakuChoiceAdapter extends RecyclerView.Adapter<DanmakuChoiceAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Danmaku> mItems;
    private int mNextFocusUpId = View.NO_ID;

    public DanmakuChoiceAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(Danmaku item);
    }

    public void addAll(List<Danmaku> items) {
        mItems.clear();
        if (items != null) mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void setNextFocusUpId(int id) {
        mNextFocusUpId = id;
    }

    public int getPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    public Danmaku getSelected() {
        for (Danmaku item : mItems) if (item.isSelected()) return item;
        return null;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterDanmakuChoiceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Danmaku item = mItems.get(position);
        holder.binding.text.setText(item.getName());
        holder.binding.text.setSelected(item.isSelected());
        holder.binding.text.setNextFocusUpId(mNextFocusUpId);
        holder.binding.text.setOnFocusChangeListener((view, focused) -> {
            holder.binding.text.setHorizontallyScrolling(focused);
            holder.binding.text.setEllipsize(focused ? TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.END);
        });
        holder.binding.text.setOnClickListener(view -> mListener.onItemClick(item));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterDanmakuChoiceBinding binding;

        ViewHolder(@NonNull AdapterDanmakuChoiceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
