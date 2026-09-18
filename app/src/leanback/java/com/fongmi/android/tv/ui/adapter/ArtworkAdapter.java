package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterArtworkBinding;
import com.fongmi.android.tv.metadata.MovieArtwork;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

/** Small, focusable TV gallery for provider-supplied landscape photos. */
public class ArtworkAdapter extends RecyclerView.Adapter<ArtworkAdapter.ViewHolder> {
    private final List<MovieArtwork> items = new ArrayList<>();

    public void addAll(List<MovieArtwork> artworks) {
        items.clear();
        if (artworks != null) items.addAll(artworks);
        notifyDataSetChanged();
    }

    public void clear() {
        if (items.isEmpty()) return;
        items.clear();
        notifyDataSetChanged();
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
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterArtworkBinding binding;

        ViewHolder(AdapterArtworkBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
