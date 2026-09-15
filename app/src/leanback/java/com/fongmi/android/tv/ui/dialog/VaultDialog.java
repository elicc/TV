package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogVaultBinding;
import com.fongmi.android.tv.db.ConfigVault;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * The single unprompted offer to turn config backup on.
 *
 * <p>Shown once per install, and only after a source has actually loaded — asking for storage on
 * a screen with nothing on it asks the viewer to pay before they have seen the goods. Declining
 * is remembered, so this never returns on its own; the settings row stays as the way back.
 */
public class VaultDialog extends BaseAlertDialog {

    /** Stable tag so the home screen can tell "already showing" from "show another". */
    public static final String TAG = "vault-dialog";

    private DialogVaultBinding binding;

    public static VaultDialog create() {
        return new VaultDialog();
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogVaultBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initEvent() {
        binding.positive.setOnClickListener(v -> {
            dismiss();
            ((Listener) requireActivity()).onVaultEnable();
        });
        binding.negative.setOnClickListener(v -> {
            ConfigVault.dismissPrompt();
            dismiss();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(0.42f);
        binding.positive.requestFocus();
    }

    /** Implemented by the home screen, which owns the permission round trip. */
    public interface Listener {

        void onVaultEnable();
    }
}
