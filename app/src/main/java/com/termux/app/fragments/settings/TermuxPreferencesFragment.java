package com.termux.app.fragments.settings;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.extrakeys.ExtraKeysEditor;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

@Keep
public class TermuxPreferencesFragment extends PreferenceFragmentCompat {

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
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (mPreferences == null || key == null) return defValue;
        if ("show_session_tabs".equals(key)) return mPreferences.shouldShowSessionTabs();
        return defValue;
    }

    public static synchronized TermuxPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TermuxPreferencesDataStore(context);
        }
        return mInstance;
    }

}
