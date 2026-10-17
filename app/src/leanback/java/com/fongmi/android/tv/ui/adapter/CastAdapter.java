package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.databinding.AdapterCastBinding;
import com.fongmi.android.tv.metadata.MovieCast;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compact, focusable cast cards used by the TV detail page. */
public class CastAdapter extends RecyclerView.Adapter<CastAdapter.ViewHolder> {
    private static final Pattern ROLE = Pattern.compile("^[饰飾配]\\s+(.+)$");
    private static final Set<String> JOB_TITLES = Set.of("演员", "演員", "配音", "导演", "導演", "编剧", "編劇", "制片人", "製片人");

    private final List<MovieCast> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(String name);
    }

    public CastAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

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
        String role = role(character);
        holder.binding.character.setText(role);
        holder.binding.character.setVisibility(role.isEmpty() ? View.GONE : View.VISIBLE);
        // A recycled holder must not retain a previous person's bitmap while a
        // new request is in flight. ImgUtil supplies the deterministic initial.
        Glide.with(holder.binding.avatar).clear(holder.binding.avatar);
        ImgUtil.load(name, avatar, holder.binding.avatar, true);
        holder.binding.getRoot().setContentDescription(character.isEmpty() ? name : name + " · " + character);
        holder.binding.getRoot().setOnClickListener(view -> {
            int current = holder.getBindingAdapterPosition();
            if (current == RecyclerView.NO_POSITION || current >= items.size()) return;
            MovieCast person = items.get(current);
            if (person != null && !person.getName().isEmpty()) listener.onItemClick(person.getName());
        });
    }

    /** Whether any credit carries a role name worth a second line. */
    public static boolean hasRoles(List<MovieCast> cast) {
        if (cast == null) return false;
        for (MovieCast person : cast) if (person != null && !role(person.getCharacter()).isEmpty()) return true;
        return false;
    }

    /**
     * Douban writes roles as "饰 沈星" (or "配 沈星" for voice parts) and otherwise only a job
     * title such as "配音"; job titles carry no role name, so they are not shown.
     */
    private static String role(String character) {
        String role = character == null ? "" : character.trim();
        Matcher matcher = ROLE.matcher(role);
        if (matcher.matches()) return matcher.group(1).trim();
        return JOB_TITLES.contains(role) ? "" : role;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterCastBinding binding;

        ViewHolder(AdapterCastBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
