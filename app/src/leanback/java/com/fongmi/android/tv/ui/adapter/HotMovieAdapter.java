package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterHotMovieBinding;
import com.fongmi.android.tv.metadata.MovieMetadata;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Focusable Douban hot-movie cards used by the empty search state. */
public class HotMovieAdapter extends RecyclerView.Adapter<HotMovieAdapter.ViewHolder> {

    private final List<MovieMetadata> items = new ArrayList<>();
    private final OnClickListener listener;

    public HotMovieAdapter(OnClickListener listener) {
        this.listener = listener;
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
        return new ViewHolder(AdapterHotMovieBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
        ImgUtil.load(item.getTitle(), item.getPoster(), holder.binding.image, true);
        holder.itemView.setContentDescription(item.getTitle());
        holder.itemView.setOnClickListener(view -> listener.onItemClick(item));
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
        }
    }
}
