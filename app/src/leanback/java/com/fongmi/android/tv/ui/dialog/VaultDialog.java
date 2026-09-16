package com.fongmi.android.tv.ui.dialog;

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogVaultBinding;
import com.fongmi.android.tv.db.ConfigVault;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Explains either side of the config-vault grant before opening system settings.
 *
 * <p>A source-less fresh install gets the recovery wording; an install that has just loaded a
 * source gets the backup wording. Their dismissals are stored separately so declining one offer
 * never suppresses the other.
 */
public class VaultDialog extends BaseAlertDialog {

    /** Stable tag so the home screen can tell "already showing" from "show another". */
    public static final String TAG = "vault-dialog";
    private static final String ARG_RESTORE = "restore";

    private DialogVaultBinding binding;

    public static VaultDialog create() {
        return new VaultDialog();
    }

    /** Render this instance as the fresh-install recovery prompt. */
    public VaultDialog restore() {
        Bundle args = new Bundle();
        args.putBoolean(ARG_RESTORE, true);
        setArguments(args);
        return this;
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
    protected void initView() {
        if (!isRestore()) return;
        binding.title.setText(R.string.tv_vault_restore_title);
        binding.body.setText(R.string.tv_vault_restore_body);
        binding.positive.setText(R.string.tv_vault_grant);
    }

    @Override
    protected void initEvent() {
        binding.positive.setOnClickListener(v -> {
            dismiss();
            if (isRestore()) ((Listener) requireActivity()).onVaultRestore();
            else ((Listener) requireActivity()).onVaultEnable();
        });
        binding.negative.setOnClickListener(v -> {
            if (isRestore()) ConfigVault.dismissRestorePrompt();
            else ConfigVault.dismissPrompt();
            dismiss();
        });
    }

    private boolean isRestore() {
        return getArguments() != null && getArguments().getBoolean(ARG_RESTORE, false);
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

        void onVaultRestore();
    }
}
