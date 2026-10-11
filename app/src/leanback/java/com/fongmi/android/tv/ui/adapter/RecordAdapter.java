package com.fongmi.android.tv.ui.adapter;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterSearchRecordBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.KeyUtil;
import com.google.gson.reflect.TypeToken;
import com.google.android.flexbox.FlexboxLayoutManager;

import java.util.ArrayList;
import java.util.List;

public class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.ViewHolder> {

    public static final int MAX_RECORDS = 50;

    private final OnClickListener listener;
    private final List<String> mItems;
    private boolean editing;
    private boolean expanded;
    private int collapsedCount = MAX_RECORDS;
    private boolean overflow;
    private boolean wrapLast;
    private int lastMaxWidth = Integer.MAX_VALUE;

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

        void onToggleExpanded();
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
        return overflow;
    }

    private int visibleRecords() {
        return editing || expanded ? mItems.size() : Math.min(collapsedCount, mItems.size());
    }

    public int getTogglePosition() {
        return !editing && overflow ? visibleRecords() : RecyclerView.NO_POSITION;
    }

    // Measure both terms and the inline action with their real padding/margins.
    public void updateCollapsedCount(ViewGroup parent) {
        int width = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        if (width <= 0) return;
        AdapterSearchRecordBinding chip = AdapterSearchRecordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) chip.getRoot().getLayoutParams();
        int margins = params.leftMargin + params.rightMargin;
        int[] widths = new int[mItems.size()];
        int[] heights = new int[mItems.size()];
        for (int i = 0; i < mItems.size(); i++) {
            measureChip(chip, mItems.get(i), width - margins);
            widths[i] = chip.getRoot().getMeasuredWidth() + margins;
            heights[i] = chip.getRoot().getMeasuredHeight() + params.topMargin + params.bottomMargin;
        }
        Flow full = measureFlow(width, widths, heights, mItems.size(), 0, 0);
        boolean nextOverflow = full.rows > 2;
        int count = mItems.size();
        Flow visible = full;
        if (nextOverflow) {
            styleAction(chip);
            measureChip(chip, parent.getContext().getString(R.string.tv_search_more), width - margins);
            int moreWidth = chip.getRoot().getMeasuredWidth() + margins;
            int actionHeight = chip.getRoot().getMeasuredHeight() + params.topMargin + params.bottomMargin;
            do {
                count--;
                visible = measureFlow(width, widths, heights, count, moreWidth, actionHeight);
            } while (count > 1 && visible.rows > 2);
            if (expanded && !editing) {
                measureChip(chip, parent.getContext().getString(R.string.tv_search_less), width - margins);
                visible = measureFlow(width, widths, heights, mItems.size(),
                        chip.getRoot().getMeasuredWidth() + margins, actionHeight);
            }
        }
        if (editing) visible = full;
        int maxWidth = nextOverflow && !editing ? Math.max(0, visible.lastWidth - margins) : Integer.MAX_VALUE;
        boolean changed = collapsedCount != count || overflow != nextOverflow
                || wrapLast != visible.wrapLast || lastMaxWidth != maxWidth;
        collapsedCount = count;
        overflow = nextOverflow;
        wrapLast = visible.wrapLast;
        lastMaxWidth = maxWidth;
        if (changed) notifyDataSetChanged();
        // Only the surrounding NestedScrollView scrolls, including all 50 terms.
        int desiredHeight = visible.height + parent.getPaddingTop() + parent.getPaddingBottom();
        ViewGroup.LayoutParams layout = parent.getLayoutParams();
        if (layout.height != desiredHeight) {
            layout.height = desiredHeight;
            parent.setLayoutParams(layout);
        }
    }

    private void measureChip(AdapterSearchRecordBinding chip, String text, int width) {
        chip.text.setText(text);
        // Detached views need forced measurement after text/background changes.
        chip.text.forceLayout();
        chip.getRoot().forceLayout();
        chip.getRoot().measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, width), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    }

    private static class Flow {
        int rows, height, lastWidth;
        boolean wrapLast;
    }

    private Flow measureFlow(int width, int[] widths, int[] heights, int count, int actionWidth, int actionHeight) {
        Flow flow = new Flow();
        int used = 0, rowHeight = 0;
        for (int i = 0; i < count; i++) {
            boolean last = i == count - 1 && actionWidth > 0;
            // Keep the final term and action together. Extremely long terms ellipsize
            // instead of stranding More/Less on a line of its own.
            int termWidth = last ? Math.min(widths[i], Math.max(0, width - actionWidth)) : widths[i];
            int size = termWidth + (last ? actionWidth : 0);
            boolean wrap = used > 0 && used + size > width;
            if (wrap) {
                flow.height += rowHeight;
                flow.rows++;
                used = 0;
                rowHeight = 0;
            }
            used += size;
            rowHeight = Math.max(rowHeight, Math.max(heights[i], last ? actionHeight : 0));
            if (last) {
                flow.wrapLast = wrap;
                flow.lastWidth = termWidth;
            }
        }
        if (count > 0) {
            flow.rows++;
            flow.height += rowHeight;
        }
        return flow;
    }

    private void styleAction(AdapterSearchRecordBinding binding) {
        binding.text.setBackgroundResource(R.drawable.tv_search_record_action);
        binding.text.setTextColor(AppCompatResources.getColorStateList(binding.getRoot().getContext(), R.color.selector_search_record_action));
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
        return visibleRecords() + (getTogglePosition() == RecyclerView.NO_POSITION ? 0 : 1);
    }

    @Override
    public int getItemViewType(int position) {
        return position == getTogglePosition() ? 1 : 0;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AdapterSearchRecordBinding binding = AdapterSearchRecordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        if (viewType == 1) styleAction(binding);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.deleteKeyDown = false;
        holder.itemView.setPressed(false);
        boolean action = position == getTogglePosition();
        FlexboxLayoutManager.LayoutParams params = (FlexboxLayoutManager.LayoutParams) holder.itemView.getLayoutParams();
        boolean last = getTogglePosition() != RecyclerView.NO_POSITION && position == visibleRecords() - 1;
        params.setMaxWidth(last ? lastMaxWidth : Integer.MAX_VALUE);
        params.setWrapBefore(last && wrapLast);
        holder.itemView.setLayoutParams(params);
        String text = action ? holder.itemView.getContext().getString(expanded ? R.string.tv_search_less : R.string.tv_search_more) : mItems.get(position);
        holder.binding.text.setText(text);
        holder.binding.delete.setVisibility(editing && !action ? View.VISIBLE : View.GONE);
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
            if (position == getTogglePosition()) listener.onToggleExpanded();
            else if (editing) listener.onItemDelete(mItems.get(position));
            else listener.onItemClick(mItems.get(position));
        }

        @Override
        public boolean onLongClick(View v) {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return false;
            if (editing || position == getTogglePosition()) return true;
            listener.onItemLongClick(mItems.get(position));
            return true;
        }
    }
}
