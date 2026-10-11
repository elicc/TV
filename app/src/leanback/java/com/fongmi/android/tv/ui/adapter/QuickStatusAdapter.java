package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;

/** Optional final card in the source rail; never represents a playable source. */
public class QuickStatusAdapter extends RecyclerView.Adapter<QuickStatusAdapter.ViewHolder> {

    private final Runnable retry;
    private int message;
    private boolean retryable;

    public QuickStatusAdapter(Runnable retry) {
        this.retry = retry;
    }

    public void setStatus(int message, boolean retryable) {
        if (this.message == message && this.retryable == retryable) return;
        boolean wasVisible = this.message != 0;
        this.message = message;
        this.retryable = retryable;
        if (!wasVisible && message != 0) notifyItemInserted(0);
        else if (wasVisible && message == 0) notifyItemRemoved(0);
        else if (wasVisible) notifyItemChanged(0);
    }

    @Override
    public int getItemCount() {
        return message == 0 ? 0 : 1;
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
            if (message != 0 && retryable) retry.run();
        });
        text.setClickable(retryable);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        ViewHolder(TextView view) {
            super(view);
        }
    }
}
