package com.fongmi.android.tv.ui.dialog;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.MediaMetadata;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.databinding.DialogDanmakuFinderBinding;
import com.fongmi.android.tv.impl.DanmakuListener;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.ui.adapter.DanmakuResultAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TV playback drawer that searches danmaku for the playing title. It searches on open and
 * builds the query from title candidates and an episode stepper, so typing is optional.
 */
public final class DanmakuFinderDialog extends BaseBottomSheetDialog implements DanmakuListener {

    private static final int MAX_KEYWORDS = 4;
    private static final long STEP_DELAY = 600;
    private static final Pattern BRACKET = Pattern.compile("[(（\\[【《「][^)）\\]】》」]*[)）\\]】》」]");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(19|20)\\d{2}(?!\\d)");
    private static final Pattern TRAILING_YEAR = Pattern.compile("[\\s·\\-_]+(19|20)\\d{2}$");
    private static final Pattern TAG = Pattern.compile("(?i)国语|國語|粤语|粵語|中字|未删减|未刪減|完整版|蓝光|藍光|超清|高清|4K|1080P|\\bHD\\b");
    private static final Pattern SEASON = Pattern.compile("(?i)第[0-9一二三四五六七八九十]+[季部]|\\bS(eason)?\\s*\\d{1,2}\\b");
    private static final Pattern EDGE = Pattern.compile("^[\\s·:：\\-_|]+|[\\s·:：\\-_|]+$");
    private static final Pattern NUMBER = Pattern.compile("\\d{1,5}");

    private final Runnable research = this::search;
    private final List<String> keywords = new ArrayList<>();
    private DialogDanmakuFinderBinding binding;
    private DanmakuResultAdapter adapter;
    private PlayerManager player;
    private String keyword = "";
    private String episode = "";
    private boolean anyEpisode;
    private boolean custom;
    private boolean focusResults;

    public static DanmakuFinderDialog create() {
        return new DanmakuFinderDialog();
    }

