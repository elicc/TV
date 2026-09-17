package com.fongmi.android.tv.ui.activity;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Cache;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVodBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.adapter.TypeAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.TvKeycapsBar;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.fragment.FolderFragment;
import com.fongmi.android.tv.ui.motion.TvMotion;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.TvTheme;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Optional;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VodActivity extends BaseActivity implements TypeAdapter.OnClickListener, SiteListener {

    private ActivityVodBinding mBinding;
    private TypeAdapter mAdapter;
    private SiteViewModel mViewModel;
    private ObjectAnimator mSourcePulse;
    private Site mPreviousSite;
    private View mOldView;
    private boolean mSwitchingSource;
    private final Handler mClockHandler = new Handler();

    public static void start(Activity activity, Result result) {
        start(activity, VodConfig.get().getHome().getKey(), result);
    }

    public static void start(Activity activity, String key, Result result) {
        if (result == null || result.getTypes().isEmpty()) return;
        Intent intent = new Intent(activity, VodActivity.class);
        intent.putExtra("key", key);
        intent.putExtra("result", result);
        activity.startActivity(intent);
    }

    private String getKey() {
        return getIntent().getStringExtra("key");
    }

    private Result getResult() {
        return getIntent().getParcelableExtra("result");
    }

    private Class getType() {
        return mAdapter.get(mBinding.pager.getCurrentItem());
    }

    /** The page the pager is actually on, or null when it has no pages yet. */
    private FolderFragment getFragment() {
        if (mBinding.pager.getAdapter() == null || mBinding.pager.getAdapter().getCount() == 0) return null;
        Object item = mBinding.pager.getAdapter().instantiateItem(mBinding.pager, mBinding.pager.getCurrentItem());
        return item instanceof FolderFragment ? (FolderFragment) item : null;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVodBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        // Follow the global film-atmosphere preference; the activity recreates on
        // change, so a one-shot visibility gate is sufficient.
        mBinding.atmosphere.setVisibility(TvTheme.isAtmosphereEnabled() ? View.VISIBLE : View.GONE);
        setSourceChip(VodConfig.get().getHome(), getColor(R.color.tv_success));
        startSourcePulse();
        updateClock();
        setViewModel();
        setRecyclerView();
        setTypes();
        setPager();
        setKeycaps();
        // Posted, not called: the first page's fragment does not exist until the pager has
        // populated, and it is the fragment that knows how many conditions are applied.
        mBinding.pager.post(this::updateFilterBadge);
    }

    private void setKeycaps() {
        TvKeycapsBar.Cap[] caps = {
                TvKeycapsBar.Cap.of(getString(R.string.tv_key_dpad), KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT),
                TvKeycapsBar.Cap.of(getString(R.string.tv_key_ok), KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER),
                TvKeycapsBar.Cap.of(getString(R.string.tv_key_menu), KeyEvent.KEYCODE_MENU),
                TvKeycapsBar.Cap.of(getString(R.string.tv_key_back), KeyEvent.KEYCODE_BACK),
        };
        String[] hints = {getString(R.string.tv_hint_dpad), getString(R.string.tv_hint_vod_ok), getString(R.string.tv_hint_filter), getString(R.string.tv_hint_back)};
        mBinding.keycaps.setCaps(caps, hints);
    }

    @Override
    protected TvKeycapsBar keycaps() {
        return mBinding.keycaps;
    }

    /**
     * The one visible way into a category's conditions.
     *
     * <p>Visibility is decided by the same question {@code TypeAdapter.getIcon} asks — does this
     * category have conditions at all — so the entry, the tab's marker and the panel can never
     * contradict one another. The count comes from the page itself, because only it knows which
     * conditions are actually being sent.
     */
    private void updateFilterBadge() {
        Class item = getType();
        boolean available = item != null && !Cache.get(item).isEmpty();
        if (!available && mBinding.vodFilter.hasFocus()) mBinding.recycler.requestFocus();
        mBinding.vodFilter.setVisibility(available ? View.VISIBLE : View.GONE);
        if (!available) return;
        FolderFragment page = getFragment();
        int count = page == null ? 0 : page.getActiveFilterCount();
        mBinding.vodFilter.setText(count == 0 ? getString(R.string.tv_vod_filter) : getString(R.string.tv_vod_filter_count, count));
    }

    /** Raised by the page whenever a condition is chosen, so the count cannot go stale. */
    public void onFilterChanged() {
        updateFilterBadge();
    }

    /**
     * Whether the viewer is currently moving along the category shelf.
     *
     * <p>A page finishes loading a beat after the category changed, and its first instinct is to
     * pull focus into the poster grid. Doing that mid-navigation drags the viewer off the shelf
     * they were traversing and leaves the filter entry — which sits at the end of it — out of
     * reach. The page asks before taking focus.
     */
    public boolean isShelfFocused() {
        return mBinding.recycler.hasFocus() || mBinding.vodFilter.hasFocus();
    }

    /**
     * Opens or closes the conditions panel.
     *
     * <p>Only opening is gated. Closing is always allowed, so a category whose conditions were
     * dropped underneath us (a source reload clears the cache) can never trap the viewer inside
     * a panel that has no way out.
     */
    private void toggleFilterPanel() {
        Class item = getType();
        if (item == null) return;
        if (!item.getFilter() && Cache.get(item).isEmpty()) return;
        updateFilter(item);
    }

    private void updateClock() {
        if (mBinding == null) return;
        mBinding.clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        mClockHandler.postDelayed(this::updateClock, 30_000);
    }

    @Override
    protected void initEvent() {
        mBinding.sourceRow.setOnClickListener(v -> {
            if (!mSwitchingSource) SiteDialog.create().show(this);
        });
        mBinding.vodFilter.setOnClickListener(v -> toggleFilterPanel());
        // Reloading a category used to be hidden on the UP key, which meant an ordinary press
        // silently discarded the viewer's scroll position. It lives here instead: on a control
        // that says what it is, next to the key hint that names it.
        mBinding.vodFilter.setOnLongClickListener(v -> {
            FolderFragment page = getFragment();
            if (page != null) page.onRefresh();
            return true;
        });
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mBinding.recycler.setSelectedPosition(position);
                mBinding.recycler.requestFocus();
                updateFilterBadge();
            }
        });
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child);
            }
        });
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, this::onSourceLoaded);
    }

    private void setSourceChip(Site site, int statusColor) {
        String title = site == null || site.getName().isEmpty()
                ? getString(R.string.tv_source_unconfigured)
                : site.getName();
        mBinding.sourceTitle.setText(title);
        mBinding.sourceTitle.setContentDescription(getString(R.string.tv_source) + "：" + title);
        mBinding.sourceStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(statusColor));
    }

    private void startSourcePulse() {
        if (!TvMotion.motionEnabled(this)) return;
        mSourcePulse = ObjectAnimator.ofFloat(mBinding.sourceStatus, "alpha", 1f, 0.45f);
        mSourcePulse.setDuration(1200);
        mSourcePulse.setRepeatCount(ValueAnimator.INFINITE);
        mSourcePulse.setRepeatMode(ValueAnimator.REVERSE);
        mSourcePulse.start();
    }

    @Override
    public void setSite(Site item) {
        if (item == null || item.isEmpty() || mSwitchingSource) return;
        Site current = VodConfig.get().getHome();
        if (item.equals(current)) {
            setSourceChip(current, getColor(R.color.tv_success));
            return;
        }
        mPreviousSite = current;
        mSwitchingSource = true;
        VodConfig.get().setHome(item);
        setSourceChip(item, TvTheme.color(this, R.attr.tvColorAccent));
        mViewModel.homeContent();
    }

    private void onSourceLoaded(Result result) {
        if (!mSwitchingSource || result == null) return;
        Site previous = mPreviousSite;
        mPreviousSite = null;
        mSwitchingSource = false;
        if (result.getTypes().isEmpty()) {
            if (previous != null && !previous.isEmpty()) VodConfig.get().setHome(previous);
            setSourceChip(VodConfig.get().getHome(), getColor(R.color.tv_success));
            Notify.show(R.string.tv_source_switch_failed);
            return;
        }
        Cache.clear().put(result);
        start(this, result);
        finish();
        overridePendingTransition(0, 0);
    }

    private void setRecyclerView() {
        mBinding.recycler.requestFocus();
        mBinding.recycler.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.recycler.setAdapter(mAdapter = new TypeAdapter(this));
    }

    private void setTypes() {
        mAdapter.addAll(getResult().getTypes());
    }

    private void setPager() {
        mBinding.pager.setAdapter(new PageAdapter(getSupportFragmentManager()));
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child) {
        if (mOldView != null) mOldView.setSelected(false);
        if ((mOldView = child != null ? child.itemView : null) == null) return;
        mOldView.setSelected(true);
        App.post(mRunnable, 100);
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mBinding.pager.setCurrentItem(mBinding.recycler.getSelectedPosition());
        }
    };

    private boolean isFilterVisible() {
        return Optional.ofNullable(getType()).map(Class::getFilter).orElse(false);
    }

    private void updateFilter() {
        Optional.ofNullable(getType()).ifPresent(this::updateFilter);
    }

    private void updateFilter(Class item) {
        if (item == null) return;
        FolderFragment page = getFragment();
        if (page == null) return;
        // The page owns the panel, so it reports the result and the class merely mirrors it. The
        // old order — flip the flag, then toggle whatever page happens to be current — let the
        // two drift apart, and every later "close the panel" then removed rows that were not the
        // panel's.
        item.setFilter(page.toggleFilter());
        int index = mAdapter.indexOf(item);
        if (index >= 0) mAdapter.notifyItemRangeChanged(index, 1);
        updateFilterBadge();
    }

    public void closeFilter() {
        if (isFilterVisible()) updateFilter();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() != RefreshEvent.Type.CATEGORY) return;
        FolderFragment page = getFragment();
        if (page != null) page.onRefresh();
        // A category reload can arrive with a different set of conditions, or none at all.
        updateFilterBadge();
    }

    @Override
    public void onItemClick(Class item) {
        // Settle the page switch before toggling anything. Focus reaches the strip ~100ms before
        // the pager follows it (see mRunnable), so acting straight away would open the panel on
        // the category the viewer just left while marking the one they picked as open.
        int position = mAdapter.indexOf(item);
        if (position >= 0 && mBinding.pager.getCurrentItem() != position) {
            App.removeCallbacks(mRunnable);
            mBinding.pager.setCurrentItem(position, false);
        }
        updateFilter(item);
    }

    /**
     * Pushes a focused Vod's poster to the activity-level backdrop so the
     * atmosphere view follows D-pad focus across the VOD grid. Invoked by
     * the child {@link com.fongmi.android.tv.ui.fragment.TypeFragment} and
     * by any VOD holder that surfaces focus events.
     */
    public void updateBackdrop(Vod vod, String sourceKey) {
        if (isFinishing() || isDestroyed() || mBinding == null) return;
        if (vod == null || TextUtils.isEmpty(vod.getPic())) {
            clearBackdrop();
            return;
        }
        mBinding.atmosphere.setImage(sourceKey, vod.getPic());
    }

    /** Releases the backdrop bitmap, e.g. when the VOD list becomes empty. */
    public void clearBackdrop() {
        if (isFinishing() || isDestroyed() || mBinding == null) return;
        mBinding.atmosphere.clear();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // The source chip sits above the horizontal shelf. Relying on geometric focus search
        // leaves it unreachable because the Leanback grid retains focus at its top edge.
        if (KeyUtil.isActionDown(event) && KeyUtil.isUpKey(event) && isShelfFocused()) {
            mBinding.sourceRow.requestFocus();
            return true;
        }
        if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event) && mBinding.sourceRow.hasFocus()) {
            if (isVisible(mBinding.vodFilter)) mBinding.vodFilter.requestFocus();
            else mBinding.recycler.requestFocus();
            return true;
        }
        if (KeyUtil.isMenuKey(event)) toggleFilterPanel();
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mSourcePulse != null) mSourcePulse.start();
        // The conditions cache is rebuilt by the home screen and cleared on every source load,
        // which can all happen while this screen is paused. Posted rather than called: reading
        // the badge reaches into the current page, and during the resume pass the pager may hand
        // back a page whose transaction has not committed yet.
        mBinding.pager.post(this::updateFilterBadge);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mSourcePulse != null) mSourcePulse.pause();
    }

    @Override
    protected void onBackInvoked() {
        FolderFragment page = getFragment();
        if (isFilterVisible()) updateFilter();
        else if (page == null) super.onBackInvoked();
        else if (page.moveToTop()) return;
        else if (page.canBack()) page.goBack();
        else super.onBackInvoked();
    }

    @Override
    protected void onDestroy() {
        mClockHandler.removeCallbacksAndMessages(null);
        if (mSourcePulse != null) mSourcePulse.cancel();
        super.onDestroy();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Class type = mAdapter.get(position);
            return FolderFragment.newInstance(getKey(), type);
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }
    }
}
