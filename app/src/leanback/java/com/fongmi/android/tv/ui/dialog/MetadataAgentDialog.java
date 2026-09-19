package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogMetadataAgentBinding;
import com.fongmi.android.tv.db.ConfigVault;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.MetadataAgentSetting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.QRCode;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

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
        binding.code.setImageBitmap(QRCode.getBitmap(Server.get().getAddress(6), 200, 0));
        String address = Server.get().getAddress();
        binding.info.setText(Util.isEmulator()
                ? ResUtil.getString(R.string.push_info_emulator, address, Server.get().getLocalAddress())
                : ResUtil.getString(R.string.push_info, address).replace("，", "\n"));
        refreshVaultBanner();
    }

    @Override
    protected void initEvent() {
        binding.vaultGrant.setOnClickListener(view -> PermissionUtil.requestAllFiles(requireActivity(), granted -> {
            refreshVaultBanner();
            if (ConfigVault.isWritable() && MetadataAgentSetting.isConfigured()) ConfigVault.save();
            if (!ConfigVault.isWritable()) Notify.show(R.string.tv_vault_denied);
        }));
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
        save(url, binding.apiKey.getText().toString());
    }

    private void save(String url, String apiKey) {
        MetadataAgentSetting.putUrl(url);
        MetadataAgentSetting.putApiKey(apiKey);
        ConfigVault.save();
        if (requireActivity() instanceof Listener) ((Listener) requireActivity()).onMetadataAgentChanged();
        dismiss();
    }

    private void refreshVaultBanner() {
        if (binding == null) return;
        boolean missing = !ConfigVault.isWritable();
        boolean unsupported = PermissionUtil.allFilesAccess(requireActivity()) == PermissionUtil.AllFilesAccess.UNSUPPORTED;
        binding.vault.setVisibility(missing ? View.VISIBLE : View.GONE);
        binding.vaultText.setText(unsupported ? R.string.tv_vault_unsupported_source : R.string.metadata_agent_vault_banner);
        binding.vaultGrant.setVisibility(unsupported ? View.GONE : View.VISIBLE);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() != ServerEvent.Type.METADATA_AGENT || !isAdded()) return;
        String url = event.text() == null ? "" : event.text().trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) return;
        binding.url.setText(url);
        binding.apiKey.setText(event.name());
        save(url, event.name());
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshVaultBanner();
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(0.55f);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    public interface Listener {
        void onMetadataAgentChanged();
    }
}
