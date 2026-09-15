package com.fongmi.android.tv.ui.dialog;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.DialogConfigBinding;
import com.fongmi.android.tv.db.ConfigVault;
import com.fongmi.android.tv.db.SourceBootstrap;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.QRCode;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class ConfigDialog extends BaseAlertDialog {

    private DialogConfigBinding binding;
    private boolean append = true;
    private boolean edit;
    private String url;
    private int type;
    /** Set while a file-access grant is in flight; a refusal only surfaces on resume. */
    private boolean mVaultPending;

    public static ConfigDialog create() {
        return new ConfigDialog();
    }

    public ConfigDialog vod() {
        type = 0;
        return this;
    }

    public ConfigDialog live() {
        type = 1;
        return this;
    }

    public ConfigDialog wall() {
        type = 2;
        return this;
    }

    public ConfigDialog edit() {
        edit = true;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogConfigBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.text.setText(url = getUrl());
        binding.text.setSelection(TextUtils.isEmpty(url) ? 0 : url.length());
        boolean firstSourceSetup = isSourceType() && !edit && TextUtils.isEmpty(url);
        binding.choose.setVisibility(firstSourceSetup ? View.GONE : View.VISIBLE);
        updatePositiveText(url);
        binding.code.setImageBitmap(QRCode.getBitmap(Server.get().getAddress(4), 200, 0));
        String mainAddress = Server.get().getAddress();
        String info;
        if (Util.isEmulator()) {
            // On the emulator `getIp()` returns 10.0.2.15, which only routes
            // from inside the VM. Surface the localhost alternative plus
            // the `adb forward` incantation so a developer running the
            // emulator can test the H5 form from the host browser.
            info = ResUtil.getString(R.string.push_info_emulator, mainAddress, Server.get().getLocalAddress());
        } else {
            info = ResUtil.getString(R.string.push_info, mainAddress).replace("\uff0c", "\n");
        }
        binding.info.setText(info);
        refreshVaultBanner();
    }

    /**
     * Without "All files access" neither saving this source nor recovering a previous one can
     * work, and the viewer is already looking at the screen where that matters. The notice is
     * driven by the grant itself rather than by any callback's opinion of it, so a refusal —
     * which the permission library does not report at all — simply leaves it standing, which
     * is the truth.
     */
    private void refreshVaultBanner() {
        if (binding == null) return;
        boolean missing = !ConfigVault.isWritable();
        boolean unsupported = PermissionUtil.allFilesAccess(requireActivity()) == PermissionUtil.AllFilesAccess.UNSUPPORTED;
        binding.vault.setVisibility(missing ? View.VISIBLE : View.GONE);
        binding.vaultText.setText(unsupported ? R.string.tv_vault_unsupported_source : R.string.tv_vault_banner);
        binding.vaultGrant.setVisibility(unsupported ? View.GONE : View.VISIBLE);
        binding.text.setNextFocusDownId(missing && !unsupported ? R.id.vaultGrant : R.id.positive);
    }

    @Override
    protected void initEvent() {
        binding.vaultGrant.setOnClickListener(v -> PermissionUtil.requestAllFiles(requireActivity(), granted -> {
            refreshVaultBanner();
            // Only reachable when the device has no such settings screen — a plain refusal is
            // never reported — and the viewer deserves to hear why nothing changed.
            if (!ConfigVault.isWritable()) Notify.show(R.string.tv_vault_denied);
        }));
        binding.choose.setOnClickListener(this::onChoose);
        binding.positive.setOnClickListener(this::onPositive);
        binding.negative.setOnClickListener(this::onNegative);
        binding.text.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
                updatePositiveText(s.toString());
            }
        });
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) binding.positive.performClick();
            return true;
        });
    }

    private String getUrl() {
        return switch (type) {
            case 0 -> VodConfig.getUrl();
            case 1 -> LiveConfig.getUrl();
            case 2 -> WallConfig.getUrl();
            default -> "";
        };
    }

    private void onChoose(View view) {
        requestFileChooser();
    }

    private void detect(String s) {
        if (append && "h".equalsIgnoreCase(s)) {
            append = false;
            binding.text.append("ttp://");
        } else if (append && "f".equalsIgnoreCase(s)) {
            append = false;
            binding.text.append("ile://");
        } else if (append && "a".equalsIgnoreCase(s)) {
            append = false;
            binding.text.append("ssets://");
        } else if (s.length() > 1) {
            append = false;
        } else if (s.isEmpty()) {
            append = true;
        }
    }

    private void onPositive(View view) {
        String name = binding.name.getText().toString().trim();
        String text = binding.text.getText().toString().trim();
        if (!edit && text.isEmpty() && isSourceType()) {
            requestRestoreOrChoose();
        } else if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(UrlUtil.scheme(text))) {
            PermissionUtil.requestFile(this, allGranted -> {
                if (!allGranted || !isAdded()) return;
                submit(name, text);
            });
        } else {
            submit(name, text);
        }
    }

    private void requestRestoreOrChoose() {
        if (PermissionUtil.allFilesAccess(requireActivity()) == PermissionUtil.AllFilesAccess.UNSUPPORTED) {
            requestFileChooser();
            return;
        }
        mVaultPending = true;
        // A supported settings screen completes on grant; a refusal is settled from onResume.
        // Unsupported firmware took the local chooser path above and never enters this flow.
        PermissionUtil.requestAllFiles(requireActivity(), granted -> resolveRestore());
    }

    private void resolveRestore() {
        if (!mVaultPending) return;
        mVaultPending = false;
        if (!ConfigVault.isWritable()) {
            refreshVaultBanner();
            Notify.show(R.string.tv_vault_denied);
        } else {
            SourceBootstrap.find(type, config -> {
                if (!isAdded()) return;
                if (config == null) FileChooser.from(launcher).show();
                else applyConfig(config);
            });
        }
    }

    private boolean isSourceType() {
        return type == 0 || type == 1;
    }

    private void updatePositiveText(String text) {
        boolean empty = TextUtils.isEmpty(text) || TextUtils.isEmpty(text.trim());
        if (!isSourceType()) {
            binding.positive.setText(edit ? R.string.dialog_edit : R.string.dialog_positive);
        } else if (!edit && empty) {
            binding.positive.setText(R.string.tv_config_continue);
        } else {
            binding.positive.setText(R.string.tv_config_apply);
        }
    }

    private void requestFileChooser() {
        PermissionUtil.requestFile(this, allGranted -> {
            if (!allGranted || !isAdded()) return;
            FileChooser.from(launcher).show();
        });
    }

    private void submit(String name, String text) {
        if (edit) Config.find(url, type).url(text).update();
        if (text.isEmpty()) {
            Config.delete(url, type);
            SourceBootstrap.save();
        }
        Config config = name.isEmpty() ? Config.find(text, type) : Config.find(text, name, type);
        applyConfig(config);
    }

    private void applyConfig(Config config) {
        ((ConfigListener) requireActivity()).setConfig(config);
        dismiss();
    }

    private void onNegative(View view) {
        dismiss();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() != ServerEvent.Type.SETTING) return;
        String text = event.text();
        if (TextUtils.isEmpty(text) || !isAdded()) return;
        // The H5 panel posts the URL+name the user confirmed on their phone.
        // Apply it straight away instead of just filling the input and
        // requiring a redundant confirm click on the TV; the same flow now
        // serves both the first-launch empty-source dialog and the Settings
        // edit dialog.
        binding.name.setText(event.name());
        binding.text.setText(text);
        binding.text.setSelection(binding.text.getText().length());
        submit(event.name(), text);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Coming back from the file-access settings screen.
        resolveRestore();
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

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> FileChooser.getUri(result, this::setConfig));

    private void setConfig(Uri uri) {
        if (!isAdded()) return;
        applyConfig(Config.find(UrlUtil.toLocalUrl(uri), type));
    }
}
