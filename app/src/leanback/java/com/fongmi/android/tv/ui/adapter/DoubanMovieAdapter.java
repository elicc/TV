package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.text.TextUtils;
import android.widget.TextView;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterHotMovieBinding;
import com.fongmi.android.tv.metadata.MovieMetadata;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared display-only cards for discovery grids and related-title rails. */
public class DoubanMovieAdapter extends RecyclerView.Adapter<DoubanMovieAdapter.ViewHolder> {

    private final List<MovieMetadata> items = new ArrayList<>();
    private final OnClickListener listener;
    private final boolean horizontal;

    public DoubanMovieAdapter(OnClickListener listener, boolean horizontal) {
        this.listener = listener;
        this.horizontal = horizontal;
    }

    public interface OnClickListener {
        void onItemClick(MovieMetadata item);
    }

    public void setItems(List<MovieMetadata> movies) {
        items.clear();
        if (movies != null) items.addAll(movies);
        notifyDataSetChanged();
    }

    public void clear() {
        if (items.isEmpty()) return;
        items.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(AdapterHotMovieBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        if (horizontal) holder.itemView.getLayoutParams().width = ResUtil.dp2px(132);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MovieMetadata item = items.get(position);
        holder.binding.title.setText(item.getTitle());
        holder.binding.year.setText(item.getYear());
        holder.binding.type.setText(item.getType());
        holder.binding.rating.setText(item.getRating() > 0
                ? String.format(Locale.US, "★ %.1f", item.getRating()) : "—");
        holder.binding.ratingCount.setText(formatCount(item.getRatingCount()));
        holder.binding.rank.setText(String.valueOf(position + 1));
        holder.binding.rank.setVisibility(horizontal ? View.GONE : View.VISIBLE);
        ImgUtil.load(item.getTitle(), item.getPoster(), holder.binding.image, true);
        holder.itemView.setContentDescription(item.getTitle() + ", " + item.getYear() + ", " + item.getType()
                + (horizontal ? ", " + holder.itemView.getContext().getString(R.string.tv_douban_search_source) : ""));
        holder.setMarquee(holder.itemView.hasFocus());
        holder.itemView.setOnClickListener(view -> listener.onItemClick(item));
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        holder.setMarquee(false);
        super.onViewRecycled(holder);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ViewHolder holder) {
        holder.setMarquee(false);
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public void onViewAttachedToWindow(@NonNull ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        holder.setMarquee(holder.itemView.hasFocus());
    }

    private static String formatCount(int count) {
        if (count <= 0) return "";
        if (count >= 10000) return String.format(Locale.US, "%.1fw", count / 10000d);
        if (count >= 1000) return String.format(Locale.US, "%.1fk", count / 1000d);
        return String.valueOf(count);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterHotMovieBinding binding;

        ViewHolder(AdapterHotMovieBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnFocusChangeListener((view, focused) -> setMarquee(focused));
        }

        private void setMarquee(boolean focused) {
            for (TextView text : new TextView[]{binding.title, binding.year, binding.type}) {
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
