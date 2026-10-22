package com.termux.app.extrakeys;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;
import com.termux.shared.settings.properties.SharedProperties;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A visual editor for the extra keys, so that the {@code extra-keys} property of
 * {@code ~/.termux/termux.properties} does not have to be edited by hand. It also has the height
 * of the extra keys, whether their text is all caps and whether they give haptic feedback.
 *
 * Keys that have a popup or are a macro, like {@code {key: 'DRAWER', popup: 'PASTE'}}, are kept
 * as they are and can be moved and removed, but have to be edited in the properties file.
 */
public final class ExtraKeysEditor {

    private static final String LOG_TAG = "ExtraKeysEditor";

    /** The keys that can be picked as a shortcut in the key dialog. */
    private static final String[] PRESET_KEYS = {
        "ESC", "TAB", "CTRL", "ALT", "SHIFT", "FN", "HOME", "END", "PGUP", "PGDN", "UP", "DOWN", "LEFT", "RIGHT",
        "BKSP", "DEL", "ENTER", "SPACE", "INS", "KEYBOARD", "DRAWER", "PASTE", "SCROLL",
        "-", "/", "|", "~", "\\", "_", ":", ";", "*", "$", "&", "'", "\"", "`", "<", ">", "(", ")", "[", "]", "{", "}",
        "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"
    };

    private static final int HEIGHT_STEPS = 26; // 0.4 to 3.0 with a step of 0.1

    private final Context mContext;
    /** Each key is a {@link String} or a {@link JSONObject} that is not edited. */
    private final List<List<Object>> mRows;
    private final String mInitialKeys;
    private final boolean mInitialAllCaps;
    private final float mInitialHeight;

    private boolean mAllCaps;
    private int mHeightProgress;
    private boolean mHaptic;
    private final boolean mInitialHaptic;

    private LinearLayout mRowsContainer;
    private final float mDensity;

    private ExtraKeysEditor(Context context, List<List<Object>> rows, boolean allCaps, float height, boolean haptic) {
        mContext = context;
        mRows = rows;
        mInitialKeys = serialize(rows);
        mInitialAllCaps = mAllCaps = allCaps;
        mInitialHeight = height;
        mHeightProgress = Math.round((height - TermuxPropertyConstants.IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR_MIN) * 10);
        mHeightProgress = Math.max(0, Math.min(HEIGHT_STEPS, mHeightProgress));
        mInitialHaptic = mHaptic = haptic;
        mDensity = context.getResources().getDisplayMetrics().density;
    }

    /** Show the editor. */
    public static void show(@NonNull Context context) {
        Properties props = readProperties();

        List<List<Object>> rows;
        String raw = props.getProperty(TermuxPropertyConstants.KEY_EXTRA_KEYS);
        if (raw == null || raw.trim().isEmpty()) raw = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS;
        try {
            rows = parse(raw);
        } catch (JSONException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not parse the extra keys", e);
            Logger.showToast(context, context.getString(R.string.extra_keys_editor_parse_failed), true);
            return;
        }

        boolean allCaps = !"false".equalsIgnoreCase(props.getProperty(
            TermuxPropertyConstants.KEY_EXTRA_KEYS_TEXT_ALL_CAPS, "true").trim());

        float height = TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR;
        try {
            height = Float.parseFloat(props.getProperty(
                TermuxPropertyConstants.KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR, String.valueOf(height)).trim());
        } catch (NumberFormatException ignored) {
        }

        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        boolean haptic = preferences == null || preferences.isExtraKeysHapticFeedbackEnabled();

        new ExtraKeysEditor(context, rows, allCaps, height, haptic).showDialog();
    }



