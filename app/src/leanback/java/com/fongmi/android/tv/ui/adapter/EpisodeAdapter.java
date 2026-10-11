package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.AdapterEpisodeBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Episode> mItems;
    private final int maxWidth;
    private int numberOffset = -1;
    private int nextFocusDown;
    private int nextFocusUp;

    public EpisodeAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
        maxWidth = ResUtil.dp2px(220);
    }

    public EpisodeAdapter numbersOnly(int offset) {
        numberOffset = offset;
        return this;
    }

    public List<Episode> getItems() {
        return new ArrayList<>(mItems);
    }

    public void addAll(List<Episode> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public int getPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    public Episode getActivated() {
        return mItems.isEmpty() ? new Episode() : mItems.get(getPosition());
    }

    public Episode getNext() {
        int current = getPosition();
        int max = getItemCount() - 1;
        current = ++current > max ? max : current;
        return mItems.get(current);
    }

    public Episode getPrev() {
        int current = getPosition();
        current = --current < 0 ? 0 : current;
        return mItems.get(current);
    }

    public void setNextFocusDown(int nextFocusDown) {
        this.nextFocusDown = nextFocusDown;
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
        return new ViewHolder(AdapterEpisodeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Episode item = mItems.get(position);
        holder.binding.text.setMaxWidth(maxWidth);
        holder.binding.text.setMaxLines(1);
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
        holder.binding.text.setNextFocusUpId(numberOffset >= 0 && nextFocusUp == 0 ? View.NO_ID : nextFocusUp);
        holder.binding.text.setNextFocusDownId(numberOffset >= 0 ? View.NO_ID : nextFocusDown);
        holder.binding.text.setSelected(item.isSelected());
        holder.binding.text.setText(numberOffset >= 0
                ? String.valueOf((item.getIndex() >= 0 ? item.getIndex() : numberOffset + position) + 1)
                : item.getDesc().concat(item.getName()));
        if (numberOffset >= 0) {
            holder.binding.text.getLayoutParams().width = ResUtil.dp2px(48);
            holder.binding.text.setMinHeight(ResUtil.dp2px(40));
            holder.binding.text.setTextSize(18);
        }
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
    }

    public interface OnClickListener {

        void onItemClick(Episode item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterEpisodeBinding binding;

        ViewHolder(@NonNull AdapterEpisodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
