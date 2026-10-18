package com.termux.app.style;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/** The built-in terminal color schemes that can be picked in {@link StylePicker}. */
public final class ColorSchemes {

    public static final class Scheme {
        public final String name;
        /** foreground, background, cursor and the 16 ansi colors, as {@code #rrggbb}. */
        public final String foreground, background, cursor;
        public final String[] ansi;

        Scheme(String name, String foreground, String background, String cursor, String... ansi) {
            if (ansi.length != 16) throw new IllegalArgumentException("A color scheme needs 16 ansi colors");
            this.name = name;
            this.foreground = foreground;
            this.background = background;
            this.cursor = cursor;
            this.ansi = ansi;
        }

        /** Get the scheme in the format of the {@code ~/.termux/colors.properties} file. */
        @NonNull
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("foreground", foreground);
            props.setProperty("background", background);
            props.setProperty("cursor", cursor);
            for (int i = 0; i < ansi.length; i++) {
                props.setProperty("color" + i, ansi[i]);
            }
            return props;
        }
    }

    public static final List<Scheme> ALL;

    static {
        List<Scheme> list = new ArrayList<>();

        list.add(new Scheme("Dracula", "#f8f8f2", "#282a36", "#f8f8f2",
            "#21222c", "#ff5555", "#50fa7b", "#f1fa8c", "#bd93f9", "#ff79c6", "#8be9fd", "#f8f8f2",
            "#6272a4", "#ff6e6e", "#69ff94", "#ffffa5", "#d6acff", "#ff92df", "#a4ffff", "#ffffff"));

        list.add(new Scheme("Nord", "#d8dee9", "#2e3440", "#d8dee9",
            "#3b4252", "#bf616a", "#a3be8c", "#ebcb8b", "#81a1c1", "#b48ead", "#88c0d0", "#e5e9f0",
            "#4c566a", "#bf616a", "#a3be8c", "#ebcb8b", "#81a1c1", "#b48ead", "#8fbcbb", "#eceff4"));

        list.add(new Scheme("Solarized Dark", "#839496", "#002b36", "#93a1a1",
            "#073642", "#dc322f", "#859900", "#b58900", "#268bd2", "#d33682", "#2aa198", "#eee8d5",
            "#002b36", "#cb4b16", "#586e75", "#657b83", "#839496", "#6c71c4", "#93a1a1", "#fdf6e3"));

        list.add(new Scheme("Solarized Light", "#657b83", "#fdf6e3", "#586e75",
            "#073642", "#dc322f", "#859900", "#b58900", "#268bd2", "#d33682", "#2aa198", "#eee8d5",
            "#002b36", "#cb4b16", "#586e75", "#657b83", "#839496", "#6c71c4", "#93a1a1", "#fdf6e3"));

        list.add(new Scheme("Gruvbox Dark", "#ebdbb2", "#282828", "#ebdbb2",
            "#282828", "#cc241d", "#98971a", "#d79921", "#458588", "#b16286", "#689d6a", "#a89984",
            "#928374", "#fb4934", "#b8bb26", "#fabd2f", "#83a598", "#d3869b", "#8ec07c", "#ebdbb2"));

        list.add(new Scheme("One Dark", "#abb2bf", "#282c34", "#528bff",
            "#282c34", "#e06c75", "#98c379", "#e5c07b", "#61afef", "#c678dd", "#56b6c2", "#abb2bf",
            "#5c6370", "#e06c75", "#98c379", "#e5c07b", "#61afef", "#c678dd", "#56b6c2", "#ffffff"));

        list.add(new Scheme("Tokyo Night", "#c0caf5", "#1a1b26", "#c0caf5",
            "#15161e", "#f7768e", "#9ece6a", "#e0af68", "#7aa2f7", "#bb9af7", "#7dcfff", "#a9b1d6",
            "#414868", "#f7768e", "#9ece6a", "#e0af68", "#7aa2f7", "#bb9af7", "#7dcfff", "#c0caf5"));

        list.add(new Scheme("Catppuccin Mocha", "#cdd6f4", "#1e1e2e", "#f5e0dc",
            "#45475a", "#f38ba8", "#a6e3a1", "#f9e2af", "#89b4fa", "#f5c2e7", "#94e2d5", "#bac2de",
            "#585b70", "#f38ba8", "#a6e3a1", "#f9e2af", "#89b4fa", "#f5c2e7", "#94e2d5", "#a6adc8"));

        list.add(new Scheme("Monokai", "#f8f8f2", "#272822", "#f8f8f0",
            "#272822", "#f92672", "#a6e22e", "#f4bf75", "#66d9ef", "#ae81ff", "#a1efe4", "#f8f8f2",
            "#75715e", "#f92672", "#a6e22e", "#f4bf75", "#66d9ef", "#ae81ff", "#a1efe4", "#f9f8f5"));

        ALL = Collections.unmodifiableList(list);
    }

    private ColorSchemes() {}

}
