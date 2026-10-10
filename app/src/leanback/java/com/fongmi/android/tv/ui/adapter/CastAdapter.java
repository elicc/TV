package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.databinding.AdapterCastBinding;
import com.fongmi.android.tv.metadata.MovieCast;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

/** Compact, focusable cast cards used by the TV detail page. */
public class CastAdapter extends RecyclerView.Adapter<CastAdapter.ViewHolder> {
    private final List<MovieCast> items = new ArrayList<>();

    public void addAll(List<MovieCast> cast) {
        items.clear();
        if (cast != null) items.addAll(cast);
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
        return new ViewHolder(AdapterCastBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MovieCast cast = items.get(position);
        String name = cast == null ? "" : cast.getName();
        String avatar = cast == null ? "" : cast.getAvatar();
        String character = cast == null ? "" : cast.getCharacter();
        holder.binding.name.setText(name);
        // A recycled holder must not retain a previous person's bitmap while a
        // new request is in flight. ImgUtil supplies the deterministic initial.
        Glide.with(holder.binding.avatar).clear(holder.binding.avatar);
        ImgUtil.load(name, avatar, holder.binding.avatar, true);
        holder.binding.getRoot().setContentDescription(character.isEmpty() ? name : name + " · " + character);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterCastBinding binding;

        ViewHolder(AdapterCastBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
