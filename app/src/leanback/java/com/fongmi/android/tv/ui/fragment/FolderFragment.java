package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Cache;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.databinding.FragmentFolderBinding;
import com.fongmi.android.tv.ui.activity.VodActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;

import java.util.HashMap;
import java.util.Optional;

public class FolderFragment extends BaseFragment {

    private FragmentFolderBinding mBinding;
    private Class mType;
    /**
     * The panel state this category wants. Held here rather than read back off {@link Class},
     * because the child fragment — which actually owns the panel — is created a frame later than
     * the request to open one can arrive.
     */
    private boolean mFilterVisible;

    public static FolderFragment newInstance(String key, Class type) {
        Bundle args = new Bundle();
        args.putString("key", key);
        args.putParcelable("type", type);
        FolderFragment fragment = new FolderFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKey() {
        return getArguments().getString("key");
    }

    public Class getType() {
        return getArguments().getParcelable("type");
    }

    /**
     * The condition panel's owner, or null while this page is not attached yet.
     *
     * <p>A page handed back by the pager adapter exists before its transaction commits, and such a
     * fragment has no parent manager to ask for children — touching it throws. Callers here all
     * treat "no child yet" as "nothing to do", which is also the truth: it has no panel, and
     * {@link #mFilterVisible} is what it will be built with when it does arrive.
     */
    private TypeFragment getChild() {
        if (!isAdded()) return null;
        return (TypeFragment) getChildFragmentManager().findFragmentById(R.id.container);
    }

    private VodActivity getParent() {
        return (VodActivity) getActivity();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentFolderBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mType = getType();
        getChildFragmentManager().beginTransaction().replace(R.id.container, TypeFragment.newInstance(getKey(), mType.getTypeId(), mType.getStyle(), getExtend(), mType.isFolder())).commit();
    }

    private HashMap<String, String> getExtend() {
        HashMap<String, String> extend = new HashMap<>();
        for (Filter filter : Cache.get(mType)) if (filter.getInit() != null) extend.put(filter.getKey(), filter.getInit());
        return extend;
    }

    public void openFolder(String typeId, HashMap<String, String> extend) {
        TypeFragment next = TypeFragment.newInstance(getKey(), typeId, mType.getStyle(), extend, mType.isFolder());
        FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        Optional.ofNullable(getParent()).ifPresent(VodActivity::closeFilter);
        Optional.ofNullable(getChild()).ifPresent(ft::hide);
        ft.add(R.id.container, next);
        ft.addToBackStack(null);
        ft.commit();
    }

    /** Flips this category's condition panel. @return its state after the call. */
    public boolean toggleFilter() {
        return setFilterVisible(!mFilterVisible);
    }

    /**
     * Applies {@code visible} now when the child exists; otherwise records the wish, which the
     * child picks up when it is finally created. The child's answer wins, so the two can never
     * disagree about whether the panel is on screen.
     *
     * @return the panel's state after the call
     */
    public boolean setFilterVisible(boolean visible) {
        mFilterVisible = visible;
        TypeFragment child = getChild();
        if (child != null) mFilterVisible = child.toggleFilter(visible);
        return mFilterVisible;
    }

    public boolean isFilterVisible() {
        return mFilterVisible;
    }

    public int getActiveFilterCount() {
        return Optional.ofNullable(getChild()).map(TypeFragment::getActiveFilterCount).orElse(0);
    }

    public void onRefresh() {
        Optional.ofNullable(getChild()).ifPresent(TypeFragment::onRefresh);
    }

    public boolean moveToTop() {
        return Optional.ofNullable(getChild()).map(TypeFragment::moveToTop).orElse(false);
    }

    public boolean canBack() {
        return isAdded() && getChildFragmentManager().getBackStackEntryCount() > 0;
    }

    public void goBack() {
        if (isAdded()) getChildFragmentManager().popBackStack();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (mBinding != null && !isVisibleToUser) Optional.ofNullable(getChild()).ifPresent(f -> f.setUserVisibleHint(false));
    }
}
