package com.fongmi.android.tv.ui.activity;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.metadata.MetadataRepository;
import com.fongmi.android.tv.metadata.DoubanCollection;
import com.fongmi.android.tv.metadata.MovieMetadata;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.DoubanMovieAdapter;
import com.fongmi.android.tv.ui.adapter.RecordAdapter;
import com.fongmi.android.tv.ui.adapter.WordAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomKeyboard;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.motion.TvMotion;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.ZhuToPin;
import com.github.catvod.net.OkHttp;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.recyclerview.widget.GridLayoutManager;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Response;

public class SearchActivity extends BaseActivity implements WordAdapter.OnClickListener, DoubanMovieAdapter.OnClickListener, RecordAdapter.OnClickListener, CustomKeyboard.Callback, SiteListener {

    private ActivitySearchBinding mBinding;
    private RecordAdapter mRecordAdapter;
    private WordAdapter mWordAdapter;
    private DoubanMovieAdapter mDoubanMovieAdapter;
    private CustomKeyboard mKeyboard;
    private ObjectAnimator mSourcePulse;
    private AlertDialog historyDialog;
    private int hotRequestToken;
    private DoubanCollection collection = DoubanCollection.HOT_MOVIES;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SearchActivity.class));
    }

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    private String getKeyword() {
        String keyword = getIntent().getStringExtra("keyword");
        return keyword != null ? keyword : "";
    }

    private boolean empty() {
        return mBinding.keyword.getText().toString().trim().isEmpty();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected boolean customWall() {
        return false;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mKeyboard = CustomKeyboard.init(this, mBinding);
        setSourceChip(VodConfig.get().getHome(), getColor(R.color.tv_success));
        startSourcePulse();
        setRecyclerView();
        bindCollectionTab(mBinding.tabHotMovies, DoubanCollection.HOT_MOVIES);
        bindCollectionTab(mBinding.tabHotTv, DoubanCollection.HOT_TV);
        bindCollectionTab(mBinding.tabShowing, DoubanCollection.SHOWING);
        mBinding.doubanStatus.setOnClickListener(view -> getHot());
        checkKeyword();
        onSearch();
    }

    @Override
    protected void initEvent() {
        mBinding.sourceRow.setOnClickListener(v -> showSourceDialog());
        mBinding.manageRecords.setOnClickListener(this::manageRecords);
        mBinding.clearRecords.setOnClickListener(this::clearRecords);
        mBinding.recordRecycler.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft) mBinding.recordRecycler.post(this::updateRecordLayout);
        });
        mBinding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onSearch();
            return true;
        });
        mBinding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                getWord(s.toString());
            }
        });
        mBinding.mic.setOnClickListener(v -> mBinding.mic.start());
        mBinding.mic.setListener(this, new CustomTextListener() {
            @Override
            public void onResults(String result) {
                if (!result.isEmpty()) setKeyword(result);
                mBinding.keyword.requestFocus();
            }
        });
    }

    private void setRecyclerView() {
        mBinding.wordRecycler.setItemAnimator(null);
        mBinding.wordRecycler.setHasFixedSize(false);
        mBinding.wordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        mBinding.wordRecycler.setAdapter(mWordAdapter = new WordAdapter(this));
        mBinding.hotRecycler.setHasFixedSize(false);
        mBinding.hotRecycler.setLayoutManager(new GridLayoutManager(this, 5));
        mBinding.hotRecycler.setAdapter(mDoubanMovieAdapter = new DoubanMovieAdapter(this, false));
        mBinding.recordRecycler.setItemAnimator(null);
        mBinding.recordRecycler.setHasFixedSize(false);
        mBinding.recordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        mBinding.recordRecycler.setAdapter(mRecordAdapter = new RecordAdapter(this));
        mBinding.recordRecycler.post(this::updateRecordLayout);
    }

    private void checkKeyword() {
        setKeyword(getKeyword());
        getWord(getKeyword());
    }

    private void setKeyword(String text) {
        mBinding.keyword.setText(text);
        mBinding.keyword.setSelection(text.length());
    }

    private void getWord(String text) {
        if (text.isEmpty()) getHot();
        else getSuggest(text);
    }

    private void bindCollectionTab(View tab, DoubanCollection category) {
        tab.setOnClickListener(view -> selectCollection(category));
        tab.setOnFocusChangeListener((view, focused) -> {
            if (focused) selectCollection(category);
        });
    }

    private void selectCollection(DoubanCollection category) {
        if (category == collection || !empty()) return;
        collection = category;
        getHot();
    }

    private View selectedCollectionTab() {
        return collection == DoubanCollection.HOT_TV ? mBinding.tabHotTv
                : collection == DoubanCollection.SHOWING ? mBinding.tabShowing : mBinding.tabHotMovies;
    }

    private boolean currentCollectionRequest(int token) {
        return token == hotRequestToken && empty() && !isFinishing() && !isDestroyed();
    }

    private void getHot() {
        mBinding.word.setText(R.string.tv_douban_charts);
        mBinding.doubanTabs.setVisibility(View.VISIBLE);
        mBinding.tabHotMovies.setSelected(collection == DoubanCollection.HOT_MOVIES);
        mBinding.tabHotTv.setSelected(collection == DoubanCollection.HOT_TV);
        mBinding.tabShowing.setSelected(collection == DoubanCollection.SHOWING);
        if (mBinding.doubanStatus.hasFocus()) selectedCollectionTab().requestFocus();
        mBinding.doubanStatus.setVisibility(View.GONE);
        mBinding.hotSkeleton.getRoot().setVisibility(View.VISIBLE);
        mBinding.hotRecycler.setVisibility(View.GONE);
        mBinding.wordRecycler.setVisibility(View.GONE);
        mDoubanMovieAdapter.clear();
        int token = ++hotRequestToken;
        MetadataRepository.get().loadCollection(collection, items -> {
            if (!currentCollectionRequest(token)) return;
            mBinding.hotSkeleton.getRoot().setVisibility(View.GONE);
            mDoubanMovieAdapter.setItems(items);
            mBinding.hotRecycler.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
            if (items.isEmpty()) showCollectionStatus(false);
        }, error -> {
            if (currentCollectionRequest(token)) showCollectionStatus(true);
        });
    }

    private void showCollectionStatus(boolean failed) {
        mBinding.hotSkeleton.getRoot().setVisibility(View.GONE);
        mBinding.hotRecycler.setVisibility(View.GONE);
        mBinding.doubanStatus.setText(failed ? R.string.tv_douban_error : R.string.tv_douban_empty);
        mBinding.doubanStatus.setFocusable(failed);
        mBinding.doubanStatus.setClickable(failed);
        mBinding.doubanStatus.setVisibility(View.VISIBLE);
    }

    private void getSuggest(String text) {
        ++hotRequestToken;
        mBinding.doubanTabs.setVisibility(View.GONE);
        mBinding.doubanStatus.setVisibility(View.GONE);
        mBinding.word.setText(R.string.search_suggest);
        mBinding.hotSkeleton.getRoot().setVisibility(View.GONE);
        mBinding.hotRecycler.setVisibility(View.GONE);
        mBinding.wordRecycler.setVisibility(View.VISIBLE);
        OkHttp.newCall("https://suggest.video.iqiyi.com/?if=mobile&key=" + Uri.encode(ZhuToPin.get(text))).enqueue(getCallback());
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String result = response.body().string();
                if (TextUtils.isEmpty(result)) return;
                App.post(() -> setAdapter(result));
            }
        };
    }

    private void setAdapter(String result) {
        if (empty()) return;
        mWordAdapter.setItems(Word.objectFrom(result).getData());
    }

    @Override
    public void onItemClick(String text) {
        setKeyword(text);
        onSearch();
    }

    @Override
    public void onItemClick(MovieMetadata item) {
        if (item == null || item.getTitle().isEmpty()) return;
        onItemClick(item.getTitle());
    }

    @Override
    public void onDataChanged(int size) {
        boolean restoreFocus = size == 0 && (mBinding.recordLayout.hasFocus() || mBinding.recordHeader.hasFocus()
                || mRecordAdapter != null && mRecordAdapter.isEditing());
        if (size == 0 && mRecordAdapter != null) setRecordEditing(false);
        mBinding.recordHeader.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        mBinding.recordLayout.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        if (mRecordAdapter != null) updateRecordLayout();
        if (restoreFocus) mKeyboard.focusDefault();
    }

    private void updateRecordLayout() {
        mRecordAdapter.updateCollapsedCount(mBinding.recordRecycler);
    }

    @Override
    public void onToggleExpanded() {
        int firstHidden = mRecordAdapter.getTogglePosition();
        if (firstHidden == RecyclerView.NO_POSITION) return;
        boolean expand = !mRecordAdapter.isExpanded();
        mRecordAdapter.setExpanded(expand);
        updateRecordLayout();
        mBinding.recordRecycler.post(() -> {
            if (isFinishing() || isDestroyed() || mRecordAdapter.isEditing()
                    || mRecordAdapter.isExpanded() != expand) return;
            int toggle = mRecordAdapter.getTogglePosition();
            if (toggle == RecyclerView.NO_POSITION) return;
            int target = expand ? Math.min(firstHidden, toggle - 1) : toggle;
            RecyclerView.ViewHolder holder = mBinding.recordRecycler.findViewHolderForAdapterPosition(target);
            if (holder != null) holder.itemView.requestFocus();
        });
    }

    private void manageRecords(View view) {
        if (mRecordAdapter.getItemCount() == 0) return;
        if (exitRecordManagement()) return;
        setRecordEditing(true);
        focusRecord(0);
    }

    private void setRecordEditing(boolean editing) {
        mRecordAdapter.setEditing(editing);
        mRecordAdapter.setExpanded(false);
        updateRecordLayout();
        mBinding.recordHint.setText(editing ? R.string.tv_search_edit_hint : R.string.tv_search_manage_hint);
        mBinding.manageRecords.setContentDescription(getString(editing ? R.string.tv_search_done : R.string.tv_search_manage));
    }

    private boolean exitRecordManagement() {
        if (mRecordAdapter == null || !mRecordAdapter.isEditing()) return false;
        setRecordEditing(false);
        mBinding.manageRecords.requestFocus();
        return true;
    }

    @Override
    public void onItemDelete(String text) {
        if (!mRecordAdapter.isEditing()) return;
        int position = mRecordAdapter.remove(text);
        if (position >= 0 && mRecordAdapter.getItemCount() > 0) focusRecord(position);
    }

    private void focusRecord(int position) {
        mBinding.recordRecycler.post(() -> {
            if (isFinishing() || isDestroyed() || !mRecordAdapter.isEditing()) return;
            int target = Math.min(position, mRecordAdapter.getItemCount() - 1);
            if (target < 0) return;
            // Do not scroll the inner wrap-content list: doing so hides earlier
            // rows after deleting an item. Parent scrolling follows requestFocus.
            mBinding.recordRecycler.post(() -> {
                if (!mRecordAdapter.isEditing()) return;
                RecyclerView.ViewHolder holder = mBinding.recordRecycler.findViewHolderForAdapterPosition(target);
                if (holder != null) holder.itemView.requestFocus();
            });
        });
    }

    @Override
    public void onItemLongClick(String text) {
        confirmRemoveRecord(text, getCurrentFocus());
    }

    private void confirmRemoveRecord(String text, View returnFocus) {
        historyDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tv_search_delete)
                .setMessage(getString(R.string.tv_search_delete_message, text))
                .setPositiveButton(R.string.tv_search_delete, (dialog, which) -> mRecordAdapter.remove(text))
                .setNegativeButton(R.string.dialog_negative, null)
                .setOnDismissListener(dialog -> restoreHistoryFocus(returnFocus))
                .show();
    }

    private void clearRecords(View view) {
        historyDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tv_search_clear)
                .setMessage(R.string.tv_search_clear_message)
                .setPositiveButton(R.string.tv_search_clear, (dialog, which) -> mRecordAdapter.clear())
                .setNegativeButton(R.string.dialog_negative, null)
                .setOnDismissListener(dialog -> restoreHistoryFocus(view))
                .show();
    }

    private void restoreHistoryFocus(View previous) {
        mBinding.recordRecycler.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (previous != null && previous.isShown() && previous.requestFocus()) return;
            if (mRecordAdapter.getItemCount() > 0 && focusFirst(mBinding.recordRecycler)) return;
            mBinding.keyword.requestFocus();
        });
    }

    @Override
    public void onSearch() {
        if (empty()) return;
        String keyword = mBinding.keyword.getText().toString().trim();
        App.post(() -> mRecordAdapter.add(keyword), 250);
        Util.hideKeyboard(mBinding.keyword);
        CollectActivity.start(this, keyword);
    }

    private void showSourceDialog() {
        SiteDialog.create().show(this);
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
        if (item == null || item.isEmpty()) return;
        VodConfig.get().setHome(item);
        setSourceChip(item, getColor(R.color.tv_success));
    }

    @Override
    public void onRemote() {
        PushActivity.start(this, 1);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) showSourceDialog();
        if (KeyUtil.isActionDown(event) && KeyUtil.isBackKey(event) && exitRecordManagement()) return true;
        if (KeyUtil.isActionDown(event) && KeyUtil.isBackKey(event) && mKeyboard != null && mKeyboard.onBack()) return true;
        if (KeyUtil.isActionDown(event) && findFocus(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onBackInvoked() {
        if (exitRecordManagement()) return;
        if (mKeyboard != null && mKeyboard.onBack()) return;
        super.onBackInvoked();
    }

    private boolean findFocus(KeyEvent event) {
        View current = getCurrentFocus();
        if (current == null) return false;
        if (current == mBinding.tabHotMovies || current == mBinding.tabHotTv || current == mBinding.tabShowing) {
            if (KeyUtil.isDownKey(event)) {
                if (mBinding.hotRecycler.getVisibility() == View.VISIBLE) focusFirst(mBinding.hotRecycler);
                else if (mBinding.doubanStatus.isFocusable()) mBinding.doubanStatus.requestFocus();
                return true;
            }
            if (KeyUtil.isRightKey(event) && current == mBinding.tabShowing) return true;
        }
        if (current == mBinding.doubanStatus && KeyUtil.isUpKey(event)) return selectedCollectionTab().requestFocus();
        if (current == mBinding.keyword) return handleKeywordKey(event);
        if (current == mBinding.manageRecords || current == mBinding.clearRecords) {
            if (KeyUtil.isDownKey(event)) return focusFirst(mBinding.recordRecycler);
            if (KeyUtil.isUpKey(event)) return true;
        }
        View inKeyboard = mBinding.keyboard.findContainingItemView(current);
        View inWord = mBinding.wordRecycler.findContainingItemView(current);
        View inHot = mBinding.hotRecycler.findContainingItemView(current);
        View inRecord = mBinding.recordRecycler.findContainingItemView(current);
        if (inKeyboard != null) return handleKeyboardKey(event, inKeyboard);
        if (inRecord != null) return handleRecordKey(event, inRecord);
        if (inHot != null) return handleHotKey(event, inHot);
        if (inWord != null) return handleWordKey(event, inWord);
        return false;
    }

    private View findNearestInLastRow(RecyclerView rv, int targetLeft) {
        if (rv.getChildCount() == 0) return null;
        int lastTop = rv.getChildAt(rv.getChildCount() - 1).getTop();
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == lastTop) {
                int dist = Math.abs(child.getLeft() - targetLeft);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = child;
                }
            }
        }
        return nearest;
    }

    private boolean isFirstRow(RecyclerView rv, View item) {
        View first = rv.getChildAt(0);
        return first != null && item.getTop() == first.getTop();
    }

    private boolean isLastRow(RecyclerView rv, View item) {
        View last = rv.getChildAt(rv.getChildCount() - 1);
        return last != null && item.getTop() == last.getTop();
    }

    private boolean isFirstInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int left = focused.getLeft();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == top && child.getLeft() < left) return false;
        }
        return true;
    }

    private boolean isLastInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int right = focused.getRight();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == top && child.getRight() > right) return false;
        }
        return true;
    }

    private boolean handleKeywordKey(KeyEvent event) {
        if (!KeyUtil.isRightKey(event)) return false;
        if (mBinding.keyword.getSelectionEnd() < mBinding.keyword.getText().length()) return false;
        if (empty() && mBinding.recordLayout.getVisibility() != View.VISIBLE) return selectedCollectionTab().requestFocus();
        boolean hasSuggestion = mBinding.wordRecycler.getVisibility() == View.VISIBLE && mWordAdapter.getItemCount() > 0;
        boolean hasRecord = mBinding.recordLayout.getVisibility() == View.VISIBLE;
        RecyclerView target = hasSuggestion ? mBinding.wordRecycler : hasRecord ? mBinding.recordRecycler
                : mBinding.hotRecycler.getVisibility() == View.VISIBLE ? mBinding.hotRecycler : mBinding.wordRecycler;
        return focusFirst(target);
    }

    private boolean handleKeyboardKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.keyboard, item)) {
            mBinding.keyword.requestFocus();
            return true;
        }
        if (KeyUtil.isLeftKey(event) && isFirstInRow(mBinding.keyboard, item)) return true;
        return KeyUtil.isDownKey(event) && isLastRow(mBinding.keyboard, item);
    }

    private boolean handleWordKey(KeyEvent event, View item) {
        if (KeyUtil.isRightKey(event)) return isLastInRow(mBinding.wordRecycler, item);
        if (KeyUtil.isDownKey(event)) return isLastRow(mBinding.wordRecycler, item);
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.wordRecycler, item)) {
            if (mBinding.recordLayout.getVisibility() == View.VISIBLE) {
                View child = findNearestInLastRow(mBinding.recordRecycler, item.getLeft());
                if (child != null) {
                    mBinding.scroll.smoothScrollTo(0, 0);
                    child.requestFocus();
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    private boolean handleRecordKey(KeyEvent event, View item) {
        if (KeyUtil.isRightKey(event)) return isLastInRow(mBinding.recordRecycler, item);
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.recordRecycler, item)) {
            mBinding.manageRecords.requestFocus();
            return true;
        }
        if (KeyUtil.isDownKey(event) && isLastRow(mBinding.recordRecycler, item)) {
            return focusDiscovery();
        }
        if (KeyUtil.isUpKey(event) || KeyUtil.isDownKey(event)) {
            boolean down = KeyUtil.isDownKey(event);
            View nearest = null;
            for (int i = 0; i < mBinding.recordRecycler.getChildCount(); i++) {
                View child = mBinding.recordRecycler.getChildAt(i);
                if (down ? child.getTop() <= item.getTop() : child.getTop() >= item.getTop()) continue;
                int rowDistance = Math.abs(child.getTop() - item.getTop());
                int leftDistance = Math.abs(child.getLeft() - item.getLeft());
                if (nearest == null || rowDistance < Math.abs(nearest.getTop() - item.getTop())
                        || rowDistance == Math.abs(nearest.getTop() - item.getTop())
                        && leftDistance < Math.abs(nearest.getLeft() - item.getLeft())) nearest = child;
            }
            return nearest != null && nearest.requestFocus();
        }
        return false;
    }

    private boolean handleHotKey(KeyEvent event, View item) {
        if (KeyUtil.isRightKey(event)) return isLastInRow(mBinding.hotRecycler, item);
        if (KeyUtil.isDownKey(event)) return isLastRow(mBinding.hotRecycler, item);
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.hotRecycler, item)) return selectedCollectionTab().requestFocus();
        return false;
    }

    private boolean focusDiscovery() {
        return empty() ? selectedCollectionTab().requestFocus() : focusFirst(mBinding.wordRecycler);
    }

    private boolean focusFirst(RecyclerView rv) {
        View child = rv.getChildAt(0);
        if (child == null) return false;
        child.requestFocus();
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mBinding.mic.setFocusable(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mRecordAdapter.refresh();
        mBinding.mic.setFocusable(true);
        setSourceChip(VodConfig.get().getHome(), getColor(R.color.tv_success));
        mKeyboard.focusDefault();
    }

    @Override
    protected void onDestroy() {
        ++hotRequestToken;
        if (historyDialog != null) historyDialog.dismiss();
        if (mSourcePulse != null) mSourcePulse.cancel();
        super.onDestroy();
        mBinding.mic.destroy();
    }
}
