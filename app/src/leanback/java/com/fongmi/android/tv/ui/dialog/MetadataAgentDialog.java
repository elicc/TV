package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogMetadataAgentBinding;
import com.fongmi.android.tv.setting.MetadataAgentSetting;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MetadataAgentDialog extends BaseAlertDialog {

    private DialogMetadataAgentBinding binding;

    public static void show(FragmentActivity activity) {
        new MetadataAgentDialog().show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogMetadataAgentBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.url.setText(MetadataAgentSetting.getUrl());
        binding.apiKey.setText(MetadataAgentSetting.getApiKey());
        binding.url.setSelection(binding.url.length());
    }

    @Override
    protected void initEvent() {
        binding.positive.setOnClickListener(this::onPositive);
        binding.negative.setOnClickListener(view -> dismiss());
        binding.apiKey.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) binding.positive.performClick();
            return true;
        });
    }

    private void onPositive(View view) {
        String url = binding.url.getText().toString().trim();
        if (!TextUtils.isEmpty(url) && !url.startsWith("http://") && !url.startsWith("https://")) {
            binding.url.setError(binding.url.getHint());
            binding.url.requestFocus();
            return;
        }
        MetadataAgentSetting.putUrl(url);
        MetadataAgentSetting.putApiKey(binding.apiKey.getText().toString());
        if (requireActivity() instanceof Listener) ((Listener) requireActivity()).onMetadataAgentChanged();
        dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(0.5f);
    }

    public interface Listener {
        void onMetadataAgentChanged();
    }
}