    private void showDialog() {
        ScrollView scrollView = new ScrollView(mContext);
        LinearLayout content = new LinearLayout(mContext);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        content.setPadding(pad, dp(8), pad, 0);
        scrollView.addView(content);

        TextView hint = new TextView(mContext);
        hint.setText(R.string.extra_keys_editor_hint);
        hint.setTextColor(MaterialColors.getColor(mContext, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xff666666));
        hint.setTextSize(13);
        content.addView(hint);

        mRowsContainer = new LinearLayout(mContext);
        mRowsContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(mRowsContainer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        refreshRows();

        MaterialButton addRow = new MaterialButton(mContext, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        addRow.setText(R.string.extra_keys_editor_add_row);
        addRow.setAllCaps(false);
        addRow.setOnClickListener(v -> {
            mRows.add(new ArrayList<>());
            refreshRows();
        });
        LinearLayout.LayoutParams addRowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addRowParams.topMargin = dp(8);
        content.addView(addRow, addRowParams);

        content.addView(createSwitch(R.string.extra_keys_editor_all_caps, mAllCaps, checked -> mAllCaps = checked));
        content.addView(createSwitch(R.string.extra_keys_editor_haptic, mHaptic, checked -> mHaptic = checked));

        TextView heightLabel = new TextView(mContext);
        heightLabel.setTextSize(15);
        heightLabel.setTextColor(MaterialColors.getColor(mContext, com.google.android.material.R.attr.colorOnSurface, 0xff000000));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(16);
        content.addView(heightLabel, labelParams);

        SeekBar seekBar = new SeekBar(mContext);
        seekBar.setMax(HEIGHT_STEPS);
        seekBar.setProgress(mHeightProgress);
        updateHeightLabel(heightLabel);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                mHeightProgress = progress;
                updateHeightLabel(heightLabel);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        content.addView(seekBar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new MaterialAlertDialogBuilder(mContext)
            .setTitle(R.string.extra_keys_editor_title)
            .setView(scrollView)
            .setPositiveButton(R.string.extra_keys_editor_save, (dialog, which) -> save())
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.extra_keys_editor_reset, (dialog, which) -> {
                try {
                    mRows.clear();
                    mRows.addAll(parse(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS));
                    save();
                } catch (JSONException e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Could not parse the default extra keys", e);
                }
            })
            .show();
    }

    private interface Toggle {
        void onChanged(boolean checked);
    }

    private View createSwitch(int label, boolean checked, Toggle toggle) {
        MaterialSwitch view = new MaterialSwitch(mContext);
        view.setText(label);
        view.setChecked(checked);
        view.setOnCheckedChangeListener((button, isChecked) -> toggle.onChanged(isChecked));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        view.setLayoutParams(params);
        return view;
    }

    private void updateHeightLabel(TextView label) {
        label.setText(mContext.getString(R.string.extra_keys_editor_height, Math.round(heightFactor() * 100)));
    }

    private float heightFactor() {
        return TermuxPropertyConstants.IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR_MIN + mHeightProgress / 10f;
    }

    /** Rebuild the rows of key buttons from {@link #mRows}. */
    private void refreshRows() {
        mRowsContainer.removeAllViews();

        for (int r = 0; r < mRows.size(); r++) {
            final int rowIndex = r;
            final List<Object> row = mRows.get(r);

            LinearLayout line = new LinearLayout(mContext);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);

            HorizontalScrollView scroll = new HorizontalScrollView(mContext);
            scroll.setHorizontalScrollBarEnabled(false);
            LinearLayout keys = new LinearLayout(mContext);
            keys.setOrientation(LinearLayout.HORIZONTAL);
            scroll.addView(keys);

            for (int k = 0; k < row.size(); k++) {
                final int keyIndex = k;
                MaterialButton key = createKeyButton(labelOf(row.get(k)), false);
                key.setOnClickListener(v -> showKeyDialog(row, keyIndex));
                keys.addView(key);
            }

            MaterialButton add = createKeyButton("+", true);
            add.setContentDescription(mContext.getString(R.string.extra_keys_editor_add_key));
            add.setOnClickListener(v -> showKeyDialog(row, -1));
            keys.addView(add);

            line.addView(scroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            MaterialButton remove = new MaterialButton(mContext, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            remove.setText("✕");
            remove.setMinWidth(0);
            remove.setMinimumWidth(0);
            remove.setContentDescription(mContext.getString(R.string.extra_keys_editor_remove_row));
            remove.setOnClickListener(v -> {
                mRows.remove(rowIndex);
                refreshRows();
            });
            line.addView(remove, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(8);
            mRowsContainer.addView(line, params);
        }
    }

    private MaterialButton createKeyButton(String text, boolean tonal) {
        MaterialButton button = new MaterialButton(mContext, null, tonal
            ? com.google.android.material.R.attr.materialButtonStyle
            : com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(dp(48));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        params.rightMargin = dp(6);
        button.setLayoutParams(params);
        return button;
    }

    /** Show the dialog to add a key to the {@code row}, or to change the key at {@code index} if it is not {@code -1}. */
    private void showKeyDialog(List<Object> row, int index) {
        final boolean isNew = index < 0;
        final Object existing = isNew ? null : row.get(index);
        final boolean isComplex = existing instanceof JSONObject;

        LinearLayout content = new LinearLayout(mContext);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);

        final EditText input = new EditText(mContext);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(R.string.extra_keys_editor_key_hint);
        if (!isNew) input.setText(labelOf(existing));
        content.addView(input, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (isComplex) {
            input.setEnabled(false);
            TextView note = new TextView(mContext);
            note.setText(R.string.extra_keys_editor_complex_key);
            note.setTextSize(13);
            LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            noteParams.topMargin = dp(8);
            content.addView(note, noteParams);
        } else {
            HorizontalScrollView presetsScroll = new HorizontalScrollView(mContext);
            presetsScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout presets = new LinearLayout(mContext);
            presets.setOrientation(LinearLayout.HORIZONTAL);
            presetsScroll.addView(presets);
            for (String preset : PRESET_KEYS) {
                MaterialButton chip = createKeyButton(preset, false);
                chip.setOnClickListener(v -> {
                    input.setText(preset);
                    input.setSelection(preset.length());
                });
                presets.addView(chip);
            }
            LinearLayout.LayoutParams presetsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            presetsParams.topMargin = dp(8);
            content.addView(presetsScroll, presetsParams);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(mContext)
            .setTitle(isNew ? R.string.extra_keys_editor_add_key : R.string.extra_keys_editor_edit_key)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null);

        if (!isComplex) {
            builder.setPositiveButton(isNew ? R.string.extra_keys_editor_add : R.string.extra_keys_editor_set, (dialog, which) -> {
                String text = input.getText().toString();
                if (text.isEmpty()) return;
                if (isNew) row.add(text); else row.set(index, text);
                refreshRows();
            });
        }

        if (!isNew) {
            builder.setNeutralButton(R.string.extra_keys_editor_remove, (dialog, which) -> {
                row.remove(index);
                refreshRows();
            });

            // Moving a key is done with extra buttons, since an AlertDialog only has 3 buttons.
            LinearLayout move = new LinearLayout(mContext);
            move.setOrientation(LinearLayout.HORIZONTAL);
            MaterialButton left = createKeyButton("←", false);
            MaterialButton right = createKeyButton("→", false);
            left.setEnabled(index > 0);
            right.setEnabled(index < row.size() - 1);
            move.addView(left);
            move.addView(right);
            LinearLayout.LayoutParams moveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            moveParams.topMargin = dp(8);
            content.addView(move, moveParams);

            final androidx.appcompat.app.AlertDialog[] holder = new androidx.appcompat.app.AlertDialog[1];
            left.setOnClickListener(v -> {
                Collections.swap(row, index, index - 1);
                refreshRows();
                if (holder[0] != null) holder[0].dismiss();
            });
            right.setOnClickListener(v -> {
                Collections.swap(row, index, index + 1);
                refreshRows();
                if (holder[0] != null) holder[0].dismiss();
            });
            holder[0] = builder.show();
            return;
        }

        builder.show();
    }

    /** Write the changes to the properties file and reload the terminal. */
    private void save() {
        Map<String, String> changes = new LinkedHashMap<>();

        String keys = serialize(mRows);
        if (!keys.equals(mInitialKeys)) {
            changes.put(TermuxPropertyConstants.KEY_EXTRA_KEYS, keys);
        }
        if (mAllCaps != mInitialAllCaps) {
            changes.put(TermuxPropertyConstants.KEY_EXTRA_KEYS_TEXT_ALL_CAPS, String.valueOf(mAllCaps));
        }
        float height = heightFactor();
        if (Math.abs(height - mInitialHeight) > 0.01f) {
            changes.put(TermuxPropertyConstants.KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR,
                String.format(Locale.US, "%.1f", height));
        }

        if (mHaptic != mInitialHaptic) {
            TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(mContext, true);
            if (preferences != null) preferences.setExtraKeysHapticFeedbackEnabled(mHaptic);
        }

        if (!changes.isEmpty()) {
            try {
                writeProperties(getPropertiesFile(), changes);
            } catch (IOException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to save the properties", e);
                Logger.showToast(mContext, mContext.getString(R.string.extra_keys_editor_save_failed), true);
                return;
            }
        }

        // The extra keys are only read when the activity is created, so recreate it.
        TermuxActivity.updateTermuxActivityStyling(mContext, true);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, mContext.getResources().getDisplayMetrics()));
    }



    /** Get the text shown on the button of a key. */
    static String labelOf(@Nullable Object key) {
        if (key instanceof JSONObject) {
            JSONObject object = (JSONObject) key;
            String label = object.optString("display", null);
            if (label == null) label = object.optString("key", null);
            if (label == null) label = object.optString("macro", "?");
            return label + " *";
        }
        return String.valueOf(key);
    }

    /** Parse the value of the {@code extra-keys} property into rows of keys. */
    static List<List<Object>> parse(String value) throws JSONException {
        JSONArray array = new JSONArray(value);
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONArray jsonRow = array.getJSONArray(i);
            List<Object> row = new ArrayList<>();
            for (int j = 0; j < jsonRow.length(); j++) {
                Object key = jsonRow.get(j);
                row.add(key instanceof JSONObject ? key : String.valueOf(key));
            }
            rows.add(row);
        }
        return rows;
    }

    /** Get the value for the {@code extra-keys} property, like {@code [['ESC','TAB'],['CTRL']]}. */
    static String serialize(List<List<Object>> rows) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) builder.append(", ");
            builder.append('[');
            List<Object> row = rows.get(i);
            for (int j = 0; j < row.size(); j++) {
                if (j > 0) builder.append(',');
                appendValue(builder, row.get(j));
            }
            builder.append(']');
        }
        return builder.append(']').toString();
    }

    private static void appendValue(StringBuilder builder, Object value) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            builder.append('{');
            Iterator<String> names = object.keys();
            boolean first = true;
            while (names.hasNext()) {
                String name = names.next();
                if (!first) builder.append(", ");
                first = false;
                builder.append(name).append(": ");
                appendValue(builder, object.opt(name));
            }
            builder.append('}');
        } else {
            builder.append('\'')
                .append(String.valueOf(value).replace("\\", "\\\\").replace("'", "\\'"))
                .append('\'');
        }
    }



    /** Get the properties file that is used by the app, or the primary one if there is none yet. */
    private static File getPropertiesFile() {
        File file = SharedProperties.getPropertiesFileFromList(TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST, LOG_TAG);
        return file != null ? file : TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE;
    }

    private static Properties readProperties() {
        Properties props = new Properties();
        File file = getPropertiesFile();
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read the properties", e);
            }
        }
        return props;
    }

    /**
     * Set the values of the keys in the properties file and leave the rest of the file as it is, including
     * comments. The file is read and written as ISO-8859-1 like {@link Properties} does, so that no
     * other bytes are changed. Values are escaped as the format needs.
     */
    static void writeProperties(File file, Map<String, String> values) throws IOException {
        List<String> lines = new ArrayList<>();
        if (file.isFile()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.ISO_8859_1))) {
                String line;
                while ((line = reader.readLine()) != null) lines.add(line);
            }
        }

        // A key can also be separated from its value by whitespace, like "extra-keys [...]".
        Pattern keyPattern = Pattern.compile("^\\s*([^#!\\s=:][^\\s=:]*)\\s*(?:[=:]|\\s)");
        List<String> output = new ArrayList<>();
        List<String> written = new ArrayList<>();
        // True while the old value of a replaced key goes on over several lines (a trailing backslash).
        boolean inReplacedValue = false;
        for (String line : lines) {
            if (inReplacedValue) {
                // Drop the rest of the old value, the new value replaces all of it.
                inReplacedValue = endsWithContinuation(line);
                continue;
            }
            Matcher matcher = keyPattern.matcher(line);
            if (matcher.find() && values.containsKey(matcher.group(1))) {
                String key = matcher.group(1);
                // Only keep the first line of a key, the last one would win when loaded.
                if (!written.contains(key)) {
                    output.add(key + " = " + escapeValue(values.get(key)));
                    written.add(key);
                }
                inReplacedValue = endsWithContinuation(line);
            } else {
                output.add(line);
            }
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!written.contains(entry.getKey())) {
                output.add(entry.getKey() + " = " + escapeValue(entry.getValue()));
            }
        }

        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.ISO_8859_1)) {
            for (String line : output) writer.write(line + "\n");
        }
    }

    /** Whether the line ends with an odd number of backslashes, so the value goes on in the next line. */
    private static boolean endsWithContinuation(String line) {
        int count = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) count++;
        return count % 2 == 1;
    }

    private static String escapeValue(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') builder.append("\\\\");
            else if (c > 126 || c < 32) builder.append(String.format(Locale.US, "\\u%04x", (int) c));
            else builder.append(c);
        }
        return builder.toString();
    }

}
