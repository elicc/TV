package com.fongmi.android.tv.ui.presenter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ViewEmptySourceBinding;
import com.fongmi.android.tv.databinding.ViewEmptySourceCardBinding;
import com.fongmi.android.tv.utils.TvTheme;

/**
 * Dedicated first-launch empty-state Presenter (Stitch fongmi_tv_home_empty_source).
 * Replaces the hero row when no source has been configured, exposing three
 * configuration entry cards (VOD / Live / Drive) and a setup hint footer.
 */
public final class EmptySourcePresenter extends Presenter {

    public enum Action {
        VOD, LIVE, DRIVE, RESTORE
    }

    public interface Listener {
        void onEmptyAction(Action action);
    }

    public record Item(boolean loading, boolean configFailed, boolean restorable) {
        public static Item fresh(boolean restorable) {
            return new Item(false, false, restorable);
        }

        public static Item asLoading() {
            return new Item(true, false, false);
        }

        public static Item asFailed(boolean restorable) {
            return new Item(false, true, restorable);
        }
    }

    private final Listener listener;

    public EmptySourcePresenter(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new Holder(ViewEmptySourceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, Object object) {
        Holder holder = (Holder) viewHolder;
        ViewEmptySourceBinding b = holder.binding;
        Item item = (Item) object;
        // Anchor the row height so the grid allocates enough room for the three
        // cards without scroll-cropping the auxiliary line.
        b.getRoot().getLayoutParams().height = com.fongmi.android.tv.utils.ResUtil.dp2px(428);

        boolean loading = item.loading();
        boolean failed = item.configFailed();
        // The restore link and the footnote are mutually exclusive: they occupy the same band
        // below the cards, and they say opposite things ("there may be something to recover"
        // vs "set it up by hand"). Keeping one visible also keeps the row inside 428dp.
        boolean restore = item.restorable() && !loading && !failed;
        b.title.setVisibility(loading || failed ? View.GONE : View.VISIBLE);
        b.subtitle.setVisibility(loading || failed ? View.GONE : View.VISIBLE);
        b.hint.setVisibility(loading || failed ? View.GONE : View.VISIBLE);
        b.cards.setVisibility(loading || failed ? View.GONE : View.VISIBLE);
        b.auxiliary.setVisibility(loading || failed || restore ? View.GONE : View.VISIBLE);
        b.vaultRestore.setVisibility(restore ? View.VISIBLE : View.GONE);
        b.badge.setText(failed ? R.string.tv_config_error_title : loading ? R.string.tv_loading_title : R.string.tv_empty_badge);

        bindCard(b.cardVod, R.drawable.ic_empty_film, Action.VOD, true, false);
        bindCard(b.cardLive, R.drawable.ic_empty_broadcast, Action.LIVE, false, true);
        bindCard(b.cardDrive, R.drawable.ic_empty_cloud, Action.DRIVE, false, true);
        bindRestore(b, restore);
    }

    /**
     * The cards pin DOWN to themselves so focus cannot wander out of the row, which would leave
     * the recovery link underneath unreachable. While it is offered the row has to hand focus
     * over; when it is not, the cards keep their self-pin.
     */
    private void bindRestore(ViewEmptySourceBinding b, boolean visible) {
        int downId = visible ? R.id.vaultRestore : R.id.cardVod;
        b.cardVod.getRoot().setNextFocusDownId(downId);
        b.cardLive.getRoot().setNextFocusDownId(downId);
        b.cardDrive.getRoot().setNextFocusDownId(downId);
        b.vaultRestore.setNextFocusUpId(R.id.cardVod);
        if (!visible) return;
        b.vaultRestore.setOnClickListener(v -> listener.onEmptyAction(Action.RESTORE));
        b.vaultRestore.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(180).start();
            else v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
        });
    }

    @Override
    public void onUnbindViewHolder(@NonNull ViewHolder viewHolder) {
    }

    private void bindCard(ViewEmptySourceCardBinding card, int iconRes, Action action, boolean showRecommended, boolean mutedIcon) {
        card.getRoot().setNextFocusDownId(card.getRoot().getId());
        card.icon.setImageResource(iconRes);
        card.iconBox.setBackgroundResource(mutedIcon ? R.drawable.tv_empty_icon_box_muted : R.drawable.tv_empty_icon_box);
        card.icon.setImageTintList(ColorStateList.valueOf(mutedIcon ? TvTheme.color(card.getRoot().getContext(), R.attr.tvColorAccent) : Color.BLACK));
        card.recommended.setVisibility(showRecommended ? View.VISIBLE : View.GONE);
        switch (action) {
            case VOD:
                card.title.setText(R.string.tv_empty_card_vod_title);
                card.tag.setText(R.string.tv_empty_card_vod_tag);
                card.tag.setVisibility(View.VISIBLE);
                card.subtitle.setText(R.string.tv_empty_card_vod_subtitle);
                card.qrRow.setVisibility(View.VISIBLE);
                card.qrUrl.setText(R.string.tv_empty_qr_url);
                card.description.setText(R.string.tv_empty_card_vod_desc);
                break;
            case LIVE:
                card.title.setText(R.string.tv_empty_card_live_title);
                card.tag.setVisibility(View.GONE);
                card.subtitle.setText(R.string.tv_empty_card_live_subtitle);
                card.qrRow.setVisibility(View.GONE);
                card.description.setText(R.string.tv_empty_card_live_desc);
                break;
            case DRIVE:
                card.title.setText(R.string.tv_empty_card_drive_title);
                card.tag.setVisibility(View.GONE);
                card.subtitle.setText(R.string.tv_empty_card_drive_subtitle);
                card.qrRow.setVisibility(View.GONE);
                card.description.setText(R.string.tv_empty_card_drive_desc);
                break;
        }
        card.getRoot().setOnClickListener(v -> listener.onEmptyAction(action));
        card.getRoot().setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(180).start();
            else v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
        });
    }

    private static final class Holder extends ViewHolder {

        final ViewEmptySourceBinding binding;

        Holder(ViewEmptySourceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
