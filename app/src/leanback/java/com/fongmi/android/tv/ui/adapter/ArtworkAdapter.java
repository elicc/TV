package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterArtworkBinding;
import com.fongmi.android.tv.metadata.MovieArtwork;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Small, focusable TV gallery for provider-supplied landscape photos. */
public class ArtworkAdapter extends RecyclerView.Adapter<ArtworkAdapter.ViewHolder> {
    private final List<MovieArtwork> items = new ArrayList<>();
    private final Set<ViewHolder> attachedHolders = new HashSet<>();
    private final OnArtworkFocusListener listener;
    private int selectedPosition = -1;

    public interface OnArtworkFocusListener {
        void onArtworkFocus(int position, MovieArtwork artwork);
    }

    public ArtworkAdapter(OnArtworkFocusListener listener) {
        this.listener = listener;
    }

    public void addAll(List<MovieArtwork> artworks) {
        items.clear();
        if (artworks != null) items.addAll(artworks);
        selectedPosition = items.isEmpty() ? -1 : 0;
        notifyDataSetChanged();
    }

    public void clear() {
        if (items.isEmpty()) return;
        items.clear();
        selectedPosition = -1;
        notifyDataSetChanged();
    }

    public void setSelectedPosition(int position) {
        if (position < 0 || position >= items.size() || position == selectedPosition) return;
        selectedPosition = position;
        // Leanback can dispatch focus during layout. Selection is view state,
        // not changed artwork data: notifying here would re-enter RecyclerView.
        for (ViewHolder holder : attachedHolders) updateSelection(holder);
    }

    private void updateSelection(ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        holder.itemView.setSelected(position != RecyclerView.NO_POSITION && position == selectedPosition);
    }

    @Override
    public void onViewAttachedToWindow(@NonNull ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        attachedHolders.add(holder);
        // Cached children may reattach without being rebound.
        updateSelection(holder);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ViewHolder holder) {
        attachedHolders.remove(holder);
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public int getItemCount() { return items.size(); }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterArtworkBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MovieArtwork artwork = items.get(position);
        ImgUtil.load("", artwork.getUrl(), holder.binding.image, true);
        holder.binding.getRoot().setSelected(position == selectedPosition);
        holder.binding.getRoot().setOnFocusChangeListener((view, hasFocus) -> {
            int current = holder.getBindingAdapterPosition();
            if (hasFocus && current != RecyclerView.NO_POSITION) listener.onArtworkFocus(current, items.get(current));
        });
        holder.binding.getRoot().setOnClickListener(view -> {
            int current = holder.getBindingAdapterPosition();
            if (current != RecyclerView.NO_POSITION) listener.onArtworkFocus(current, items.get(current));
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterArtworkBinding binding;

        ViewHolder(AdapterArtworkBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
