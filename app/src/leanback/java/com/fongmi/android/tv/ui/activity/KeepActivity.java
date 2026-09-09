package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.core.view.OneShotPreDrawListener;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.ActivityKeepBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.adapter.KeepAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class KeepActivity extends BaseActivity implements KeepAdapter.OnClickListener {

    private ActivityKeepBinding mBinding;
    private KeepAdapter mAdapter;
    private AlertDialog deleteDialog;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, KeepActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityKeepBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setRecyclerView();
        mBinding.findFilms.setOnClickListener(v -> SearchActivity.start(this));
        mBinding.manage.setOnClickListener(v -> {
            setManaging(!mAdapter.isDelete());
            if (mAdapter.isDelete()) focusCard(0);
        });
        getKeep();
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setAdapter(mAdapter = new KeepAdapter(this));
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn()));
        mBinding.recycler.addItemDecoration(new SpaceItemDecoration(Product.getColumn(), 16));
    }

    private void getKeep() {
        mAdapter.setItems(Keep.getVod(), this::updateContent);
    }

    private void updateContent() {
        boolean empty = mAdapter.getItemCount() == 0;
        mBinding.progressLayout.showContent();
        mBinding.progressLayout.setVisibility(empty ? View.GONE : View.VISIBLE);
        mBinding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        mBinding.manage.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            setManaging(false);
            mBinding.findFilms.requestFocus();
        }
    }

    private void setManaging(boolean managing) {
        mAdapter.setDelete(managing);
        mBinding.manage.setText(managing ? R.string.tv_keep_done : R.string.tv_keep_manage);
    }

    private void focusCard(int position) {
        if (isFinishing() || isDestroyed()) return;
        if (mAdapter.getItemCount() == 0) {
            mBinding.findFilms.requestFocus();
            return;
        }
        int target = Math.max(0, Math.min(position, mAdapter.getItemCount() - 1));
        mBinding.recycler.scrollToPosition(target);
        OneShotPreDrawListener.add(mBinding.recycler, () -> {
            if (isFinishing() || isDestroyed()) return;
            View card = mBinding.recycler.getLayoutManager().findViewByPosition(target);
            if (card != null) card.requestFocus();
            else mBinding.manage.requestFocus();
        });
        mBinding.recycler.invalidate();
    }

    private void loadConfig(Config config, Keep item) {
        VodConfig.load(config, new Callback() {
            @Override
            public void success() {
                VideoActivity.start(getActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.KEEP) getKeep();
    }

    @Override
    public void onItemClick(Keep item) {
        Config config = Config.find(item.getCid());
        if (config == null) CollectActivity.start(this, item.getVodName());
        else if (item.getCid() != VodConfig.getCid()) loadConfig(config, item);
        else VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(Keep item) {
        if (deleteDialog != null && deleteDialog.isShowing()) return;
        int position = mAdapter.getItems().indexOf(item);
        if (position < 0) return;
        boolean[] confirmed = {false};
        deleteDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tv_keep_remove)
                .setMessage(getString(R.string.tv_keep_remove_message, item.getVodName()))
                .setPositiveButton(R.string.tv_keep_remove, (dialog, which) -> {
                    confirmed[0] = true;
                    mAdapter.remove(item.delete(), () -> {
                        updateContent();
                        focusCard(position);
                    });
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .setOnDismissListener(dialog -> {
                    deleteDialog = null;
                    if (!confirmed[0]) focusCard(position);
                })
                .show();
        View cancel = deleteDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        cancel.setFocusableInTouchMode(true);
        cancel.post(cancel::requestFocus);
    }

    @Override
    public boolean onLongClick() {
        setManaging(true);
        return true;
    }

    @Override
    protected void onBackInvoked() {
        if (mAdapter.isDelete()) setManaging(false);
        else super.onBackInvoked();
    }

    @Override
    protected void onDestroy() {
        if (deleteDialog != null) {
            deleteDialog.setOnDismissListener(null);
            deleteDialog.dismiss();
        }
        super.onDestroy();
    }
}
