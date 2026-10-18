package com.termux.app.style;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Built in picker for the terminal color scheme and font, so that the Termux:Styling plugin is not
 * needed. The choice is stored in the same {@code ~/.termux/colors.properties} and
 * {@code ~/.termux/font.ttf} files that the plugin uses, so both keep working together.
 */
public final class StylePicker {

    private static final String LOG_TAG = "StylePicker";

    /** Used to show a choice live in the terminal before it is applied. */
    public interface Previewer {
        /** Show the colors, {@code null} for the default colors. */
        void previewColors(@Nullable Properties colors);

        /** Show the font, {@code null} for the default font. */
        void previewFont(@Nullable Typeface typeface);

        /** Go back to the colors and font that are stored on disk. */
        void revert();
    }

    private StylePicker() {}



    /** Show the dialog to pick a color scheme. The {@code previewer} may be {@code null}. */
    public static void showColorSchemePicker(@NonNull Context context, @Nullable Previewer previewer) {
        final List<ColorSchemes.Scheme> schemes = new ArrayList<>(ColorSchemes.ALL);
        // The first entry is the default of the app, which is stored by removing the colors file.
        final int total = schemes.size() + 1;
        final int[] selected = {-1};
        final boolean[] applied = {false};

        final float density = context.getResources().getDisplayMetrics().density;

        ArrayAdapter<Object> adapter = new ArrayAdapter<Object>(context, 0, new Object[total]) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                ColorSchemes.Scheme scheme = position == 0 ? null : schemes.get(position - 1);
                return createColorSchemeRow(parent.getContext(), scheme, density);
            }
        };

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.style_color_scheme_title)
            .setSingleChoiceItems(adapter, -1, null)
            .setPositiveButton(R.string.style_apply, (d, which) -> {
                if (selected[0] < 0) return;
                applied[0] = true;
                saveColorScheme(context, selected[0] == 0 ? null : schemes.get(selected[0] - 1));
            })
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener(d -> {
                if (!applied[0] && previewer != null) previewer.revert();
            })
            .show();

        // Do not allow to apply before something was picked.
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            dialog.getListView().setItemChecked(position, true);
            selected[0] = position;
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            if (previewer != null) {
                previewer.previewColors(position == 0 ? null : schemes.get(position - 1).toProperties());
            }
        });
    }

    private static View createColorSchemeRow(Context context, @Nullable ColorSchemes.Scheme scheme, float density) {
        int pad = Math.round(12 * density);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(pad, pad, pad, pad);
        row.setBackground(createSelectableBackground(context, density));

        TextView name = new TextView(context);
        name.setText(scheme == null ? context.getString(R.string.style_color_scheme_default) : scheme.name);
        name.setTextSize(16);
        name.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        row.addView(name);

        if (scheme != null) {
            // The terminal background with a sample line, and the 8 normal ansi colors below it.
            TextView sample = new TextView(context);
            sample.setTypeface(Typeface.MONOSPACE);
            sample.setTextSize(13);
            sample.setText("~ $ ls --color");
            sample.setTextColor(Color.parseColor(scheme.foreground));
            sample.setBackgroundColor(Color.parseColor(scheme.background));
            sample.setPadding(pad, Math.round(6 * density), pad, Math.round(6 * density));
            LinearLayout.LayoutParams sampleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sampleParams.topMargin = Math.round(8 * density);
            row.addView(sample, sampleParams);

            LinearLayout swatches = new LinearLayout(context);
            swatches.setOrientation(LinearLayout.HORIZONTAL);
            swatches.setBackgroundColor(Color.parseColor(scheme.background));
            swatches.setGravity(Gravity.CENTER_VERTICAL);
            swatches.setPadding(pad, 0, pad, Math.round(6 * density));
            for (int i = 1; i < 7; i++) {
                View swatch = new View(context);
                swatch.setBackgroundColor(Color.parseColor(scheme.ansi[i]));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Math.round(10 * density), 1f);
                params.rightMargin = Math.round(4 * density);
                swatches.addView(swatch, params);
            }
            row.addView(swatches, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        return row;
    }

    /** Rounded background that is highlighted when the row is checked, a non Checkable view in a ListView is set activated. */
    private static StateListDrawable createSelectableBackground(Context context, float density) {
        int color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondaryContainer, Color.LTGRAY);
        GradientDrawable activated = new GradientDrawable();
        activated.setColor(color);
        activated.setCornerRadius(16 * density);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_activated}, activated);
        states.addState(new int[]{}, new GradientDrawable());
        return states;
    }

    /** Store the color scheme, {@code null} for the default colors, and tell the terminal to reload it. */
    public static void saveColorScheme(@NonNull Context context, @Nullable ColorSchemes.Scheme scheme) {
        File colorsFile = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
        try {
            if (scheme == null) {
                if (colorsFile.exists() && !colorsFile.delete()) {
                    throw new IOException("Could not delete " + colorsFile.getAbsolutePath());
                }
            } else {
                File parent = colorsFile.getParentFile();
                if (parent != null) parent.mkdirs();
                try (OutputStream out = new FileOutputStream(colorsFile)) {
                    scheme.toProperties().store(out, "Termux color scheme: " + scheme.name);
                }
            }
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to save the color scheme", e);
            Logger.showToast(context, context.getString(R.string.style_save_failed), true);
            return;
        }

        TermuxActivity.updateTermuxActivityStyling(context, false);
    }



    private static class FontEntry {
        /** {@code null} for the default font. */
        @Nullable final File file;
        @Nullable final Typeface typeface;
        final String label;

        FontEntry(String label, @Nullable File file, @Nullable Typeface typeface) {
            this.label = label;
            this.file = file;
            this.typeface = typeface;
        }
    }

    /**
     * Show the dialog to pick a font.
     *
     * @param previewer May be {@code null}.
     * @param chooseFile If not {@code null} an entry is added to pick a font file, and it is run when
     *                   that entry is clicked.
     */
    public static void showFontPicker(@NonNull Context context, @Nullable Previewer previewer, @Nullable Runnable chooseFile) {
        final List<FontEntry> fonts = findFonts(context);
        final int total = fonts.size() + (chooseFile != null ? 1 : 0);
        final int[] selected = {-1};
        final boolean[] applied = {false};
        final float density = context.getResources().getDisplayMetrics().density;

        ArrayAdapter<Object> adapter = new ArrayAdapter<Object>(context, 0, new Object[total]) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                Context c = parent.getContext();
                int pad = Math.round(12 * density);

                LinearLayout row = new LinearLayout(c);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(pad, pad, pad, pad);
                row.setBackground(createSelectableBackground(c, density));

                int onSurface = MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);

                TextView name = new TextView(c);
                name.setTextSize(16);
                name.setTextColor(onSurface);
                row.addView(name);

                if (position < fonts.size()) {
                    FontEntry entry = fonts.get(position);
                    name.setText(entry.label);

                    TextView sample = new TextView(c);
                    sample.setTypeface(entry.typeface != null ? entry.typeface : Typeface.MONOSPACE);
                    sample.setTextSize(13);
                    sample.setTextColor(onSurface);
                    sample.setText("0O 1lI {}[]() => ~/.bashrc");
                    row.addView(sample);
                } else {
                    name.setText(R.string.style_font_choose_file);
                }
                return row;
            }
        };

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.style_font_title)
            .setSingleChoiceItems(adapter, -1, null)
            .setPositiveButton(R.string.style_apply, (d, which) -> {
                if (selected[0] < 0 || selected[0] >= fonts.size()) return;
                applied[0] = true;
                saveFont(context, fonts.get(selected[0]).file);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener(d -> {
                if (!applied[0] && previewer != null) previewer.revert();
            })
            .show();

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            if (position >= fonts.size()) {
                // The "choose file" entry.
                dialog.dismiss();
                if (chooseFile != null) chooseFile.run();
                return;
            }
            dialog.getListView().setItemChecked(position, true);
            selected[0] = position;
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            if (previewer != null) previewer.previewFont(fonts.get(position).typeface);
        });
    }

    /** The default font and the monospace fonts that are installed on the device. */
    private static List<FontEntry> findFonts(Context context) {
        List<FontEntry> fonts = new ArrayList<>();
        fonts.add(new FontEntry(context.getString(R.string.style_font_default), null, Typeface.MONOSPACE));

        File[] files = new File("/system/fonts").listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File file : files) {
                String name = file.getName();
                String lower = name.toLowerCase();
                if (!(lower.endsWith(".ttf") || lower.endsWith(".otf"))) continue;
                if (!(lower.contains("mono") || lower.contains("code"))) continue;
                if (lower.contains("bold") || lower.contains("italic") || lower.contains("oblique")) continue;

                try {
                    Typeface typeface = Typeface.createFromFile(file);
                    String label = name.substring(0, name.lastIndexOf('.')).replace('-', ' ').replace('_', ' ');
                    fonts.add(new FontEntry(label, file, typeface));
                } catch (RuntimeException e) {
                    // Not a font that can be loaded.
                }
            }
        }
        return fonts;
    }

    /** Store the font, {@code null} for the default font, and tell the terminal to reload it. */
    public static void saveFont(@NonNull Context context, @Nullable File source) {
        File fontFile = TermuxConstants.TERMUX_FONT_FILE;
        try {
            if (source == null) {
                if (fontFile.exists() && !fontFile.delete()) {
                    throw new IOException("Could not delete " + fontFile.getAbsolutePath());
                }
            } else {
                try (InputStream in = new FileInputStream(source)) {
                    copyFont(in, fontFile);
                }
            }
        } catch (IOException | RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to save the font", e);
            Logger.showToast(context, context.getString(R.string.style_save_failed), true);
            return;
        }

        TermuxActivity.updateTermuxActivityStyling(context, false);
    }

    /** Install the font file the user picked with the file picker. */
    public static void installFontFromUri(@NonNull Context context, @NonNull Uri uri) {
        File fontFile = TermuxConstants.TERMUX_FONT_FILE;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Could not open " + uri);
            copyFont(in, fontFile);
        } catch (IOException | RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to install the font", e);
            Logger.showToast(context, context.getString(R.string.style_font_invalid), true);
            return;
        }

        TermuxActivity.updateTermuxActivityStyling(context, false);
    }

    /** Copy the font to a temporary file and only replace the font file if it can be loaded, so a bad file does not break the terminal. */
    private static void copyFont(InputStream in, File fontFile) throws IOException {
        File parent = fontFile.getParentFile();
        if (parent != null) parent.mkdirs();

        File temp = new File(fontFile.getPath() + ".tmp");
        try {
            try (OutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }

            // Throws a RuntimeException if the file is not a font.
            Typeface.createFromFile(temp);

            if (fontFile.exists() && !fontFile.delete()) {
                throw new IOException("Could not replace " + fontFile.getAbsolutePath());
            }
            if (!temp.renameTo(fontFile)) {
                throw new IOException("Could not move the font to " + fontFile.getAbsolutePath());
            }
        } finally {
            if (temp.exists()) temp.delete();
        }
    }

}
