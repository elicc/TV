package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.databinding.AdapterEpgDataBinding;

import java.util.ArrayList;
import java.util.List;

public class EpgDataAdapter extends RecyclerView.Adapter<EpgDataAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<EpgData> mItems;

    public EpgDataAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
    }

    public void addAll(List<EpgData> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public EpgData get(int position) {
        return mItems.get(position);
    }

    public void setSelected(EpgData selected) {
        for (EpgData item : mItems) item.setSelected(selected);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterEpgDataBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EpgData item = mItems.get(position);
        holder.binding.time.setText(item.getTime());
        String title = item.getTitle();
        if (item.isInRange()) title = holder.itemView.getContext().getString(R.string.tv_playback_epg_current, title);
        else if (item.isSelected()) title = holder.itemView.getContext().getString(R.string.tv_playback_epg_selected, title);
        holder.binding.title.setText(title);
        holder.binding.getRoot().setContentDescription(item.getTime() + " " + title);
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setLeftListener(mListener::hideEpg);
        holder.binding.getRoot().setOnClickListener(v -> {
            if (!item.isFuture()) mListener.onItemClick(item);
        });
    }

    public interface OnClickListener {

        void hideEpg();

        void onItemClick(EpgData item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterEpgDataBinding binding;

        ViewHolder(@NonNull AdapterEpgDataBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
