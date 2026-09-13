package com.fongmi.android.tv.ui.motion;

import android.view.View;
import android.view.animation.AnimationUtils;

import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;

/**
 * First-screen entrance choreography (Stitch fadeInUpEntrance): views rise 22dp
 * with a fade, staggered 50ms/150ms/250ms/350ms. Call once per cold start; the
 * helpers are one-shot and no-op when system animation is disabled.
 */
public final class TvStagger {

    private static final int TRAVEL_DP = 22;

    private TvStagger() {
    }

    /** Animate the given children of {@code root} in declaration order. */
    public static void activity(View root, int... childIds) {
        if (!TvMotion.motionEnabled(root)) return;
        long delay = TvMotion.ENTRANCE_DELAY_FIRST;
        for (int childId : childIds) {
            View child = root.findViewById(childId);
            if (child == null) continue;
            rise(child, delay);
            delay += TvMotion.ENTRANCE_DELAY_STEP;
        }
    }

    /** Animate the first on-screen rows of a list once, after layout. */
    public static void firstScreen(RecyclerView recycler) {
        if (recycler.getTag(R.id.tv_stagger_done) instanceof Boolean) return;
        recycler.setTag(R.id.tv_stagger_done, Boolean.TRUE);
        if (!TvMotion.motionEnabled(recycler)) return;
        recycler.post(() -> {
            long delay = TvMotion.ENTRANCE_DELAY_FIRST;
            for (int i = 0; i < recycler.getChildCount(); i++) {
                rise(recycler.getChildAt(i), delay);
                delay += TvMotion.ENTRANCE_DELAY_STEP;
            }
        });
    }

    private static void rise(View view, long delay) {
        view.setAlpha(0f);
        view.setTranslationY(ResUtil.dp2px(TRAVEL_DP));
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(TvMotion.ENTRANCE)
                .setInterpolator(AnimationUtils.loadInterpolator(view.getContext(), R.interpolator.tv_interp_enter)).start();
    }
}
