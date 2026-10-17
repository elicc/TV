package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;

/** Optional final card in the source rail; never represents a playable source. */
public class QuickStatusAdapter extends RecyclerView.Adapter<QuickStatusAdapter.ViewHolder> {

    private final Runnable retry;
    private CharSequence message;
    private boolean retryable;

    public QuickStatusAdapter(Runnable retry) {
        this.retry = retry;
    }

    /** {@code null} removes the card; the text carries counts, so compare by content. */
    public void setStatus(@Nullable CharSequence message, boolean retryable) {
        if (TextUtils.equals(this.message, message) && this.retryable == retryable) return;
        boolean wasVisible = this.message != null;
        this.message = message;
        this.retryable = retryable;
        if (!wasVisible && message != null) notifyItemInserted(0);
        else if (wasVisible && message == null) notifyItemRemoved(0);
        else if (wasVisible) notifyItemChanged(0);
    }

    @Override
    public int getItemCount() {
        return message == null ? 0 : 1;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder((TextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.adapter_quick_status, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TextView text = (TextView) holder.itemView;
        text.setText(message);
        text.setOnClickListener(view -> {
            if (message != null && retryable) retry.run();
        });
        text.setClickable(retryable);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        ViewHolder(TextView view) {
            super(view);
        }
    }
}
