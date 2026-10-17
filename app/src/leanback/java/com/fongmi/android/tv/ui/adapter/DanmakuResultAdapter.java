package com.fongmi.android.tv.ui.adapter;

import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.databinding.AdapterDanmakuResultBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Danmaku search results for the TV drawer; the loaded source keeps the choice underline. */
public class DanmakuResultAdapter extends RecyclerView.Adapter<DanmakuResultAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Danmaku> mItems;
    private Danmaku mCurrent;

    public DanmakuResultAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(Danmaku item);
    }

    /** Search APIs carry no platform field, so it is read from the name or the decoded URL. */
    private enum Platform {
        TENCENT(R.string.tv_danmaku_platform_tencent, "tencent", "v.qq.com", "【qq】", "[qq]", "腾讯", "騰訊"),
        IQIYI(R.string.tv_danmaku_platform_iqiyi, "iqiyi", "qiyi.com", "爱奇艺", "愛奇藝"),
        YOUKU(R.string.tv_danmaku_platform_youku, "youku", "优酷", "優酷"),
        BILIBILI(R.string.tv_danmaku_platform_bilibili, "bilibili", "b23.tv", "哔哩", "嗶哩"),
        MGTV(R.string.tv_danmaku_platform_mgtv, "mgtv", "imgo.tv", "芒果"),
        SOHU(R.string.tv_danmaku_platform_sohu, "sohu", "搜狐"),
        LETV(R.string.tv_danmaku_platform_letv, "letv", "乐视", "樂視");

        private final int label;
        private final String[] keys;

        Platform(int label, String... keys) {
            this.label = label;
            this.keys = keys;
        }
    }

    public static int platform(Danmaku item) {
        String text = (item.getName() + " " + Uri.decode(item.getUrl())).toLowerCase(Locale.ROOT);
        for (Platform platform : Platform.values()) for (String key : platform.keys) if (text.contains(key)) return platform.label;
        return 0;
    }

    public void setItems(List<Danmaku> items, Danmaku current) {
        mItems.clear();
        if (items != null) mItems.addAll(items);
        mCurrent = current;
        notifyDataSetChanged();
    }

    public void clear() {
        setItems(null, null);
    }

    public int getPosition() {
        int position = mItems.indexOf(mCurrent);
        return Math.max(position, 0);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterDanmakuResultBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Danmaku item = mItems.get(position);
        int platform = platform(item);
        View root = holder.binding.getRoot();
        holder.binding.name.setText(item.getName());
        holder.binding.platform.setText(platform == 0 ? "" : root.getContext().getString(platform));
        holder.binding.platform.setVisibility(platform == 0 ? View.GONE : View.VISIBLE);
        root.setSelected(item.equals(mCurrent));
        // Selection reaches the name too; only focus may run its marquee.
        holder.binding.name.setSelected(root.isFocused());
        root.setOnFocusChangeListener((view, focused) -> {
            holder.binding.name.setEllipsize(focused ? TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.END);
            holder.binding.name.setSelected(focused);
        });
        root.setOnClickListener(view -> mListener.onItemClick(item));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterDanmakuResultBinding binding;

        ViewHolder(@NonNull AdapterDanmakuResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
