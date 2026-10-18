package com.termux.app.fragments.settings;

import android.content.Context;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.style.StylePicker;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

@Keep
public class TermuxPreferencesFragment extends PreferenceFragmentCompat {

    private ActivityResultLauncher<String[]> mFontFileLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFontFileLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            Context context = getContext();
            if (uri != null && context != null) StylePicker.installFontFromUri(context, uri);
        });
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_preferences, rootKey);

        Preference colorScheme = findPreference("style_color_scheme");
        if (colorScheme != null) {
            colorScheme.setOnPreferenceClickListener(preference -> {
                StylePicker.showColorSchemePicker(requireContext(), null);
                return true;
            });
        }

        Preference font = findPreference("style_font");
        if (font != null) {
            font.setOnPreferenceClickListener(preference -> {
                StylePicker.showFontPicker(requireContext(), null, () -> mFontFileLauncher.launch(new String[]{"*/*"}));
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

    public static synchronized TermuxPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TermuxPreferencesDataStore(context);
        }
        return mInstance;
    }

}
