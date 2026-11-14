package com.termux.app.fragments.settings;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.extrakeys.ExtraKeysEditor;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Keep
public class TermuxPreferencesFragment extends PreferenceFragmentCompat {

    private static final String LOG_TAG = "TermuxPreferences";

    /** File and restore work runs here, never on the UI thread. */
    private final ExecutorService mIoExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String> mBackupPicker = registerForActivityResult(
        new ActivityResultContracts.CreateDocument("application/zip"),
        uri -> {
            if (uri != null) backupTo(uri);
        });

    private final ActivityResultLauncher<String[]> mRestorePicker = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(),
        uri -> {
            if (uri != null) confirmRestore(uri);
        });

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_preferences, rootKey);

        Preference extraKeys = findPreference("extra_keys_editor");
        if (extraKeys != null) {
            extraKeys.setOnPreferenceClickListener(preference -> {
                ExtraKeysEditor.show(requireContext());
                return true;
            });
        }

        Preference backup = findPreference("backup_settings");
        if (backup != null) {
            backup.setOnPreferenceClickListener(preference -> {
                String name = "termux-settings-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".zip";
                mBackupPicker.launch(name);
                return true;
            });
        }

        Preference restore = findPreference("restore_settings");
        if (restore != null) {
            restore.setOnPreferenceClickListener(preference -> {
                mRestorePicker.launch(new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
                return true;
            });
        }
    }

    @Override
    public void onDestroy() {
        mIoExecutor.shutdown();
        super.onDestroy();
    }

    private void backupTo(Uri uri) {
        Context appContext = requireContext().getApplicationContext();
        mIoExecutor.execute(() -> {
            try {
                int count = SettingsBackup.export(appContext, uri);
                toast(appContext, count > 0 ? R.string.backup_done : R.string.backup_nothing);
            } catch (IOException e) {
                Log.w(LOG_TAG, "Backup failed", e);
                toast(appContext, R.string.backup_failed);
            }
        });
    }

    private void confirmRestore(Uri uri) {
        Context appContext = requireContext().getApplicationContext();
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.restore_confirm_title)
            .setMessage(R.string.restore_confirm_message)
            .setPositiveButton(R.string.restore_action, (dialog, which) -> mIoExecutor.execute(() -> {
                try {
                    SettingsBackup.restore(appContext, uri);
                    toast(appContext, R.string.restore_done);
                    // Reload the colors, font and properties in Termux, the same way Termux:Styling does.
                    TermuxActivity.updateTermuxActivityStyling(appContext, true);
                } catch (IOException e) {
                    Log.w(LOG_TAG, "Restore failed", e);
                    toast(appContext, R.string.restore_failed);
                }
            }))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private static void toast(Context context, int messageRes) {
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show());
    }

}

class TermuxPreferencesDataStore extends PreferenceDataStore {

    private final Context mContext;
    private final TermuxAppSharedPreferences mPreferences;

    private static TermuxPreferencesDataStore mInstance;

    private TermuxPreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAppSharedPreferences.build(context, true);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (mPreferences == null || key == null) return;
        if ("show_session_tabs".equals(key)) mPreferences.setShowSessionTabs(value);
        if ("auto_hide_single_session_tab".equals(key)) mPreferences.setAutoHideSingleSessionTab(value);
        if ("terminal_material_background".equals(key)) mPreferences.setTerminalUseMaterialBackground(value);
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (mPreferences == null || key == null) return defValue;
        if ("show_session_tabs".equals(key)) return mPreferences.shouldShowSessionTabs();
        if ("auto_hide_single_session_tab".equals(key)) return mPreferences.shouldAutoHideSingleSessionTab();
        if ("terminal_material_background".equals(key)) return mPreferences.shouldTerminalUseMaterialBackground();
        return defValue;
    }

    public static synchronized TermuxPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TermuxPreferencesDataStore(context);
        }
        return mInstance;
    }

}
