/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AuthenticatorDescription;
import android.accounts.AccountManagerFuture;
import android.accounts.AccountManagerCallback;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.backup.IBackupManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import com.android.internal.os.IKillSwitchService;
import android.util.Log;

/**
 * Gesture lock pattern settings.
 */
public class PrivacySettings extends SettingsPreferenceFragment implements
        DialogInterface.OnClickListener {

    private static final String TAG = "PrivacySettings";

    // Vendor specific
    private static final String GSETTINGS_PROVIDER = "com.google.settings";
    private static final String BACKUP_CATEGORY = "backup_category";
    private static final String BACKUP_DATA = "backup_data";
    private static final String AUTO_RESTORE = "auto_restore";
    private static final String CONFIGURE_ACCOUNT = "configure_account";

    private static final String ACCOUNT_TYPE_CYANOGEN = "com.cyanogen";
    private static final String EXTRA_CREATE_ACCOUNT = "create-account";
    private static final String LOCK_TO_CYANOGEN_ACCOUNT = "lock_to_cyanogen_account";
    private static final String EXTRA_SETCKS = "setCks";
    private static final String EXTRA_AUTHCKS = "authCks";

    private IBackupManager mBackupManager;
    private CheckBoxPreference mBackup;
    private CheckBoxPreference mAutoRestore;
    private CheckBoxPreference mLockDeviceToCyanogenAccount;
    private Dialog mConfirmDialog;
    private PreferenceScreen mConfigure;

    private static final int DIALOG_ERASE_BACKUP = 2;
    private int mDialogType;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.privacy_settings);
        final PreferenceScreen screen = getPreferenceScreen();

        mBackupManager = IBackupManager.Stub.asInterface(
                ServiceManager.getService(Context.BACKUP_SERVICE));

        mBackup = (CheckBoxPreference) screen.findPreference(BACKUP_DATA);
        mAutoRestore = (CheckBoxPreference) screen.findPreference(AUTO_RESTORE);
        mConfigure = (PreferenceScreen) screen.findPreference(CONFIGURE_ACCOUNT);
        mLockDeviceToCyanogenAccount = (CheckBoxPreference)
                screen.findPreference(LOCK_TO_CYANOGEN_ACCOUNT);

        if (!hasCyanogenAccountType(getActivity())) {
            screen.removePreference(mLockDeviceToCyanogenAccount);
            mLockDeviceToCyanogenAccount = null;
        }

        // Vendor specific
        if (getActivity().getPackageManager().
                resolveContentProvider(GSETTINGS_PROVIDER, 0) == null) {
            screen.removePreference(findPreference(BACKUP_CATEGORY));
        }
        updateToggles();
    }

    private boolean hasLoggedInCyanogenAccount(Context context) {
        AccountManager accountManager = (AccountManager)
                context.getSystemService(Context.ACCOUNT_SERVICE);
        Account[] accountsByType = accountManager.getAccountsByType(ACCOUNT_TYPE_CYANOGEN);
        return accountsByType != null && accountsByType.length > 0;
    }

    public static boolean hasCyanogenAccountType(Context context) {
        AccountManager accountManager = (AccountManager)
                context.getSystemService(Context.ACCOUNT_SERVICE);
        for (AuthenticatorDescription authenticatorDescription :
                accountManager.getAuthenticatorTypes()) {
            if (authenticatorDescription.type.equals(ACCOUNT_TYPE_CYANOGEN)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDeviceLocked() {
        IBinder b = ServiceManager.getService(Context.KILLSWITCH_SERVICE);
        IKillSwitchService service = IKillSwitchService.Stub.asInterface(b);
        if (service != null) {
            try {
                return service.isDeviceLocked();
            } catch (Exception e) {
                // silently fail
            }
        }
        return false;
    }

    public static void confirmCyanogenCredentials(final Activity activity,
                                                  boolean setCks,
                                                  AccountManagerCallback<Bundle> callback) {
        // verify creds
        AccountManager accountManager = (AccountManager)
                activity.getSystemService(Context.ACCOUNT_SERVICE);
        Account cyanogenAccount = null;
        Account[] accountsByType = accountManager.getAccountsByType(ACCOUNT_TYPE_CYANOGEN);
        if (accountsByType != null && accountsByType.length > 0) {
            // take the first account
            cyanogenAccount = accountsByType[0];
        }

        Bundle opts = new Bundle();
        opts.putBoolean(EXTRA_AUTHCKS, true);
        opts.putInt(EXTRA_SETCKS, setCks ? 1 : 0);

        accountManager.confirmCredentials(cyanogenAccount, opts, activity,
            new AccountManagerCallback<Bundle>() {
                public void run(AccountManagerFuture<Bundle> f) {
                    try {
                        Bundle b = f.getResult();
                        Intent i = b.getParcelable("KEY_INTENT");
                        activity.startActivityForResult(i, 0);
                    } catch(Throwable t) {
                        Log.e(TAG, "confirmCredentials failed", t);
                    }
                }
            }, null);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Refresh UI
        updateToggles();
    }

    @Override
    public void onStop() {
        if (mConfirmDialog != null && mConfirmDialog.isShowing()) {
            mConfirmDialog.dismiss();
        }
        mConfirmDialog = null;
        mDialogType = 0;
        super.onStop();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (preference == mBackup) {
            if (!mBackup.isChecked()) {
                showEraseBackupDialog();
            } else {
                setBackupEnabled(true);
            }
        } else if (preference == mAutoRestore) {
            boolean curState = mAutoRestore.isChecked();
            try {
                mBackupManager.setAutoRestore(curState);
            } catch (RemoteException e) {
                mAutoRestore.setChecked(!curState);
            }
        } else if (preference == mLockDeviceToCyanogenAccount) {
            if (mLockDeviceToCyanogenAccount.isChecked()) {
                // wants to opt in.
                if (hasLoggedInCyanogenAccount(getActivity())) {
                    confirmCyanogenCredentials(getActivity(), true, null);
                } else {
                    // no account, need to create one!
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                            .setMessage(R.string.lock_to_cyanogen_create_account_msg)
                            .setPositiveButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // create new account
                                    AccountManager accountManager = (AccountManager)
                                            getActivity().getSystemService(Context.ACCOUNT_SERVICE);
                                    Bundle opts = new Bundle();
                                    opts.putBoolean(EXTRA_CREATE_ACCOUNT, true);
                                    opts.putInt(EXTRA_SETCKS, 1);

                                    accountManager.addAccount(ACCOUNT_TYPE_CYANOGEN, null, null,
                                            opts, getActivity(), null, null);
                                }
                            });
                    builder.create().show();
                }
            } else {
                //  opt out
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                        .setMessage(R.string.lock_to_cyanogen_disable_msg)
                        .setNegativeButton(android.R.string.no,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        updateToggles();
                                    }
                                })
                        .setPositiveButton(android.R.string.yes,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        confirmCyanogenCredentials(getActivity(), false,
                                                null);
                                    }
                                });
                builder.create().show();
            }
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }


    private void showEraseBackupDialog() {
        mBackup.setChecked(true);

        mDialogType = DIALOG_ERASE_BACKUP;
        CharSequence msg = getResources().getText(R.string.backup_erase_dialog_message);
        // TODO: DialogFragment?
        mConfirmDialog = new AlertDialog.Builder(getActivity()).setMessage(msg)
                .setTitle(R.string.backup_erase_dialog_title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, this)
                .show();
    }

    /*
     * Creates toggles for each available location provider
     */
    private void updateToggles() {
        ContentResolver res = getContentResolver();

        boolean backupEnabled = false;
        Intent configIntent = null;
        String configSummary = null;
        try {
            backupEnabled = mBackupManager.isBackupEnabled();
            String transport = mBackupManager.getCurrentTransport();
            configIntent = mBackupManager.getConfigurationIntent(transport);
            configSummary = mBackupManager.getDestinationString(transport);
        } catch (RemoteException e) {
            // leave it 'false' and disable the UI; there's no backup manager
            mBackup.setEnabled(false);
        }
        mBackup.setChecked(backupEnabled);

        mAutoRestore.setChecked(Settings.Secure.getInt(res,
                Settings.Secure.BACKUP_AUTO_RESTORE, 1) == 1);
        mAutoRestore.setEnabled(backupEnabled);

        final boolean configureEnabled = (configIntent != null) && backupEnabled;
        mConfigure.setEnabled(configureEnabled);
        mConfigure.setIntent(configIntent);
        setConfigureSummary(configSummary);

        if (mLockDeviceToCyanogenAccount != null) {
            mLockDeviceToCyanogenAccount.setChecked(isDeviceLocked());
        }
    }

    private void setConfigureSummary(String summary) {
        if (summary != null) {
            mConfigure.setSummary(summary);
        } else {
            mConfigure.setSummary(R.string.backup_configure_account_default_summary);
        }
    }

    private void updateConfigureSummary() {
        try {
            String transport = mBackupManager.getCurrentTransport();
            String summary = mBackupManager.getDestinationString(transport);
            setConfigureSummary(summary);
        } catch (RemoteException e) {
            // Not much we can do here
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            //updateProviders();
            if (mDialogType == DIALOG_ERASE_BACKUP) {
                setBackupEnabled(false);
                updateConfigureSummary();
            }
        }
        mDialogType = 0;
    }

    /**
     * Informs the BackupManager of a change in backup state - if backup is disabled,
     * the data on the server will be erased.
     * @param enable whether to enable backup
     */
    private void setBackupEnabled(boolean enable) {
        if (mBackupManager != null) {
            try {
                mBackupManager.setBackupEnabled(enable);
            } catch (RemoteException e) {
                mBackup.setChecked(!enable);
                mAutoRestore.setEnabled(!enable);
                return;
            }
        }
        mBackup.setChecked(enable);
        mAutoRestore.setEnabled(enable);
        mConfigure.setEnabled(enable);
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_url_backup_reset;
    }
}
