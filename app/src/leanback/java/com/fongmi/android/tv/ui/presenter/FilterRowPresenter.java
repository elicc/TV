package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.databinding.AdapterFilterRowBinding;
import com.fongmi.android.tv.utils.ResUtil;

/** A fixed-height condition row: persistent label on the left, options on the right. */
public class FilterRowPresenter extends Presenter {

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        AdapterFilterRowBinding binding = AdapterFilterRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        binding.options.setHorizontalSpacing(ResUtil.dp2px(8));
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        ViewHolder holder = (ViewHolder) viewHolder;
        FilterRow row = (FilterRow) object;
        holder.row = row;
        holder.binding.title.setText(row.title);
        holder.binding.options.setAdapter(new ItemBridgeAdapter(row.getAdapter()));
        holder.binding.options.setSelectedPosition(row.selectedPosition);
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
        ViewHolder holder = (ViewHolder) viewHolder;
        if (holder.row != null) holder.row.selectedPosition = Math.max(0, holder.binding.options.getSelectedPosition());
        holder.binding.options.setAdapter(null);
        holder.row = null;
    }

    public static final class FilterRow extends ListRow {

        private final String title;
        private int selectedPosition;

        public FilterRow(String title, ObjectAdapter adapter) {
            super(adapter);
            this.title = title;
        }
    }

    private static final class ViewHolder extends Presenter.ViewHolder {

        private final AdapterFilterRowBinding binding;
        private FilterRow row;

        private ViewHolder(AdapterFilterRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
