package com.fongmi.android.tv.ui.dialog;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
import com.fongmi.android.tv.db.VaultPolicy;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.Callback;
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
    private boolean vaultEntryHandled;
    private boolean vaultChecking;
    private boolean vaultCheckRunning;
    private boolean vaultRestoring;
    private int vaultPrompt; // 0: none, 1: permission, 2: full backup, 3: sources only
    private AlertDialog vaultDialog;
    private Bundle restoredState;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restoredState = savedInstanceState;
        if (savedInstanceState == null) return;
        type = savedInstanceState.getInt("config.type");
        edit = savedInstanceState.getBoolean("config.edit");
        vaultEntryHandled = savedInstanceState.getBoolean("vault.entryHandled");
        vaultChecking = savedInstanceState.getBoolean("vault.checking");
        vaultPrompt = savedInstanceState.getInt("vault.prompt");
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("config.type", type);
        outState.putBoolean("config.edit", edit);
        outState.putString("config.url", url);
        outState.putString("config.text", binding.text.getText().toString());
        outState.putString("config.name", binding.name.getText().toString());
        outState.putBoolean("vault.entryHandled", vaultEntryHandled);
        outState.putBoolean("vault.checking", vaultChecking);
        outState.putInt("vault.prompt", vaultPrompt);
    }

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
        if (restoredState != null) {
            url = restoredState.getString("config.url", url);
            binding.text.setText(restoredState.getString("config.text", url));
            binding.name.setText(restoredState.getString("config.name", ""));
        }
        binding.text.setSelection(binding.text.length());
        boolean firstSourceSetup = isSourceType() && !edit && TextUtils.isEmpty(url);
        binding.choose.setVisibility(firstSourceSetup ? View.GONE : View.VISIBLE);
        updatePositiveText(binding.text.getText().toString());
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
        binding.vaultGrant.setOnClickListener(v -> requestVaultPermission());
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
        if (vaultRestoring) return;
        String name = binding.name.getText().toString().trim();
        String text = binding.text.getText().toString().trim();
        if (!edit && text.isEmpty() && isSourceType()) {
            requestFileChooser();
        } else if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(UrlUtil.scheme(text))) {
            PermissionUtil.requestFile(this, allGranted -> {
                if (!allGranted || !isAdded()) return;
                submit(name, text);
            });
        } else {
            submit(name, text);
        }
    }

    private boolean isDialogActive() {
        return isAdded() && !isRemoving() && getDialog() != null && getDialog().isShowing();
    }

    private void beginVaultFlow() {
        // Once per opening, not once per installation. Returning from Settings must not loop.
        if (!isSourceType() || vaultEntryHandled) return;
        vaultEntryHandled = true;
        ConfigVault.deferAutoRestore();
        if (ConfigVault.isWritable()) checkRestoreSource();
        else {
            vaultPrompt = 1;
            showVaultPrompt();
        }
    }

    private void showVaultPrompt() {
        if (!isDialogActive() || !isResumed() || vaultPrompt == 0 || vaultDialog != null) return;
        boolean permission = vaultPrompt == 1;
        boolean unsupported = permission && PermissionUtil.allFilesAccess(requireActivity()) == PermissionUtil.AllFilesAccess.UNSUPPORTED;
        vaultDialog = builder()
                .setTitle(permission ? R.string.tv_vault_permission_title : R.string.tv_vault_restore_title)
                .setMessage(permission ? (unsupported ? R.string.tv_vault_unsupported_source : R.string.tv_vault_permission_body)
                        : vaultPrompt == 2 ? R.string.tv_vault_restore_confirm : R.string.tv_vault_restore_sources_confirm)
                .setNegativeButton(R.string.tv_vault_manual, (dialog, which) -> vaultPrompt = 0)
                .setOnCancelListener(dialog -> vaultPrompt = 0)
                .create();
        if (!unsupported) vaultDialog.setButton(AlertDialog.BUTTON_POSITIVE,
                getString(permission ? R.string.tv_vault_grant : R.string.tv_vault_restore_accept), (dialog, which) -> {
                    vaultPrompt = 0;
                    if (permission) requestVaultPermission();
                    else restoreVault();
                });
        vaultDialog.setOnDismissListener(dialog -> vaultDialog = null);
        vaultDialog.show();
    }

    private void requestVaultPermission() {
        ConfigVault.deferAutoRestore();
        if (ConfigVault.isWritable()) {
            checkRestoreSource();
            return;
        }
        if (PermissionUtil.allFilesAccess(requireActivity()) == PermissionUtil.AllFilesAccess.UNSUPPORTED) {
            Notify.show(R.string.tv_vault_unsupported);
            return;
        }
        // Own this result instead of relying on PermissionX's grant-only callback or onResume,
        // which also fires before Settings has actually opened. A refusal is a result too.
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + requireContext().getPackageName()));
        if (intent.resolveActivity(requireActivity().getPackageManager()) == null)
            intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        try {
            vaultPermissionLauncher.launch(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Notify.show(R.string.tv_vault_unsupported);
        }
    }

    private void checkRestoreSource() {
        if (!isDialogActive() || !ConfigVault.isWritable() || vaultCheckRunning) return;
        vaultChecking = true;
        vaultCheckRunning = true;
        ConfigVault.findRestoreSource(source -> {
            vaultChecking = false;
            vaultCheckRunning = false;
            if (!isDialogActive()) return;
            vaultPrompt = switch (source) {
                case BACKUP -> 2;
                case BOOTSTRAP -> 3;
                default -> 0;
            };
            showVaultPrompt();
        });
    }

    private void restoreVault() {
        if (!isDialogActive() || vaultRestoring) return;
        vaultRestoring = true;
        binding.positive.setEnabled(false);
        ConfigVault.restore(source -> {
            vaultRestoring = false;
            if (source != VaultPolicy.Source.NONE) {
                VodConfig.get().init().load(new Callback());
                LiveConfig.get().init().load();
                WallConfig.get().init().load();
                ConfigEvent.common();
                RefreshEvent.history();
                RefreshEvent.keep();
            }
            if (!isDialogActive()) return;
            binding.positive.setEnabled(true);
            if (source == VaultPolicy.Source.NONE) {
                Notify.show(R.string.tv_vault_restore_missing);
                return;
            }
            Config config = type == 0 ? VodConfig.get().getConfig() : LiveConfig.get().getConfig();
            binding.name.setText(config.getName());
            binding.text.setText(url = config.getUrl());
            binding.text.setSelection(binding.text.length());
            binding.choose.setVisibility(View.VISIBLE);
            Notify.show(R.string.tv_vault_restored);
        });
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
        if (TextUtils.isEmpty(text) || !isAdded() || vaultRestoring) return;
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
        refreshVaultBanner();
        if (!vaultEntryHandled) beginVaultFlow();
        else if (vaultChecking) checkRestoreSource();
        else showVaultPrompt();
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

    @Override
    public void onDestroyView() {
        if (vaultDialog != null) vaultDialog.dismiss();
        vaultDialog = null;
        super.onDestroyView();
    }

    private final ActivityResultLauncher<Intent> vaultPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                vaultChecking = ConfigVault.isWritable();
                if (!isDialogActive()) return;
                refreshVaultBanner();
                if (vaultChecking) checkRestoreSource();
            });

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> FileChooser.getUri(result, this::setConfig));

    private void setConfig(Uri uri) {
        if (!isAdded()) return;
        applyConfig(Config.find(UrlUtil.toLocalUrl(uri), type));
    }
}