    public DanmakuFinderDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof DanmakuFinderDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDanmakuFinderBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        if (player == null) return;
        MediaMetadata metadata = player.getMetadata();
        String title = metadata == null || TextUtils.isEmpty(metadata.title) ? "" : metadata.title.toString().trim();
        episode = metadata == null || TextUtils.isEmpty(metadata.artist) ? "" : metadata.artist.toString().trim();
        anyEpisode = episode.isEmpty();
        adapter = new DanmakuResultAdapter(this::onItemClick);
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        binding.recycler.addItemDecoration(SpaceItemDecoration.of(binding.recycler, 16));
        keywords.addAll(keywords(title));
        for (String text : keywords) addKeyword(text);
        keyword = keywords.isEmpty() ? "" : keywords.get(0);
        if (!keywords.isEmpty()) binding.setting.setNextFocusDownId(binding.keywords.getChildAt(0).getId());
        updateKeywords();
        updateEpisode();
        focusResults = true;
        search();
        binding.getRoot().post(this::focusDefault);
    }

    @Override
    protected void initEvent() {
        binding.setting.setOnClickListener(this::onSetting);
        binding.configure.setOnClickListener(this::onSetting);
        binding.retry.setOnClickListener(view -> retry());
        binding.prev.setOnClickListener(view -> onStep(-1));
        binding.next.setOnClickListener(view -> onStep(1));
        binding.episode.setOnClickListener(view -> setAnyEpisode(false));
        binding.anyEpisode.setOnClickListener(view -> setAnyEpisode(true));
        binding.keyword.setOnFocusChangeListener(this::onKeywordFocus);
        binding.keyword.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId != EditorInfo.IME_ACTION_SEARCH && actionId != EditorInfo.IME_ACTION_DONE && !enter) return false;
            submit();
            return true;
        });
        // With the keyboard hidden, LEFT/RIGHT would walk the cursor; keep them for focus.
        binding.keyword.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int direction = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? View.FOCUS_RIGHT : 0;
            if (direction == 0) return false;
            View next = view.focusSearch(direction);
            return next == null || next.requestFocus();
        });
    }

    /** Title variants a search API is likely to match: as given, without tags, without season, with year. */
    static List<String> keywords(String title) {
        Set<String> items = new LinkedHashSet<>();
        String text = title.trim();
        if (text.isEmpty()) return new ArrayList<>();
        items.add(text);
        Matcher year = YEAR.matcher(text);
        String found = year.find() ? year.group() : "";
        String base = clean(TRAILING_YEAR.matcher(TAG.matcher(BRACKET.matcher(text).replaceAll(" ")).replaceAll(" ")).replaceAll(""));
        String core = clean(SEASON.matcher(base).replaceAll(" "));
        items.add(base);
        items.add(core);
        if (!found.isEmpty() && !core.isEmpty() && !core.contains(found)) items.add(core + " " + found);
        items.remove("");
        List<String> result = new ArrayList<>(items);
        return result.subList(0, Math.min(MAX_KEYWORDS, result.size()));
    }

    private static String clean(String text) {
        return EDGE.matcher(text.replaceAll("\\s+", " ")).replaceAll("").trim();
    }

    static int number(String episode) {
        Matcher matcher = NUMBER.matcher(episode);
        return matcher.find() ? Integer.parseInt(matcher.group()) : 0;
    }

    /** Moves the first number of the episode name, keeping its zero padding and wording. */
    static String step(String episode, int delta) {
        Matcher matcher = NUMBER.matcher(episode);
        if (!matcher.find()) return delta > 0 ? "第1集" : episode;
        String group = matcher.group();
        int value = Math.max(1, Integer.parseInt(group) + delta);
        String digits = String.format(Locale.ROOT, "%0" + (group.startsWith("0") ? group.length() : 1) + "d", value);
        return episode.substring(0, matcher.start()) + digits + episode.substring(matcher.end());
    }

    private void addKeyword(String text) {
        TextView chip = (TextView) getLayoutInflater().inflate(R.layout.adapter_danmaku_keyword, binding.keywords, false);
        chip.setId(View.generateViewId());
        chip.setText(text);
        chip.setNextFocusUpId(R.id.setting);
        chip.setOnClickListener(view -> onKeyword(text));
        binding.keywords.addView(chip);
    }

    private void onKeyword(String text) {
        keyword = text;
        custom = false;
        binding.keyword.setText("");
        updateKeywords();
        focusResults = false;
        search();
    }

    private void onKeywordFocus(View view, boolean focused) {
        // Editing starts from the active keyword; an unsubmitted edit falls back to the hint.
        if (focused && TextUtils.isEmpty(binding.keyword.getText())) {
            binding.keyword.setText(keyword);
            binding.keyword.setSelection(keyword.length());
        } else if (!focused && !custom) {
            binding.keyword.setText("");
        } else if (!focused) {
            binding.keyword.setText(keyword);
        }
    }

    private void submit() {
        CharSequence value = binding.keyword.getText();
        String text = value == null ? "" : value.toString().trim();
        Util.hideKeyboard(binding.keyword);
        if (text.isEmpty()) return;
        keyword = text;
        custom = !keywords.contains(text);
        updateKeywords();
        focusResults = true;
        search();
    }

    private void updateKeywords() {
        for (int i = 0; i < binding.keywords.getChildCount(); i++) {
            View chip = binding.keywords.getChildAt(i);
            chip.setSelected(!custom && keywords.get(i).equals(keyword));
        }
        binding.keyword.setSelected(custom);
        updateQuery();
    }

    private void onStep(int delta) {
        String next = step(episode, delta);
        if (next.equals(episode) && !anyEpisode) return;
        episode = next;
        anyEpisode = false;
        updateEpisode();
        // Stepping several episodes in a row sends one request.
        focusResults = false;
        binding.getRoot().removeCallbacks(research);
        binding.getRoot().postDelayed(research, STEP_DELAY);
    }

    private void setAnyEpisode(boolean any) {
        if (!any && episode.isEmpty()) return;
        anyEpisode = any;
        updateEpisode();
        focusResults = false;
        search();
    }

    private void updateEpisode() {
        boolean has = !episode.isEmpty();
        boolean canPrev = number(episode) > 1;
        binding.episode.setVisibility(has ? VISIBLE : GONE);
        binding.episode.setText(episode);
        binding.episode.setSelected(has && !anyEpisode);
        binding.anyEpisode.setSelected(anyEpisode || !has);
        if (!canPrev && binding.prev.isFocused()) (has ? binding.episode : binding.next).requestFocus();
        binding.prev.setEnabled(canPrev);
        binding.prev.setAlpha(canPrev ? 1f : 0.4f);
        updateQuery();
    }

    private void updateQuery() {
        String query = anyEpisode || episode.isEmpty() ? keyword : getString(R.string.tv_danmaku_query, keyword, episode);
        binding.query.setText(query);
    }

    private void retry() {
        focusResults = true;
        search();
    }

    private void search() {
        if (binding == null || player == null) return;
        binding.getRoot().removeCallbacks(research);
        if (DanmakuSetting.getEffectiveApiUrl().isEmpty()) {
            DanmakuApi.cancel();
            feedback(getString(R.string.tv_danmaku_no_api), false, false, true);
        } else if (keyword.isEmpty()) {
            DanmakuApi.cancel();
            feedback(getString(R.string.tv_danmaku_no_keyword), false, false, false);
        } else {
            // Earlier results stay, dimmed, so the drawer keeps its height while loading.
            if (adapter.getItemCount() > 0) {
                binding.recycler.setAlpha(0.5f);
                binding.count.setText(R.string.tv_danmaku_searching);
            } else {
                feedback(getString(R.string.tv_danmaku_searching), true, false, false);
            }
            DanmakuApi.search(keyword, anyEpisode ? "" : episode, this::onSuccess, this::onError);
        }
    }

    private void onSuccess(List<Danmaku> items) {
        if (binding == null) return;
        if (items.isEmpty()) {
            feedback(getString(R.string.tv_danmaku_no_result), false, false, false);
            return;
        }
        boolean lost = binding.feedback.hasFocus() || binding.recycler.hasFocus();
        adapter.setItems(items, current());
        binding.feedback.setVisibility(GONE);
        binding.recycler.setAlpha(1f);
        binding.recycler.setVisibility(VISIBLE);
        binding.count.setText(getString(R.string.tv_danmaku_result_count, items.size()));
        if (focusResults || lost) focusResult();
        focusResults = false;
    }

    private void onError(Exception e) {
        if (binding == null) return;
        String message = TextUtils.isEmpty(e.getMessage()) ? ResUtil.getString(R.string.error_empty) : e.getMessage();
        feedback(message, false, true, true);
    }

    /** Replaces the results with a message; failures offer a way out and take focus when it was lost. */
    private void feedback(String message, boolean loading, boolean retry, boolean configure) {
        boolean focused = binding.feedback.hasFocus() || binding.recycler.hasFocus();
        adapter.clear();
        binding.recycler.setAlpha(1f);
        binding.recycler.setVisibility(GONE);
        binding.count.setText("");
        binding.feedback.setVisibility(VISIBLE);
        binding.progress.setVisibility(loading ? VISIBLE : GONE);
        binding.retry.setVisibility(retry ? VISIBLE : GONE);
        binding.configure.setVisibility(configure ? VISIBLE : GONE);
        TextView text = binding.getRoot().findViewById(android.R.id.message);
        text.setText(message);
        if (loading) return;
        View action = retry ? binding.retry : configure ? binding.configure : null;
        if (action != null && (focusResults || focused)) action.requestFocus();
        else if (focused) focusDefault();
        focusResults = false;
    }

    private void focusResult() {
        int position = adapter.getPosition();
        binding.recycler.scrollToPosition(position);
        binding.recycler.post(() -> {
            if (binding == null) return;
            RecyclerView.ViewHolder holder = binding.recycler.findViewHolderForAdapterPosition(position);
            if (holder != null) holder.itemView.requestFocus();
            else binding.recycler.requestFocus();
        });
    }

    private void focusDefault() {
        if (binding == null || binding.recycler.hasFocus() || binding.feedback.hasFocus()) return;
        View chip = null;
        for (int i = 0; i < binding.keywords.getChildCount(); i++) if (binding.keywords.getChildAt(i).isSelected()) chip = binding.keywords.getChildAt(i);
        (chip != null ? chip : binding.keyword).requestFocus();
    }

    private Danmaku current() {
        for (Danmaku item : player.getDanmakus()) if (item.isSelected()) return item;
        return null;
    }

    private void onItemClick(Danmaku item) {
        player.setDanmaku(item);
        dismiss();
    }

    private void onSetting(View view) {
        DanmakuApiDialog.show(this, keyword, anyEpisode ? "" : episode);
    }

    @Override
    public void setDanmakuApi(String url) {
        DanmakuSetting.putApiUrl(url);
        focusResults = true;
        search();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (player == null) dismissAllowingStateLoss();
    }

    @Override
    public void onDestroyView() {
        binding.getRoot().removeCallbacks(research);
        binding = null;
        DanmakuApi.cancel();
        super.onDestroyView();
    }
}
