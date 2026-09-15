package com.fongmi.android.tv.ui.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Cache;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentTypeBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.activity.VodActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.presenter.FilterPresenter;
import com.fongmi.android.tv.ui.presenter.HeaderPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TypeFragment extends BaseFragment implements CustomScroller.Callback, VodPresenter.OnClickListener, SwipeRefreshLayout.OnRefreshListener {

    private HashMap<String, String> mExtends;
    private FragmentTypeBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private ArrayObjectAdapter mLast;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private List<Filter> mFilters;
    private boolean headerVisible;
    private boolean filterVisible;
    /**
     * Exactly the items this fragment prepended for the filter panel, in order.
     *
     * <p>This is a ledger, not a convenience: the panel's rows are removed by index, so the count
     * must be "what we actually inserted", never "what we think should be there". The old code
     * removed {@code mFilters.size()} rows unconditionally, which deleted real poster rows
     * whenever the panel and the adapter had drifted apart.
     */
    private final List<Object> mFilterItems = new ArrayList<>();

    public static TypeFragment newInstance(String key, String typeId, Style style, HashMap<String, String> extend, boolean folder) {
        Bundle args = new Bundle();
        args.putString("key", key);
        args.putString("typeId", typeId);
        args.putBoolean("folder", folder);
        args.putParcelable("style", style);
        args.putSerializable("extend", extend);
        TypeFragment fragment = new TypeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKey() {
        return getArguments().getString("key");
    }

    private String getTypeId() {
        return getArguments().getString("typeId");
    }

    private boolean isFolder() {
        return getArguments().getBoolean("folder");
    }

    /**
     * The VOD landing page is intentionally a square poster grid.  Providers
     * often omit a style (or return the legacy list style), so normalize the
     * visual presentation here without changing the API result or navigation
     * semantics.
     */
    private Style getDisplayStyle() {
        return new Style("rect", 1.0f);
    }

    private HashMap<String, String> getExtend() {
        return (HashMap<String, String>) getArguments().getSerializable("extend");
    }

    private List<Filter> getFilter() {
        return Cache.copy(getTypeId());
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private FolderFragment getParent() {
        return ((FolderFragment) getParentFragment());
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentTypeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mScroller = new CustomScroller(this);
        mExtends = getExtend();
        mFilters = getFilter();
        setRecyclerView();
        setViewModel();
        setFilters();
        // Asked, not remembered: the page may have been created with a panel already requested
        // (opening one switches category first, and this fragment's transaction is committed a
        // frame later), so the parent is read at the moment we can finally act on it.
        toggleFilter(getParentFilterVisible());
        getVideo();
        // Posted so it runs outside the transaction that is still creating this fragment — the
        // screen asks the page for its condition count, and instantiating the page from inside
        // that same transaction is not a re-entrancy the pager tolerates.
        mBinding.recycler.post(() -> {
            if (getActivity() instanceof VodActivity host) host.onFilterChanged();
        });
    }

    private boolean getParentFilterVisible() {
        FolderFragment parent = getParentFragment() instanceof FolderFragment ? (FolderFragment) getParentFragment() : null;
        return parent != null && parent.isFilterVisible();
    }

    @Override
    protected void initEvent() {
        mBinding.swipeLayout.setOnRefreshListener(this);
        mBinding.recycler.addOnScrollListener(mScroller);
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                syncBackdropFromSelection(child);
            }
        });
    }

    /**
     * Pushes the selected row's poster (when it carries a Vod payload) to the
     * activity-level backdrop. Filter rows have no poster and intentionally
     * leave the last backdrop in place; clearing it would flicker on every
     * filter-chip navigation.
     */
    private void syncBackdropFromSelection(@Nullable RecyclerView.ViewHolder child) {
        if (child == null) return;
        if (!(child instanceof ItemBridgeAdapter.ViewHolder)) return;
        Object item = ((ItemBridgeAdapter.ViewHolder) child).getItem();
        if (item instanceof Vod) {
            Vod v = (Vod) item;
            VodActivity host = (VodActivity) getActivity();
            if (host != null) host.updateBackdrop(v, getKey());
        }
    }

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        // Condition groups are labelled with the plain String items showFilter() prepends.
        selector.addPresenter(String.class, new HeaderPresenter());
        selector.addPresenter(Vod.class, new VodPresenter(this, Style.list()));
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(8, FocusHighlight.ZOOM_FACTOR_NONE, HorizontalGridView.FOCUS_SCROLL_ALIGNED), FilterPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setHeader(getActivity(), R.id.categoryBar, R.id.recycler);
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(getViewLifecycleOwner(), this::setAdapter);
        mViewModel.getAction().observe(getViewLifecycleOwner(), result -> Notify.show(result.getMsg()));
    }

    private void setFilters() {
        for (Filter filter : mFilters) {
            if (mExtends.containsKey(filter.getKey())) {
                filter.setSelected(mExtends.get(filter.getKey()));
            }
        }
    }

    private void setClick(ArrayObjectAdapter adapter, String key, Value item) {
        for (int i = 0; i < adapter.size(); i++) ((Value) adapter.get(i)).setSelected(item);
        adapter.notifyArrayItemRangeChanged(0, adapter.size());
        if (item.isSelected()) mExtends.put(key, item.getV());
        else mExtends.remove(key);
        onRefresh();
        // The screen's filter entry reports how many conditions are applied; only this fragment
        // knows that, so it has to say so.
        if (getActivity() instanceof VodActivity host) host.onFilterChanged();
    }

    private void getVideo() {
        mLast = null;
        checkFilter();
        mScroller.reset();
        getVideo(getTypeId(), "1");
    }

    private void getVideo(String typeId, String page) {
        mViewModel.categoryContent(getKey(), typeId, page, true, mExtends);
    }

    private void setAdapter(Result result) {
        boolean first = mScroller.first();
        boolean flag = mExtends.isEmpty();
        int size = result.getList().size();
        mBinding.progressLayout.showContent(first & flag, size);
        mBinding.swipeLayout.setRefreshing(false);
        mScroller.endLoading(result);
        if (size > 0) {
            addVideo(result);
            if (first) takeFocus();
        }
    }

    /**
     * Moves focus into the poster grid once the first page has landed — unless the viewer is
     * already navigating the category shelf, or has the condition panel open. Stealing focus
     * either way would move the ground under them.
     */
    private void takeFocus() {
        if (filterVisible) return;
        if (getActivity() instanceof VodActivity host && host.isShelfFocused()) return;
        mBinding.recycler.post(() -> {
            if (!isHidden() && !filterVisible) mBinding.recycler.requestFocus();
        });
    }

    private void addVideo(Result result) {
        addGrid(result.getList(), getDisplayStyle());
        checkMore();
    }

    private void checkMore() {
        if (mScroller.isDisable() || mAdapter.size() >= 5) return;
        mScroller.checkMore();
    }

    private boolean checkLastSize(List<Vod> items, Style style) {
        if (mLast == null || items.isEmpty()) return false;
        int size = Product.getColumn(style) - mLast.size();
        if (size == 0) return false;
        size = Math.min(size, items.size());
        mLast.addAll(mLast.size(), items.subList(0, size));
        addGrid(items.subList(size, items.size()), style);
        return true;
    }

    private void addGrid(List<Vod> items, Style style) {
        if (checkLastSize(items, style)) return;
        List<ListRow> rows = new ArrayList<>();
        VodPresenter presenter = new VodPresenter(this, style);
        for (List<Vod> part : Lists.partition(items, Product.getColumn(style))) {
            mLast = new ArrayObjectAdapter(presenter);
            mLast.addAll(0, part);
            rows.add(new ListRow(mLast));
        }
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private ListRow getRow(Filter filter) {
        FilterPresenter presenter = new FilterPresenter(filter.getKey());
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenter);
        presenter.setOnClickListener((key, item) -> setClick(adapter, key, item));
        adapter.setItems(filter.getValue(), null);
        return new ListRow(adapter);
    }

    private void showFilter() {
        if (!mFilterItems.isEmpty()) return;
        List<Object> items = new ArrayList<>();
        for (Filter filter : mFilters) {
            // Name each group. Without it the panel is a stack of anonymous chip rows and the
            // viewer cannot tell which one is the genre and which the region; the source supplies
            // the wording, falling back to its own key when it does not.
            items.add(filter.getName().isEmpty() ? filter.getKey() : filter.getName());
            items.add(getRow(filter));
        }
        mFilterItems.addAll(items);
        mBinding.recycler.postDelayed(() -> mBinding.recycler.scrollToPosition(0), 48);
        mAdapter.addAll(0, items);
    }

    private void hideFilter() {
        // Nothing of ours is in the adapter, so nothing of ours may be taken out of it. Without
        // this guard the removal below lands on whatever rows happen to sit at the top — the
        // poster rows — and the grid silently loses content it can never get back.
        if (mFilterItems.isEmpty()) return;
        int count = mFilterItems.size();
        mFilterItems.clear();
        mAdapter.removeItems(0, count);
    }

    /**
     * Shows or hides this category's condition panel.
     *
     * <p>The fragment owns the panel, so it also states the outcome: the caller mirrors the
     * returned value instead of flipping its own flag first and hoping the two agree. A category
     * with no conditions can never report {@code true}.
     *
     * @return the panel's state after the call
     */
    public boolean toggleFilter(boolean visible) {
        if (mAdapter == null || mFilters == null) return false;
        filterVisible = visible && !mFilters.isEmpty();
        if (filterVisible) showFilter();
        else hideFilter();
        return filterVisible;
    }

    public boolean isFilterVisible() {
        return filterVisible;
    }

    /** How many conditions are currently applied to the request, seeded ones included. */
    public int getActiveFilterCount() {
        return mExtends == null ? 0 : mExtends.size();
    }

    private void checkFilter() {
        // Keep exactly what the ledger says we put at the top — never `mFilters.size()`, which
        // describes the model rather than the adapter and so can disagree with it.
        int keep = mFilterItems.size();
        int size = mAdapter.size();
        if (size > keep) mAdapter.removeItems(keep, size - keep);
        if (size - keep == 0) mBinding.progressLayout.showProgress();
        else mBinding.swipeLayout.setRefreshing(true);
    }

    public void onRefresh() {
        getVideo();
    }

    public boolean moveToTop() {
        return mBinding != null && mBinding.recycler.moveToTop();
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) {
            mViewModel.action(getKey(), item.getAction());
        } else if (item.isFolder()) {
            getParent().openFolder(item.getId(), mExtends);
            headerVisible = mBinding.recycler.isHeaderVisible();
        } else {
            if (getSite().isIndex()) CollectActivity.start(requireActivity(), item.getName());
            else VideoActivity.start(requireActivity(), getKey(), item.getId(), item.getName(), item.getPic(), isFolder() ? item.getName() : null);
        }
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction() || item.isFolder()) return false;
        CollectActivity.start(requireActivity(), item.getName());
        return true;
    }

    /**
     * Receives focus events from {@link VodPresenter} holders inside this
     * fragment. Bubbles them up to the parent {@link VodActivity} so the
     * activity-level atmosphere tracks D-pad navigation between cards in a
     * row — the outer recycler listener only fires when focus moves
     * between rows, not between cards inside the same row.
     */
    @Override
    public void onItemFocus(Vod item) {
        if (item == null) return;
        VodActivity host = (VodActivity) getActivity();
        if (host != null) host.updateBackdrop(item, getKey());
    }

    @Override
    public boolean onLoadMore(String page) {
        getVideo(getTypeId(), page);
        return true;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            mBinding.recycler.showHeader();
        } else {
            if (headerVisible) mBinding.recycler.showHeader();
            else mBinding.recycler.hideHeader();
            mBinding.recycler.requestFocus();
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        moveToTop();
    }
}
