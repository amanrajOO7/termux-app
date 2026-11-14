package com.termux.app.fragments.settings;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backs up and restores the color scheme, the font and the Termux properties as one zip file.
 * Only the four known entries are read or written, so a backup can't change any other file.
 */
public final class SettingsBackup {

    static final String ENTRY_COLORS = "colors.properties";
    static final String ENTRY_FONT = "font.ttf";
    static final String ENTRY_STYLE = "style.properties";
    static final String ENTRY_PROPERTIES = "termux.properties";

    private static final List<String> ENTRIES =
        Arrays.asList(ENTRY_COLORS, ENTRY_FONT, ENTRY_STYLE, ENTRY_PROPERTIES);

    /** The largest entry accepted from a backup. Fonts are the only large ones. */
    private static final long MAX_ENTRY_BYTES = 32L * 1024 * 1024;

    private SettingsBackup() {
    }

    /** Where a backup entry is restored to, or null if the name is not one of the known entries. */
    private static File destinationFor(String entryName) {
        switch (entryName) {
            case ENTRY_COLORS:
                return TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
            case ENTRY_FONT:
                return TermuxConstants.TERMUX_FONT_FILE;
            case ENTRY_STYLE:
                return TermuxConstants.TERMUX_STYLE_PROPERTIES_FILE;
            case ENTRY_PROPERTIES:
                return TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE;
            default:
                return null;
        }
    }

    /** The file to back up for an entry, or null if it does not exist. Properties can be in either location. */
    private static File sourceFor(String entryName) {
        if (ENTRY_PROPERTIES.equals(entryName)) {
            File[] candidates = {
                TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE,
                TermuxConstants.TERMUX_PROPERTIES_SECONDARY_FILE
            };
            for (File candidate : candidates) {
                if (candidate.isFile()) return candidate;
            }
            return null;
        }
        File file = destinationFor(entryName);
        return file != null && file.isFile() ? file : null;
    }

    /** Write the current settings to the zip file at [uri]. Returns how many entries were written. */
    public static int export(Context context, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        int written = 0;
        try (OutputStream output = resolver.openOutputStream(uri)) {
            if (output == null) throw new IOException("Cannot open the file for writing");
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                for (String entryName : ENTRIES) {
                    File source = sourceFor(entryName);
                    if (source == null) continue;
                    zip.putNextEntry(new ZipEntry(entryName));
                    try (InputStream input = new FileInputStream(source)) {
                        copy(input, zip, Long.MAX_VALUE);
                    }
                    zip.closeEntry();
                    written++;
                }
            }
        }
        return written;
    }

    /**
     * Restore the settings from the zip file at [uri]. Every entry is read, checked and staged first,
     * so a broken backup changes nothing. Returns how many entries were restored.
     */
    public static int restore(Context context, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        File cacheDir = context.getCacheDir();
        List<File> staged = new ArrayList<>();
        Map<String, File> stagedByName = new HashMap<>();

        try {
            try (InputStream raw = resolver.openInputStream(uri)) {
                if (raw == null) throw new IOException("Cannot open the file");
                try (ZipInputStream zip = new ZipInputStream(raw)) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        String name = entry.getName();
                        if (!entry.isDirectory() && ENTRIES.contains(name)) {
                            if (stagedByName.containsKey(name)) throw new IOException("Duplicate entry " + name);
                            File temp = File.createTempFile("restore-", ".tmp", cacheDir);
                            staged.add(temp);
                            stagedByName.put(name, temp);
                            try (OutputStream output = new FileOutputStream(temp)) {
                                copy(zip, output, MAX_ENTRY_BYTES);
                            }
                        }
                        zip.closeEntry();
                    }
                }
            }

            if (stagedByName.isEmpty()) throw new IOException("The file has no Termux settings");
            for (Map.Entry<String, File> item : stagedByName.entrySet()) {
                validate(item.getKey(), item.getValue());
            }
        } catch (IOException | RuntimeException e) {
            deleteAll(staged);
            throw e;
        }

        int restored = 0;
        try {
            for (Map.Entry<String, File> item : stagedByName.entrySet()) {
                File destination = destinationFor(item.getKey());
                File parent = destination.getParentFile();
                if (parent != null) parent.mkdirs();
                if (!item.getValue().renameTo(destination)) {
                    throw new IOException("Cannot write " + destination.getName());
                }
                restored++;
            }
        } finally {
            deleteAll(staged);
        }
        return restored;
    }

    /** Check that a staged entry will load in Termux. Throws IOException if it will not. */
    private static void validate(String name, File file) throws IOException {
        if (ENTRY_FONT.equals(name)) {
            try {
                if (Typeface.createFromFile(file.getPath()) == null) {
                    throw new IOException("Not a font file");
                }
            } catch (RuntimeException e) {
                throw new IOException("Not a font file", e);
            }
            return;
        }

        // The color scheme, the style and termux.properties are all key=value files.
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(file)) {
            properties.load(input);
        } catch (IllegalArgumentException e) {
            throw new IOException(name + " is not a valid properties file", e);
        }
    }

    private static void deleteAll(List<File> files) {
        for (File file : files) {
            file.delete();
        }
    }

    /** Copy at most [limit] bytes. Throws IOException if the input is longer. */
    private static void copy(InputStream input, OutputStream output, long limit) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IOException("File is too large");
            output.write(buffer, 0, read);
        }
    }
}
