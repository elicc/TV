package com.fongmi.android.tv.ui.adapter;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterSearchRecordBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.KeyUtil;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.ViewHolder> {

    public static final int MAX_RECORDS = 50;

    private final OnClickListener listener;
    private final List<String> mItems;
    private boolean editing;
    private boolean expanded;
    private int collapsedCount = MAX_RECORDS;

    public RecordAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = getItems();
        if (mItems.size() > MAX_RECORDS) mItems.subList(MAX_RECORDS, mItems.size()).clear();
        this.listener.onDataChanged(mItems.size());
    }

    public interface OnClickListener {

        void onItemClick(String text);

        void onDataChanged(int size);

        void onItemLongClick(String text);

        void onItemDelete(String text);
    }

    private List<String> getItems() {
        if (Setting.getKeyword().isEmpty()) return new ArrayList<>();
        return App.gson().fromJson(Setting.getKeyword(), TypeToken.getParameterized(List.class, String.class).getType());
    }

    public void refresh() {
        List<String> items = getItems();
        if (items.size() > MAX_RECORDS) items.subList(MAX_RECORDS, items.size()).clear();
        if (mItems.equals(items)) return;
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
        listener.onDataChanged(mItems.size());
    }

    private void checkToAdd(String item) {
        mItems.remove(item);
        mItems.add(0, item);
        if (mItems.size() > MAX_RECORDS) mItems.subList(MAX_RECORDS, mItems.size()).clear();
    }

    public void add(String item) {
        refresh();
        checkToAdd(item);
        Setting.putKeyword(App.gson().toJson(mItems));
        notifyDataSetChanged();
        listener.onDataChanged(mItems.size());
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        notifyDataSetChanged();
    }

    public boolean hasOverflow() {
        return mItems.size() > collapsedCount;
    }

    // Measure the actual chip layout: character counts do not predict CJK/Latin widths.
    public void updateCollapsedCount(ViewGroup parent) {
        int width = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        if (width <= 0) return;
        AdapterSearchRecordBinding chip = AdapterSearchRecordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) chip.getRoot().getLayoutParams();
        int margins = params.leftMargin + params.rightMargin;
        int row = 1, used = 0, count = 0, rowHeight = 0, height = 0, collapsedHeight = 0;
        for (String item : mItems) {
            chip.text.setText(item);
            // This detached measuring view is never laid out, so setText's
            // requestLayout alone may leave the previous measurement cached.
            chip.text.forceLayout();
            chip.getRoot().forceLayout();
            chip.getRoot().measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, width - margins), View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int size = chip.getRoot().getMeasuredWidth() + margins;
            if (used > 0 && used + size > width) {
                height += rowHeight;
                if (row <= 2) collapsedHeight = height;
                row++;
                used = 0;
                rowHeight = 0;
            }
            used += size;
            rowHeight = Math.max(rowHeight, chip.getRoot().getMeasuredHeight() + params.topMargin + params.bottomMargin);
            if (row <= 2) count++;
        }
        height += rowHeight;
        if (row <= 2) collapsedHeight = height;
        if (collapsedCount != count) {
            collapsedCount = count;
            if (!editing && !expanded) notifyDataSetChanged();
        }
        // Give the inner list its full height; only the surrounding NestedScrollView
        // should scroll. This also keeps all 50 chips reachable in expanded mode.
        int desiredHeight = (editing || expanded ? height : collapsedHeight) + parent.getPaddingTop() + parent.getPaddingBottom();
        ViewGroup.LayoutParams layout = parent.getLayoutParams();
        if (layout.height != desiredHeight) {
            layout.height = desiredHeight;
            parent.setLayoutParams(layout);
        }
    }

    public boolean isEditing() {
        return editing;
    }

    public void setEditing(boolean editing) {
        if (this.editing == editing) return;
        this.editing = editing;
        notifyDataSetChanged();
    }

    public int remove(String text) {
        // A remote search can open another SearchActivity. Never overwrite its
        // newer history with this screen's stale snapshot when deleting one term.
        refresh();
        int position = mItems.indexOf(text);
        if (position < 0) return -1;
        mItems.remove(position);
        Setting.putKeyword(App.gson().toJson(mItems));
        // A removal can rewrap every later chip. Rebuild the wrap-content Flexbox
        // instead of retaining its incremental line/anchor cache.
        notifyDataSetChanged();
        listener.onDataChanged(mItems.size());
        return position;
    }

    public void clear() {
        mItems.clear();
        Setting.putKeyword(App.gson().toJson(mItems));
        notifyDataSetChanged();
        listener.onDataChanged(0);
    }

    @Override
    public int getItemCount() {
        return editing || expanded ? mItems.size() : Math.min(collapsedCount, mItems.size());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSearchRecordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.deleteKeyDown = false;
        holder.itemView.setPressed(false);
        String text = mItems.get(position);
        holder.binding.text.setText(text);
        holder.binding.delete.setVisibility(editing ? View.VISIBLE : View.GONE);
        holder.itemView.setContentDescription(editing
                ? holder.itemView.getContext().getString(R.string.tv_search_delete_term, text) : text);
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        private final AdapterSearchRecordBinding binding;
        private boolean deleteKeyDown;

        public ViewHolder(@NonNull AdapterSearchRecordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
            itemView.setOnKeyListener((view, keyCode, event) -> {
                if (!editing || !KeyUtil.isEnterKey(event)) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    deleteKeyDown = true;
                    view.setPressed(true);
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    boolean delete = deleteKeyDown && !event.isCanceled();
                    deleteKeyDown = false;
                    view.setPressed(false);
                    if (delete) view.performClick();
                }
                return true;
            });
            itemView.setOnFocusChangeListener((view, focused) -> {
                if (!focused) {
                    deleteKeyDown = false;
                    view.setPressed(false);
                }
            });
        }

        @Override
        public void onClick(View v) {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            if (editing) listener.onItemDelete(mItems.get(position));
            else listener.onItemClick(mItems.get(position));
        }

        @Override
        public boolean onLongClick(View v) {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return false;
            if (editing) return true;
            listener.onItemLongClick(mItems.get(position));
            return true;
        }
    }
}
