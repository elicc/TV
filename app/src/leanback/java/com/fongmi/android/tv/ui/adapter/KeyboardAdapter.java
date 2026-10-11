package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterKeyboardIconBinding;
import com.fongmi.android.tv.databinding.AdapterKeyboardTextBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class KeyboardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<Integer> icons = Arrays.asList(R.drawable.ic_keyboard_back, R.drawable.ic_keyboard_remote, R.drawable.ic_keyboard_search);
    private final List<Group> enGroups = Arrays.asList(
            new Group("1\n0", new String[]{"1", "0"}),
            new Group("2\nABC", new String[]{"A", "B", "C", "2"}),
            new Group("3\nDEF", new String[]{"D", "E", "F", "3"}),
            new Group("4\nGHI", new String[]{"G", "H", "I", "4"}),
            new Group("5\nJKL", new String[]{"J", "K", "L", "5"}),
            new Group("6\nMNO", new String[]{"M", "N", "O", "6"}),
            new Group("7\nPQRS", new String[]{"P", "Q", "R", "S", "7"}),
            new Group("8\nTUV", new String[]{"T", "U", "V", "8"}),
            new Group("9\nWXYZ", new String[]{"W", "X", "Y", "Z", "9"}));
    private final OnClickListener listener;
    private final List<Object> mItems;
    private int expandedGroup = -1;

    public KeyboardAdapter(OnClickListener listener) {
        this.mItems = new ArrayList<>();
        this.listener = listener;
        rebuildItems();
    }

    public interface OnClickListener {

        void onTextClick(String text);

        void onGroupClick(int position);

        void onIconClick(int resId);

        boolean onLongClick(int resId);
    }

    public void expand(int position) {
        if (isExpanded() || position < 0 || position >= enGroups.size()) return;
        expandedGroup = position;
        rebuildItems();
        notifyDataSetChanged();
    }

    public boolean isExpanded() {
        return expandedGroup >= 0;
    }

    public int collapse() {
        if (!isExpanded()) return -1;
        int position = expandedGroup;
        expandedGroup = -1;
        rebuildItems();
        notifyDataSetChanged();
        return position;
    }

    private void rebuildItems() {
        mItems.clear();
        if (isExpanded()) {
            mItems.addAll(Arrays.asList(enGroups.get(expandedGroup).letters));
            // Keep delete at row 2, column 1; blanks preserve the three-column grid.
            while (mItems.size() < 3) mItems.add("");
            mItems.add(3, R.drawable.ic_keyboard_back);
            while (mItems.size() < 6) mItems.add("");
        } else {
            mItems.addAll(enGroups);
            mItems.addAll(icons);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return mItems.get(position) instanceof String || mItems.get(position) instanceof Group ? 0 : 1;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 0) return new TextHolder(AdapterKeyboardTextBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        else return new IconHolder(AdapterKeyboardIconBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        switch (getItemViewType(position)) {
            case 0:
                TextHolder text = (TextHolder) holder;
                String label = mItems.get(position).toString();
                text.binding.text.setText(label);
                text.binding.text.setTextSize(isExpanded() ? 24 : 16);
                text.itemView.setVisibility(label.isEmpty() ? View.INVISIBLE : View.VISIBLE);
                text.itemView.setFocusable(!label.isEmpty());
                text.itemView.setFocusableInTouchMode(!label.isEmpty());
                break;
            case 1:
                IconHolder icon = (IconHolder) holder;
                icon.binding.icon.setImageResource((int) mItems.get(position));
                break;
        }
    }

    class TextHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final AdapterKeyboardTextBinding binding;

        TextHolder(@NonNull AdapterKeyboardTextBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            Object item = mItems.get(position);
            if (item instanceof Group) listener.onGroupClick(position);
            else if (!item.toString().isEmpty()) listener.onTextClick(item.toString());
        }
    }

    class IconHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        private final AdapterKeyboardIconBinding binding;

        IconHolder(@NonNull AdapterKeyboardIconBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            listener.onIconClick((int) mItems.get(position));
        }

        @Override
        public boolean onLongClick(View view) {
            int position = getBindingAdapterPosition();
            return position != RecyclerView.NO_POSITION && listener.onLongClick((int) mItems.get(position));
        }
    }

    private static class Group {

        private final String title;
        private final String[] letters;

        private Group(String title, String[] letters) {
            this.title = title;
            this.letters = letters;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}
