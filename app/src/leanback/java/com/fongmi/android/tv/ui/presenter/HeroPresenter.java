package com.fongmi.android.tv.ui.presenter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterHeroBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.TvTheme;

import java.util.ArrayList;
import java.util.List;

/** One current-source item, without extra detail requests or automatic rotation. */
public final class HeroPresenter extends Presenter {

    public record Item(Vod vod, String sourceKey, String sourceName, boolean configured, boolean loading, boolean configFailed, String error, boolean history) {
    }

    public interface Listener {
        void onHeroClick(Vod vod);

        void onHeroBrowse();

        void onHeroConfigure();

        void onHeroRetry();
    }

    private final Listener listener;

    public HeroPresenter(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new Holder(AdapterHeroBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, Object object) {
        Holder holder = (Holder) viewHolder;
        AdapterHeroBinding b = holder.binding;
        Item item = (Item) object;
        Vod vod = item.vod();
        boolean largeText = b.getRoot().getResources().getConfiguration().fontScale > 1.15f;
        int heroHeight = ResUtil.dp2px(vod != null && (!item.history() || largeText) ? 152 : 216);
        b.getRoot().getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        b.getRoot().setMinimumHeight(heroHeight);
        // Do not let a drawable's intrinsic height expand the wrap-content row.
        b.poster.getLayoutParams().height = heroHeight;
        b.name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, b.getRoot().getResources().getDimension(item.history() && !largeText ? R.dimen.tv_text_hero : R.dimen.tv_text_title));
        int emptyTitle = item.loading() ? R.string.tv_loading_title : item.configFailed() ? R.string.tv_config_error_title : !TextUtils.isEmpty(item.error()) ? R.string.tv_content_error_title : R.string.tv_no_content_title;
        b.name.setText(vod != null ? vod.getName() : b.getRoot().getContext().getString(emptyTitle));
        bindBadge(b, vod);
        bindWatermark(b, vod == null ? "" : vod.getName());
        List<String> meta = new ArrayList<>();
        if (vod != null) {
            for (String text : new String[]{vod.getYear(), vod.getTypeName(), vod.getRemarks()}) {
                if (!TextUtils.isEmpty(text)) meta.add(text);
            }
        }
        b.metadata.setText(TextUtils.join(" · ", meta));
        b.metadata.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);
        int emptyBody = item.configFailed() ? R.string.tv_config_error_body : !TextUtils.isEmpty(item.error()) ? R.string.tv_content_error_body : R.string.tv_empty_body;
        String description = vod == null ? b.getRoot().getContext().getString(emptyBody) : HtmlCompat.fromHtml(vod.getContent(), HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim();
        b.description.setMaxLines(vod == null ? 2 : 1);
        b.description.setText(description);
        b.description.setVisibility(item.loading() || TextUtils.isEmpty(description) || (vod != null && (!item.history() || largeText)) ? View.GONE : View.VISIBLE);
        b.primary.setText(item.loading() ? R.string.tv_loading_title : vod != null ? R.string.tv_details : item.configFailed() ? R.string.tv_reload_config : R.string.tv_retry);
        boolean configureSecondary = item.configFailed();
        b.secondary.setText(configureSecondary ? R.string.home_setting : R.string.tv_browse);
        b.primary.setOnClickListener(v -> {
            if (item.loading()) return;
            if (vod != null) listener.onHeroClick(vod);
            else if (item.configured()) listener.onHeroRetry();
            else listener.onHeroConfigure();
        });
        b.secondary.setOnClickListener(v -> {
            if (configureSecondary) listener.onHeroConfigure();
            else listener.onHeroBrowse();
        });
        b.primary.setNextFocusUpId(R.id.navHome);
        b.secondary.setNextFocusUpId(R.id.navVod);
        Glide.with(b.poster).clear(b.poster);
        b.poster.setImageDrawable(null);
        boolean hasImage = vod != null && !TextUtils.isEmpty(vod.getPic());
        b.poster.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        b.atmosphere.clear();
        b.atmosphere.setVisibility(TvTheme.isAtmosphereEnabled() ? View.VISIBLE : View.GONE);
        if (hasImage) {
            if (TvTheme.isAtmosphereEnabled()) b.atmosphere.setImage(item.sourceKey(), vod.getPic());
            Glide.with(b.poster).load(ImgUtil.getUrl(vod.getPic())).override(960, 540).fitCenter().into(b.poster);
        }
    }

    @Override
    public void onUnbindViewHolder(@NonNull ViewHolder viewHolder) {
        Holder holder = (Holder) viewHolder;
        holder.binding.atmosphere.clear();
        Glide.with(holder.binding.poster).clear(holder.binding.poster);
    }

    /** Prefer the source remark, then the type name, as the shimmering badge text. */
    private void bindBadge(AdapterHeroBinding b, Vod vod) {
        String text = "";
        if (vod != null) {
            if (!TextUtils.isEmpty(vod.getRemarks())) text = vod.getRemarks();
            else if (!TextUtils.isEmpty(vod.getTypeName())) text = vod.getTypeName();
            else text = vod.getYear();
        }
        b.badge.setText(text);
        b.badge.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        b.shimmer.setVisibility(b.badge.getVisibility());
    }

    /** Giant leading-character watermark anchoring the hero column. */
    private void bindWatermark(AdapterHeroBinding b, String name) {
        boolean has = !TextUtils.isEmpty(name);
        b.watermark.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) b.watermark.setText(name.substring(0, Character.charCount(name.codePointAt(0))));
    }

    private static final class Holder extends ViewHolder {

        final AdapterHeroBinding binding;

        Holder(AdapterHeroBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}