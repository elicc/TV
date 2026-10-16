package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.api.DanmakuProbe;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.databinding.DialogDanmakuApiBinding;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.DanmakuListener;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.utils.QRCode;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class DanmakuApiDialog extends AppCompatDialogFragment {

    private DialogDanmakuApiBinding binding;
    private final DanmakuProbe probe = new DanmakuProbe();
    private boolean testing;

    public static void show(FragmentActivity activity) {
        new DanmakuApiDialog().show(activity.getSupportFragmentManager(), null);
    }

    public static void showProbe(FragmentActivity activity) {
        DanmakuApiDialog dialog = new DanmakuApiDialog();
        Bundle args = new Bundle();
        args.putBoolean("probe", true);
        dialog.setArguments(args);
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    public static void show(Fragment fragment) {
        new DanmakuApiDialog().show(fragment.getChildFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AppCompatDialog dialog = new AppCompatDialog(requireContext(), getTheme());
        dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        binding = DialogDanmakuApiBinding.inflate(getLayoutInflater());
        dialog.setContentView(binding.getRoot());
        initView();
        initEvent();
        return dialog;
    }

    private void initView() {
        // A source default must not silently become a user override on save.
        String text = DanmakuSetting.getApiUrl();
        binding.text.setText(text);
        binding.text.setSelection(TextUtils.isEmpty(text) ? 0 : text.length());
        binding.code.setImageBitmap(QRCode.getBitmap(Server.get().getAddress(4), 240, 0));
        if (getArguments() != null && getArguments().getBoolean("probe")) binding.testName.requestFocus();
        else binding.text.requestFocus();
    }

    private void initEvent() {
        binding.positive.setOnClickListener(this::onPositive);
        binding.negative.setOnClickListener(v -> dismiss());
        binding.useDefault.setOnClickListener(v -> binding.text.setText(""));
        binding.test.setOnClickListener(v -> test());
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                probe.cancel();
                testing = false;
                binding.test.setText(R.string.tv_dm_probe);
                binding.result.setText(R.string.tv_dm_edited);
            }
            public void afterTextChanged(Editable s) { }
        };
        binding.text.addTextChangedListener(watcher);
        binding.testName.addTextChangedListener(watcher);
        binding.testEpisode.addTextChangedListener(watcher);
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            Util.hideKeyboard(binding.text);
            binding.positive.requestFocus();
            return true;
        });
    }

    private String draftUrl() {
        String url = binding.text.getText().toString().trim();
        return url.isEmpty() ? VodConfig.get().getConfig().getDanmaku() : url;
    }

    private void test() {
        if (testing) return;
        String name = binding.testName.getText().toString().trim();
        String episode = binding.testEpisode.getText().toString().trim();
        if (name.isEmpty() || episode.isEmpty()) {
            binding.result.setText(R.string.tv_dm_test_empty);
            (name.isEmpty() ? binding.testName : binding.testEpisode).requestFocus();
            return;
        }
        Util.hideKeyboard(binding.testName);
        testing = true;
        binding.result.setText(R.string.tv_dm_testing);
        binding.test.setText(R.string.tv_dm_testing);
        probe.start(draftUrl(), name, episode, result -> {
            if (binding == null || !isAdded()) return;
            testing = false;
            binding.test.setText(R.string.tv_dm_probe);
            binding.result.setText(format(result));
            binding.result.post(() -> {
                if (binding != null) binding.result.requestRectangleOnScreen(new Rect(0, 0, binding.result.getWidth(), binding.result.getHeight()), false);
            });
        });
    }

    private String format(DanmakuProbe.Result result) {
        switch (result.code) {
            case SUCCESS: return getString(R.string.tv_dm_success, result.candidateCount, result.commentCount);
            case NO_RESULTS: return getString(R.string.tv_dm_no_results);
            case INVALID_URL: return getString(R.string.danmaku_api_invalid_url);
            case SEARCH_HTTP: return getString(R.string.tv_dm_search_http, result.httpStatus);
            case SITE_CONFIG: return getString(R.string.danmaku_api_site_config);
            case SEARCH_FORMAT: return getString(R.string.danmaku_api_invalid_response);
            case XML_HTTP: return getString(R.string.tv_dm_xml_http, result.httpStatus);
            case XML_FORMAT: return getString(R.string.tv_dm_xml_format);
            case LIMIT_EXCEEDED: return getString(R.string.tv_dm_limit);
            default: return getString(R.string.danmaku_search_network_error);
        }
    }

    private void onPositive(View view) {
        String url = binding.text.getText().toString().trim();
        if (!url.isEmpty() && !DanmakuApi.isValidUrl(url)) {
            binding.text.setError(getString(R.string.danmaku_api_invalid_url));
            binding.text.requestFocus();
            return;
        }
        getListener().setDanmakuApi(url);
        dismiss();
    }

    private DanmakuListener getListener() {
        Fragment parent = getParentFragment();
        if (parent instanceof DanmakuListener) return (DanmakuListener) parent;
        return (DanmakuListener) requireActivity();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() != ServerEvent.Type.SETTING) return;
        binding.text.setText(event.text());
        binding.positive.requestFocus(); // Remote input is a draft, not an implicit save.
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            // This is a complete form, not an AlertDialog custom panel. Size its
            // window directly so the scroll area and pinned actions share one bound.
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            getDialog().getWindow().setDimAmount(0.35f);
            getDialog().getWindow().setLayout((int) (ResUtil.getScreenWidth(requireActivity()) * 0.88f), (int) (ResUtil.getScreenHeight(requireActivity()) * 0.82f));
        }
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        probe.cancel();
        if (testing && binding != null) {
            binding.test.setText(R.string.tv_dm_probe);
            binding.result.setText(R.string.tv_dm_idle);
        }
        testing = false;
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        probe.cancel();
        binding = null;
        super.onDestroyView();
    }
}
